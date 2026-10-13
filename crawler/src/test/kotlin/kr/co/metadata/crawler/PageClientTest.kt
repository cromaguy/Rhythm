// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PageClientTest {
    private suspend fun withServer(block: suspend (com.sun.net.httpserver.HttpServer, String) -> Unit) {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.executor = executor
        server.start()
        try { block(server, "http://127.0.0.1:${server.address.port}") }
        finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun `rejects both declared and chunked oversized bodies`() = runBlocking {
        withServer { server, base ->
            for ((path, text, length) in listOf(Triple("declared", "x".repeat(65), 65L),
                Triple("chunked", "x".repeat(65), 0L), Triple("valid", "small", 0L))) {
                server.createContext("/$path") { exchange ->
                    exchange.sendResponseHeaders(200, length)
                    exchange.responseBody.use { it.write(text.toByteArray()) }
                }
            }
            val client = PageClient(Duration.ZERO, 64)
            assertNull(client.get("$base/declared"))
            assertNull(client.get("$base/chunked"))
            assertEquals("small", client.get("$base/valid"))
        }
    }

    @Test fun `cancelling a body read cancels the OkHttp call`() = runBlocking {
        withServer { server, base ->
            val started = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            server.createContext("/slow") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.flush()
                started.countDown()
                release.await(3, TimeUnit.SECONDS)
                exchange.close()
            }
            val http = OkHttpClient()
            val client = PageClient(Duration.ZERO, client = http)
            val job = launch { client.get("$base/slow") }
            try {
                withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
                job.cancelAndJoin()
                withTimeout(3_000) { while (http.dispatcher.runningCallsCount() != 0) delay(10) }
                assertTrue(job.isCancelled)
            } finally { release.countDown() }
        }
    }

    @Test fun `repository propagates cancellation without trying the next provider`() = runBlocking {
        var secondCalled = false
        val first = object : MusicProvider {
            override val provider = Provider.BUGS_MOBILE
            override suspend fun track(id: String): TrackMetadata? = throw CancellationException("cancel")
            override suspend fun artist(id: String): ArtistMetadata? = null
            override suspend fun album(id: String): AlbumMetadata? = null
        }
        val second = object : MusicProvider {
            override val provider = Provider.MELON
            override suspend fun track(id: String): TrackMetadata? { secondCalled = true; return null }
            override suspend fun artist(id: String): ArtistMetadata? = null
            override suspend fun album(id: String): AlbumMetadata? = null
        }
        assertFailsWith<CancellationException> {
            MusicMetadataRepository(listOf(first, second)).track(MusicLookup(mapOf(Provider.BUGS_MOBILE to "1", Provider.MELON to "2")))
        }
        assertFalse(secondCalled)
    }
}
