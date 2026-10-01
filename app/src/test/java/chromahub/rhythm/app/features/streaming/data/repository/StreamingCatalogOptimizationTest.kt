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
import chromahub.rhythm.app.features.streaming.domain.repository.StreamingMusicRepository
import chromahub.rhythm.app.util.ArtistSeparator
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StreamingCatalogOptimizationTest {

    private lateinit var tempDir: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("streaming-optimization-test").toFile()
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. CatalogCacheCodec & URL References Tests
    // -------------------------------------------------------------------------

    private object MockUrlRefs : CatalogUrlRefs {
        private const val BASE_URL = "https://music.example.com/rest/"
        private const val AUTH = "u=admin&t=token123&s=salt456&v=1.16.1&c=Rhythm&f=json"

        fun cover(id: String) = "${BASE_URL}getCoverArt.view?$AUTH&id=$id&size=500"
        fun stream(id: String) = "${BASE_URL}stream.view?$AUTH&id=$id&maxBitRate=320"

        override fun toRef(url: String): String? {
            if (!url.startsWith(BASE_URL)) return null
            val endpoint = url.removePrefix(BASE_URL).substringBefore(".view")
            val id = url.substringAfter("&id=").substringBefore('&')
            return "$endpoint?id=$id"
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

    private fun createSong(index: Int, isFavorite: Boolean = false, sourceType: SourceType = SourceType.SUBSONIC) = StreamingSong(
        id = "${sourceType.name.lowercase()}::tr-$index",
        title = "Track $index",
        artist = "Artist ${index / 5}",
        album = "Album ${index / 10}",
        duration = 180_000L + index,
        artworkUri = if (sourceType == SourceType.SUBSONIC) MockUrlRefs.cover("al-${index / 10}") else "https://jellyfin.example.com/Items/al-${index / 10}/Images/Primary",
        sourceType = sourceType,
        streamingUrl = if (sourceType == SourceType.SUBSONIC) MockUrlRefs.stream("tr-$index") else "https://jellyfin.example.com/Audio/tr-$index/stream",
        previewUrl = null,
        externalId = "tr-$index",
        albumId = "${sourceType.name.lowercase()}::al-${index / 10}",
        albumArtist = "Artist ${index / 5}",
        isFavorite = isFavorite,
        trackNumber = (index % 10) + 1,
        year = 2024,
        genre = "Rock",
        bitrate = 320,
        codec = "flac"
    )

    private fun createSampleCatalog(serviceId: String = "subsonic", songCount: Int = 30): StreamingCatalogCache {
        val type = if (serviceId.equals("jellyfin", ignoreCase = true)) SourceType.JELLYFIN else SourceType.SUBSONIC
        val songs = (0 until songCount).map { createSong(it, isFavorite = it % 3 == 0, sourceType = type) }
        val albums = (0 until (songCount / 10).coerceAtLeast(1)).map {
            StreamingAlbum(
                id = "$serviceId::al-$it",
                title = "Album $it",
                artist = "Artist",
                artworkUri = if (type == SourceType.SUBSONIC) MockUrlRefs.cover("al-$it") else "https://jellyfin.example.com/Items/al-$it/Images/Primary",
                songCount = 10,
                year = 2024,
                sourceType = type
            )
        }
        val artists = listOf(
            StreamingArtist(id = "$serviceId::ar-1", name = "Artist 1", artworkUri = if (type == SourceType.SUBSONIC) MockUrlRefs.cover("ar-1") else null, songCount = 15, albumCount = 2, sourceType = type),
            StreamingArtist(id = "$serviceId::ar-2", name = "Artist 2", artworkUri = "https://external-art.example.com/image.jpg", songCount = 15, albumCount = 1, sourceType = type)
        )
        val playlists = listOf(
            StreamingPlaylist(id = "$serviceId::pl-1", name = "Favorites", description = "Best tracks", artworkUri = null, songCount = 5, isEditable = true, sourceType = type)
        )
        return StreamingCatalogCache(
            serviceId = serviceId,
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists,
            likedSongIds = songs.filter { it.isFavorite }.map { it.id },
            lastSyncTimestamp = 1700000000000L,
            libraryMarker = "user-1:1700000000000"
        )
    }

    @Test
    fun testCatalogCacheCodec_roundTripPreservesAllFields() {
        val codec = CatalogCacheCodec(MockUrlRefs)
        val original = createSampleCatalog("subsonic", 20)

        val writer = StringWriter()
        codec.write(writer, original)
        val encodedJson = writer.toString()

        val restored = codec.read(StringReader(encodedJson))
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun testCatalogCacheCodec_storesCompactUrlReferencesWithoutAuthTokens() {
        val codec = CatalogCacheCodec(MockUrlRefs)
        val original = createSampleCatalog("subsonic", 10)

        val writer = StringWriter()
        codec.write(writer, original)
        val json = writer.toString()

        // Auth tokens must not be persisted in the serialized cache
        assertFalse(json.contains("token123"))
        assertFalse(json.contains("salt456"))
        assertTrue(json.contains("\"ref:getCoverArt?id=al-0\""))
        assertTrue(json.contains("\"ref:stream?id=tr-0\""))
        // External URLs remain untouched
        assertTrue(json.contains("https://external-art.example.com/image.jpg"))
    }

    @Test
    fun testCatalogCacheCodec_backwardsCompatibilityWithLegacyVerboseJson() {
        val legacy = createSampleCatalog("subsonic", 20)
        val legacyJson = Gson().toJson(legacy)

        val codec = CatalogCacheCodec(MockUrlRefs)
        val restored = codec.read(StringReader(legacyJson))
        assertNotNull(restored)
        assertEquals(legacy, restored)
    }

    @Test
    fun testCatalogCacheCodec_jellyfinCatalogPreservesFullUrls() {
        val jellyfinCatalog = createSampleCatalog("jellyfin", 15)
        val codec = CatalogCacheCodec(CatalogUrlRefs.None)

        val writer = StringWriter()
        codec.write(writer, jellyfinCatalog)
        val json = writer.toString()

        assertTrue(json.contains("https://jellyfin.example.com/Items/al-0/Images/Primary"))
        assertTrue(json.contains("https://jellyfin.example.com/Audio/tr-0/stream"))

        val restored = codec.read(StringReader(json))
        assertNotNull(restored)
        assertEquals(jellyfinCatalog, restored)
    }

    @Test
    fun testCatalogCacheCodec_missingOptionalFieldsUseSafeDefaults() {
        val minimalJson = """
            {
                "serviceId": "subsonic",
                "songs": [
                    { "i": "subsonic::s1", "t": "Track 1", "a": "Artist 1", "l": "Album 1", "d": 200000, "s": "SUBSONIC" }
                ]
            }
        """.trimIndent()

        val restored = CatalogCacheCodec().read(StringReader(minimalJson))
        assertNotNull(restored)
        val song = restored!!.songs.first()
        assertEquals("s1", song.externalId)
        assertTrue(song.isPlayable)
        assertFalse(song.isFavorite)
        assertNull(song.streamingUrl)
        assertNull(song.artworkUri)
    }

    // -------------------------------------------------------------------------
    // 2. CatalogCacheWriter & SaveCoalescer Tests
    // -------------------------------------------------------------------------

    @Test
    fun testCatalogCacheWriter_atomicWriteAndConcurrentSerialization() = runBlocking {
        val writer = CatalogCacheWriter()
        val cacheFile = File(tempDir, "streaming_catalog_subsonic.json")
        val maxConcurrentWriters = AtomicInteger(0)
        val currentWriters = AtomicInteger(0)

        val tasks = (1..32).map { n ->
            async(Dispatchers.IO) {
                val payload = createSampleCatalog("subsonic", n * 2)
                writer.write(cacheFile, snapshot = { payload }) { data, out ->
                    val running = currentWriters.incrementAndGet()
                    maxConcurrentWriters.accumulateAndGet(running, ::maxOf)
                    Thread.sleep(10) // Small delay to test overlap serialization
                    CatalogCacheCodec().write(out, data)
                    currentWriters.decrementAndGet()
                }
            }
        }
        tasks.awaitAll()

        assertEquals("Writes must be strictly serialized", 1, maxConcurrentWriters.get())
        assertTrue(cacheFile.exists())
        val restored = CatalogCacheCodec().readFile(cacheFile)
        assertNotNull(restored)
        assertTrue(restored!!.songs.isNotEmpty())
        assertFalse("No temporary files should be left behind", File(tempDir, "${cacheFile.name}.tmp").exists())
    }

    @Test
    fun testCatalogCacheWriter_deleteRemovesFileCleanly() = runBlocking {
        val writer = CatalogCacheWriter()
        val cacheFile = File(tempDir, "temp_cache.json")
        writer.write(cacheFile, snapshot = { "test-payload" }) { text, out -> out.write(text) }
        assertTrue(cacheFile.exists())

        assertTrue(writer.delete(cacheFile))
        assertFalse(cacheFile.exists())
    }

    @Test
    fun testSaveCoalescer_mergesBurstsIntoSingleExecutionOfLatestState() = runBlocking {
        val coalescer = SaveCoalescer(scope, delayMs = 150)
        val executions = AtomicInteger(0)
        val latestState = AtomicInteger(0)
        val recordedValues = mutableListOf<Int>()

        repeat(8) { iteration ->
            latestState.set(iteration)
            coalescer.request("subsonic") {
                executions.incrementAndGet()
                synchronized(recordedValues) { recordedValues += latestState.get() }
            }
        }

        delay(400)
        assertEquals("Coalesced requests should trigger only once", 1, executions.get())
        assertEquals(listOf(7), recordedValues)

        // Subsequent requests outside the coalescing window run separately
        coalescer.request("subsonic") {
            executions.incrementAndGet()
            synchronized(recordedValues) { recordedValues += 99 }
        }
        delay(400)
        assertEquals(2, executions.get())
        assertEquals(listOf(7, 99), recordedValues)
    }

    @Test
    fun testSaveCoalescer_isolatesDifferentServiceKeys() = runBlocking {
        val coalescer = SaveCoalescer(scope, delayMs = 100)
        val subsonicExecutions = AtomicInteger(0)
        val jellyfinExecutions = AtomicInteger(0)

        coalescer.request("subsonic") { subsonicExecutions.incrementAndGet() }
        coalescer.request("jellyfin") { jellyfinExecutions.incrementAndGet() }

        delay(300)
        assertEquals(1, subsonicExecutions.get())
        assertEquals(1, jellyfinExecutions.get())
    }

    // -------------------------------------------------------------------------
    // 3. BackgroundLoad Tests
    // -------------------------------------------------------------------------

    @Test
    fun testBackgroundLoad_executesOffCallingThreadWithoutBlocking() {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        var backgroundThread: Thread? = null
        var completed = false

        val backgroundLoad = BackgroundLoad(scope) {
            backgroundThread = Thread.currentThread()
            startedLatch.countDown()
            releaseLatch.await()
            completed = true
        }

        assertTrue("Background load must start promptly", startedLatch.await(5, TimeUnit.SECONDS))
        assertFalse(completed)
        assertNotSame(Thread.currentThread(), backgroundThread)

        releaseLatch.countDown()
        runBlocking { backgroundLoad.await() }
        assertTrue(completed)
    }

    @Test
    fun testBackgroundLoad_awaitReturnsSafelyEvenIfLoadFails() {
        val backgroundLoad = BackgroundLoad(scope) {
            throw IllegalStateException("Cache parse failure simulation")
        }

        // Must not crash or hang caller
        runBlocking { backgroundLoad.await() }
    }

    // -------------------------------------------------------------------------
    // 4. CatalogSyncGuards Tests
    // -------------------------------------------------------------------------

    @Test
    fun testSingleFlight_concurrentCallersJoinSingleExecution() = runBlocking {
        val singleFlight = SingleFlight<String>()
        val executionCounter = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        val callers = (1..30).map {
            async {
                singleFlight.run {
                    executionCounter.incrementAndGet()
                    gate.await()
                    "sync-success"
                }
            }
        }

        repeat(5) { yield() }
        gate.complete(Unit)

        val results = callers.awaitAll()
        assertEquals(List(30) { "sync-success" }, results)
        assertEquals("SingleFlight must execute exactly once for concurrent callers", 1, executionCounter.get())
    }

    @Test
    fun testSingleFlight_exceptionPropagatesToJoinersAndAllowsFreshRetry() = runBlocking {
        val singleFlight = SingleFlight<Int>()
        val started = CompletableDeferred<Unit>()
        val failGate = CompletableDeferred<Unit>()

        val owner = async {
            runCatching {
                singleFlight.run {
                    started.complete(Unit)
                    failGate.await()
                    throw RuntimeException("Network timeout")
                }
            }
        }

        started.await()
        val joiner = async { runCatching { singleFlight.run { 10 } } }
        repeat(5) { yield() }
        failGate.complete(Unit)

        assertTrue(owner.await().exceptionOrNull() is RuntimeException)
        assertTrue(joiner.await().exceptionOrNull() is RuntimeException)

        // Subsequent call must run fresh
        val freshResult = singleFlight.run { 42 }
        assertEquals(42, freshResult)
    }

    @Test
    fun testSingleFlight_cancellationPropagatesCleanly() = runBlocking {
        val singleFlight = SingleFlight<Int>()
        val started = CompletableDeferred<Unit>()

        val owner = async {
            singleFlight.run {
                started.complete(Unit)
                CompletableDeferred<Int>().await()
            }
        }

        started.await()
        val joiner = async { runCatching { singleFlight.run { 99 } } }
        repeat(5) { yield() }
        owner.cancel()

        assertTrue(joiner.await().exceptionOrNull() is CancellationException)
        assertEquals(123, singleFlight.run { 123 })
    }

    @Test
    fun testOnceGate_permitsOnlyFirstEntry() {
        val gate = OnceGate()
        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        assertFalse(gate.tryEnter())
    }

    @Test
    fun testCatalogSaveFilter_ignoresTimestampChangesAndDetectsContentModifications() {
        val filter = CatalogSaveFilter()
        val baseCache = createSampleCatalog("subsonic", 10)

        assertTrue("First save must report changed", filter.hasChanged(baseCache))
        filter.remember(baseCache)

        // Same content with newer timestamp must be filtered out
        val updatedTimestampCache = baseCache.copy(lastSyncTimestamp = baseCache.lastSyncTimestamp + 5000L)
        assertFalse(filter.hasChanged(updatedTimestampCache))

        // Genuine content changes must be detected
        val addedSongCache = baseCache.copy(songs = baseCache.songs + createSong(99))
        assertTrue(filter.hasChanged(addedSongCache))

        val changedMarkerCache = baseCache.copy(libraryMarker = "user-1:999999999")
        assertTrue(filter.hasChanged(changedMarkerCache))

        filter.remember(null)
        assertTrue("Clearing filter allows re-saving", filter.hasChanged(baseCache))
    }

    // -------------------------------------------------------------------------
    // 5. ArtistSongIndex Tests
    // -------------------------------------------------------------------------

    @Test
    fun testArtistSongIndex_fastLookupMatchesLinearScan() {
        val splitDelimiters: (String) -> List<String> = { raw ->
            ArtistSeparator.splitArtistNames(raw, delimiters = "&;/,", enabled = true)
        }

        val testSongs = listOf(
            createSong(1).copy(id = "1", artist = "Queen"),
            createSong(2).copy(id = "2", artist = "queen"),
            createSong(3).copy(id = "3", artist = "Queen & David Bowie"),
            createSong(4).copy(id = "4", artist = "David Bowie"),
            createSong(5).copy(id = "5", artist = "Freddie Mercury"),
            createSong(6).copy(id = "6", artist = "Bowie"),
            createSong(7).copy(id = "7", artist = "")
        )

        val index = ArtistSongIndex(testSongs, splitDelimiters)

        assertEquals(listOf("1", "2", "3"), index.songsFor("Queen").map { it.id })
        assertEquals(listOf("1", "2", "3"), index.songsFor("QUEEN").map { it.id })
        assertEquals(listOf("3", "4"), index.songsFor("David Bowie").map { it.id })
        assertEquals(listOf("6"), index.songsFor("Bowie").map { it.id })
        assertTrue(index.songsFor("Unknown").isEmpty())
        assertTrue(index.songsFor("").isEmpty())
    }

    // -------------------------------------------------------------------------
    // 6. DeezerArtistImageCache Tests
    // -------------------------------------------------------------------------

    @Test
    fun testDeezerArtistImageCache_hitsMissesAndTtlExpiry() {
        var currentTime = 1_000_000L
        val cache = DeezerArtistImageCache(
            file = null,
            clock = { currentTime },
            hitTtlMs = 1_000L,
            missTtlMs = 200L,
            failureTtlMs = 50L
        )

        assertNull(cache.get("daft punk"))
        cache.put("daft punk", "https://img.deezer.com/daftpunk.jpg")
        assertEquals("https://img.deezer.com/daftpunk.jpg", cache.get("daft punk")?.imageUrl)

        // Exceed hit TTL
        currentTime += 1_001L
        assertNull(cache.get("daft punk"))

        // Miss TTL
        cache.put("unknown-artist", null)
        assertNotNull(cache.get("unknown-artist"))
        assertNull(cache.get("unknown-artist")?.imageUrl)
        currentTime += 201L
        assertNull(cache.get("unknown-artist"))

        // Failure backoff
        cache.markFailed("flaky-artist")
        assertNotNull(cache.get("flaky-artist"))
        currentTime += 51L
        assertNull(cache.get("flaky-artist"))
    }

    @Test
    fun testDeezerArtistImageCache_persistenceAndRecovery() {
        val cacheFile = File(tempDir, "deezer_cache.json")
        val clock = { 100_000L }

        val firstInstance = DeezerArtistImageCache(cacheFile, clock)
        firstInstance.put("pink floyd", "https://img.deezer.com/pinkfloyd.jpg")
        firstInstance.put("unknown", null)
        firstInstance.flush()

        val secondInstance = DeezerArtistImageCache(cacheFile, clock)
        assertEquals("https://img.deezer.com/pinkfloyd.jpg", secondInstance.get("pink floyd")?.imageUrl)
        assertNotNull(secondInstance.get("unknown"))
        assertNull(secondInstance.get("non-existent"))
    }

    // -------------------------------------------------------------------------
    // 7. Large Catalog Scaling Tests (50,000 Songs)
    // -------------------------------------------------------------------------

    @Test
    fun testLargeCatalogScaling_roundTrips50kSongsWithinSizeBounds() {
        val targetSize = StreamingMusicRepository.MAX_LIBRARY_SONGS
        val songs = (0 until targetSize).map { index ->
            val id = "s-%06d".format(index)
            val albumId = "a-%04d".format(index / 10)
            StreamingSong(
                id = "subsonic::$id",
                title = "Title $index",
                artist = "Artist ${index / 50}",
                album = "Album ${index / 10}",
                duration = 200_000L,
                artworkUri = "https://music.example.com/rest/getCoverArt.view?id=$albumId",
                sourceType = SourceType.SUBSONIC,
                streamingUrl = "https://music.example.com/rest/stream.view?id=$id",
                previewUrl = null,
                externalId = id,
                albumId = "subsonic::$albumId",
                albumArtist = "Artist ${index / 50}",
                isFavorite = index % 25 == 0,
                trackNumber = index % 10,
                year = 2024,
                genre = "Electronic",
                bitrate = 320,
                codec = "mp3"
            )
        }

        val cache = StreamingCatalogCache(
            serviceId = "subsonic",
            songs = songs,
            likedSongIds = songs.filter { it.isFavorite }.map { it.id },
            lastSyncTimestamp = System.currentTimeMillis()
        )

        val file = File(tempDir, "streaming_catalog_subsonic_50k.json")
        CatalogCacheCodec().writeFile(file, cache)
        assertTrue(file.exists())

        // Ensure compact size: average under 1 KB per song
        assertTrue(file.length() < targetSize * 1024L)

        val restored = CatalogCacheCodec().readFile(file)
        assertNotNull(restored)
        assertEquals(targetSize, restored!!.songs.size)
        assertEquals(cache.songs.first().id, restored.songs.first().id)
        assertEquals(cache.songs.last().id, restored.songs.last().id)
    }
}
