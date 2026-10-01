/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class CatalogCacheWriterTest {

    private val gson = Gson()
    private lateinit var dir: File
    private lateinit var file: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("catalog-cache").toFile()
        file = File(dir, "streaming_catalog_subsonic.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** A payload whose size depends on [n], so interleaved writes would corrupt the JSON. */
    private fun payload(n: Int): String = gson.toJson(
        StreamingCatalogCache(
            serviceId = "subsonic",
            likedSongIds = List(200 + n * 37) { "subsonic::song-$n-$it" },
            lastSyncTimestamp = n.toLong()
        )
    )

    private fun writeText(text: String, out: java.io.Writer) = out.write(text)

    @Test
    fun concurrentSaves_leaveOneCompleteReadableFile() = runBlocking {
        val writer = CatalogCacheWriter()
        val inside = AtomicInteger(0)
        val maxInside = AtomicInteger(0)

        (1..64).map { n ->
            async(Dispatchers.IO) {
                writer.write(file, { payload(n) }) { text, out ->
                    maxInside.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
                    out.write(text)
                    inside.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals("saves must not overlap", 1, maxInside.get())
        val cache = gson.fromJson(file.readText(), StreamingCatalogCache::class.java)
        val n = cache.lastSyncTimestamp.toInt()
        assertTrue(n in 1..64)
        assertEquals(payload(n), file.readText())
        assertFalse("temp file must not be left behind", File(dir, "${file.name}.tmp").exists())
    }

    @Test
    fun write_replacesExistingFileAndSkipsNullContent() = runBlocking {
        val writer = CatalogCacheWriter()

        assertTrue(writer.write(file, { payload(1) }, ::writeText))
        assertTrue(writer.write(file, { payload(2) }, ::writeText))
        assertEquals(payload(2), file.readText())

        assertFalse(writer.write(file, { null }, ::writeText))
        assertEquals(payload(2), file.readText())
    }

    @Test
    fun delete_removesFile() = runBlocking {
        val writer = CatalogCacheWriter()
        writer.write(file, { payload(1) }, ::writeText)

        assertTrue(writer.delete(file))
        assertFalse(file.exists())
    }

    @Test
    fun coalescer_mergesABurstIntoOneSaveOfTheLatestState() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val coalescer = SaveCoalescer(scope, delayMs = 200)
            val saves = AtomicInteger(0)
            val state = AtomicInteger(0)
            val saved = mutableListOf<Int>()

            repeat(10) {
                state.set(it)
                coalescer.request("subsonic") { saves.incrementAndGet(); synchronized(saved) { saved += state.get() } }
            }
            delay(600)
            assertEquals(1, saves.get())
            assertEquals(listOf(9), saved)

            // A later request is saved again.
            state.set(42)
            coalescer.request("subsonic") { saves.incrementAndGet(); synchronized(saved) { saved += state.get() } }
            delay(600)
            assertEquals(listOf(9, 42), saved)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun coalescer_keepsKeysSeparate() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val coalescer = SaveCoalescer(scope, delayMs = 100)
            val saves = AtomicInteger(0)

            coalescer.request("subsonic") { saves.incrementAndGet() }
            coalescer.request("jellyfin") { saves.incrementAndGet() }
            delay(400)

            assertEquals(2, saves.get())
        } finally {
            scope.cancel()
        }
    }
}
