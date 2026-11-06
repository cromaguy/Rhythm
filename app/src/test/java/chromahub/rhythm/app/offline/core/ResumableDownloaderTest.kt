// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Offline cache, item 1: resumable download of the original file. */
class ResumableDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val requests = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val payload = ByteArray(300_000) { (it * 31 + 7).toByte() }

    /** How the fake server answers; tests swap it. */
    private var respond: (RecordedRequest) -> MockResponse = { rangeAware(it) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return respond(request)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    /** A well-behaved server: 206 with Content-Range when asked for a range, 200 otherwise. */
    private fun rangeAware(request: RecordedRequest, headers: Map<String, String> = emptyMap()): MockResponse {
        val range = request.headers["Range"]
        val b = MockResponse.Builder()
        headers.forEach { (k, v) -> b.addHeader(k, v) }
        return if (range != null) {
            val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
            if (from >= payload.size) {
                b.code(416).build()
            } else {
                b.code(206)
                    .addHeader("Content-Range", "bytes $from-${payload.size - 1}/${payload.size}")
                    .body(body(payload.copyOfRange(from, payload.size)))
                    .build()
            }
        } else {
            b.code(200).body(body(payload)).build()
        }
    }

    private val url get() = server.url("/rest/download.view?id=1").toString()
    private val part get() = File(tmp.root, "dl/SUBSONIC__1.part")
    private val target get() = File(tmp.root, "dl/SUBSONIC__1.flac")

    private fun run(
        progress: MutableList<Pair<Long, Long>> = mutableListOf(),
        shouldContinue: () -> Boolean = { true },
    ) = ResumableDownloader().download(
        url = url,
        part = part,
        finalFile = { target },
        onProgress = { d, t -> progress += d to t },
        shouldContinue = shouldContinue,
    )

    @Test
    fun aFreshDownloadWritesTheFileAndRenamesItWhenComplete() {
        val progress = mutableListOf<Pair<Long, Long>>()
        val result = run(progress)
        assertTrue(result is ResumableDownloader.Result.Done)
        assertArrayEquals(payload, target.readBytes())
        assertFalse("the .part file is gone", part.exists())
        assertNull("no Range header on a fresh download", requests.single().headers["Range"])
        assertEquals("reports the total from the first tick", payload.size.toLong(), progress.first().second)
        assertEquals(payload.size.toLong() to payload.size.toLong(), progress.last())
    }

    @Test
    fun aRestartedDownloadAsksForTheRestWithARangeAndAppends() {
        part.parentFile!!.mkdirs()
        part.writeBytes(payload.copyOfRange(0, 120_000))
        val progress = mutableListOf<Pair<Long, Long>>()
        val result = run(progress)
        assertTrue(result is ResumableDownloader.Result.Done)
        assertEquals("bytes=120000-", requests.single().headers["Range"])
        assertArrayEquals("the file is whole and byte-identical", payload, target.readBytes())
        assertEquals("progress starts where the .part file ended", 120_000L, progress.first().first)
        assertEquals(payload.size.toLong(), progress.first().second)
    }

    @Test
    fun aServerThatIgnoresRangeIsHandledByStartingTheFileOver() {
        respond = { MockResponse.Builder().code(200).body(body(payload)).build() }
        part.parentFile!!.mkdirs()
        part.writeBytes(ByteArray(50_000) { 9 })
        val result = run()
        assertTrue(result is ResumableDownloader.Result.Done)
        assertArrayEquals("no stale bytes in front", payload, target.readBytes())
    }

    @Test
    fun aRangeNotSatisfiableAnswerRestartsFromZero() {
        // The first ask (bytes=N-) gets 416 because the server's file is shorter; the retry is plain.
        part.parentFile!!.mkdirs()
        part.writeBytes(ByteArray(payload.size + 10) { 1 })
        val result = run()
        assertTrue(result is ResumableDownloader.Result.Done)
        assertEquals(2, requests.size)
        assertNull(requests[1].headers["Range"])
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun aResumeAtTheWrongOffsetIsRefusedNotCorrupted() {
        respond = {
            MockResponse.Builder().code(206)
                .addHeader("Content-Range", "bytes 0-${payload.size - 1}/${payload.size}")
                .body(body(payload)).build()
        }
        part.parentFile!!.mkdirs()
        part.writeBytes(payload.copyOfRange(0, 1_000))
        try {
            run()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("wrong offset"))
        }
        assertEquals("the partial bytes are untouched", 1_000L, part.length())
        assertFalse(target.exists())
    }

    @Test
    fun aConnectionCutMidFileKeepsThePartialFileAndTheNextTryFinishesIt() {
        respond = {
            // Promises the whole file, sends a third of it, then drops the connection.
            MockResponse.Builder().code(200)
                .body(body(payload.copyOfRange(0, 100_000)))
                .setHeader("Content-Length", payload.size.toString())
                .onResponseEnd(SocketEffect.CloseSocket())
                .build()
        }
        try {
            run()
            fail("a cut connection must not look like a finished file")
        } catch (_: IOException) {
        }
        assertFalse(target.exists())
        assertTrue("some bytes were kept", part.exists())
        assertTrue(part.length() < payload.size)

        respond = { rangeAware(it) }
        val kept = part.length()
        requests.clear()
        val result = run()
        assertTrue(result is ResumableDownloader.Result.Done)
        assertEquals("bytes=$kept-", requests.single().headers["Range"])
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun cancellingStopsBetweenChunksAndKeepsThePartialFile() {
        var chunks = 0
        val result = run(shouldContinue = { ++chunks <= 2 })
        assertTrue(result is ResumableDownloader.Result.Cancelled)
        assertTrue(part.exists())
        assertFalse(target.exists())
        // ...and it resumes.
        requests.clear()
        val resumed = run()
        assertTrue(resumed is ResumableDownloader.Result.Done)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun anHttpErrorIsAnIoExceptionAndLeavesNoFile() {
        respond = { MockResponse.Builder().code(404).build() }
        try {
            run()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
        assertFalse(target.exists())
    }

    @Test
    fun theFinalNameCanDependOnTheResponse() {
        respond = { rangeAware(it, mapOf("Content-Disposition" to "attachment; filename=\"song.opus\"")) }
        var seen: String? = null
        val out = File(tmp.root, "dl/SUBSONIC__1.opus")
        val result = ResumableDownloader().download(
            url = url,
            part = part,
            finalFile = { r ->
                seen = r.contentDisposition
                out
            },
        )
        assertTrue(result is ResumableDownloader.Result.Done)
        assertTrue(seen!!.contains("song.opus"))
        assertTrue(out.exists())
    }

    @Test
    fun progressIsThrottled() {
        var now = 0L
        val progress = mutableListOf<Pair<Long, Long>>()
        ResumableDownloader().download(
            url = url, part = part, finalFile = { target },
            onProgress = { d, t -> progress += d to t },
            clock = { now },
        )
        // A clock that never advances: the opening tick, the first chunk's tick and the closing one.
        assertTrue("got ${progress.size} ticks", progress.size <= 4)
    }

    @Test
    fun contentRangeIsParsed() {
        val d = ResumableDownloader()
        assertEquals(ResumableDownloader.ContentRange(1000, 4999, 5000), d.parseContentRange("bytes 1000-4999/5000"))
        assertEquals(ResumableDownloader.ContentRange(0, 9, null), d.parseContentRange("bytes 0-9/*"))
        assertNull(d.parseContentRange("garbage"))
        assertNull(d.parseContentRange(null))
    }

    @Suppress("unused")
    private val unusedTimeUnit = TimeUnit.SECONDS

    // Review 2026-10-07: a Subsonic API error (download disabled, bad login) is an HTTP 200 whose body
    // is JSON or XML. It must never be saved as the song.
    @Test
    fun aSubsonicErrorAnswerWithStatus200IsNotSavedAsAudio() {
        respond = {
            MockResponse.Builder().code(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .body(body("""{"subsonic-response":{"status":"failed","error":{"code":50,"message":"not authorized"}}}""".toByteArray()))
                .build()
        }
        try {
            run()
            fail("an error document is not a download")
        } catch (e: NotAudioException) {
            assertEquals("application/json; charset=utf-8", e.contentType)
        }
        assertFalse(target.exists())
        assertFalse(part.exists())
    }

    @Test
    fun anXmlOrHtmlAnswerIsNotAudioEither() {
        for (type in listOf("text/xml", "application/xml", "text/html", "text/plain")) {
            respond = { MockResponse.Builder().code(200).addHeader("Content-Type", type).body(body("<x/>".toByteArray())).build() }
            try {
                run()
                fail("$type is not audio")
            } catch (_: NotAudioException) {
            }
        }
    }

    @Test
    fun audioAndUnlabelledAnswersAreStillDownloaded() {
        for (type in listOf("audio/flac", "audio/x-wav", "application/octet-stream", "application/ogg", null)) {
            respond = {
                val b = MockResponse.Builder().code(200).body(body(payload))
                if (type != null) b.addHeader("Content-Type", type)
                b.build()
            }
            assertTrue("$type", run() is ResumableDownloader.Result.Done)
            target.delete()
        }
    }

    // Review 2026-10-07: when WorkManager stops a worker, its thread can be blocked in a read for a
    // while; a replacement worker may already be appending to the same .part. A stopped download must
    // not write the chunk it was reading when it was stopped.
    @Test
    fun aChunkReadAfterStopWasRequestedIsNotWritten() {
        var stop = false
        val stream = object : java.io.InputStream() {
            var reads = 0
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                if (reads == 2) stop = true // stopped while this read was blocked
                if (reads > 3) return -1
                java.util.Arrays.fill(b, off, off + 1000, reads.toByte())
                return 1000
            }
        }
        val fetcher = RangeFetcher { _, _, _ -> RangeResponse(200, -1L, null, null, null, stream) }
        val result = ResumableDownloader(fetcher).download(url, part, { target }, shouldContinue = { !stop })
        assertTrue(result is ResumableDownloader.Result.Cancelled)
        assertEquals("only the first chunk", 1000L, part.length())
    }
}
