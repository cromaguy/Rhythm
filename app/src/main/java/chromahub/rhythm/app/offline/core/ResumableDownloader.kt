// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP answer to a (possibly ranged) GET. Closing it releases the connection. */
class RangeResponse(
    val code: Int,
    /** Length of this response's body, or -1 when the server did not say. */
    val contentLength: Long,
    /** `Content-Range` header, e.g. `bytes 1000-4999/5000`, or null. */
    val contentRange: String?,
    val contentDisposition: String?,
    val contentType: String?,
    val body: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            onClose()
        }
    }
}

/** The server answered with a status that is not a download ([code]); a different URL may still work. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/**
 * The server answered 200 with a document, not audio. Subsonic reports an API error (download not
 * allowed, bad login) as HTTP 200 with a JSON or XML body, which must never be saved as the song.
 */
class NotAudioException(val contentType: String) : IOException("The server sent $contentType, not audio")

/** Fetches [url] from byte [from] on. `:app` supplies an OkHttp one (user-trusted certificates). */
fun interface RangeFetcher {
    @Throws(IOException::class)
    fun fetch(url: String, from: Long): RangeResponse =
        fetch(url, from, if (from > 0) mapOf("Range" to "bytes=$from-") else emptyMap())

    /**
     * Fetches [url] from byte [from] on, sending [headers] as they are, so every request of a transfer carries the
     * same Range and nothing of its own.
     */
    @Throws(IOException::class)
    fun fetch(url: String, from: Long, headers: Map<String, String>): RangeResponse

    companion object {
        /** `HttpURLConnection`: what the tests use. */
        val URL_CONNECTION = RangeFetcher { url, from, headers ->
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            // Identity: a gzip'd body would make byte offsets and lengths meaningless.
            connection.setRequestProperty("Accept-Encoding", "identity")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else (connection.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0)))
            RangeResponse(
                code = code,
                contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L,
                contentRange = connection.getHeaderField("Content-Range"),
                contentDisposition = connection.getHeaderField("Content-Disposition"),
                contentType = connection.contentType,
                body = stream,
                onClose = { connection.disconnect() },
            )
        }
    }
}

/**
 * Downloads a file so that an interruption costs nothing: bytes go to `<name>.part`, a restart
 * asks the server for the rest with `Range: bytes=<have>-`, and only a complete file is renamed to
 * its final name. A server that ignores Range (answers 200) is handled by starting that file over.
 *
 * Blocking; call it off the main thread. Cancellation is cooperative through [shouldContinue],
 * checked between chunks; a cancelled download leaves its `.part` file for the next try.
 */
class ResumableDownloader(private val fetcher: RangeFetcher = RangeFetcher.URL_CONNECTION) {

    sealed interface Result {
        data class Done(val file: File, val bytes: Long) : Result
        /** [shouldContinue] said stop; the `.part` file is kept. */
        data object Cancelled : Result
    }

    /**
     * @param part the partial file; its length is where a resume starts.
     * @param finalFile the file name once complete, chosen from the response (extension, type).
     * @param onProgress (bytesDone, bytesTotal or 0 when unknown), throttled to [PROGRESS_INTERVAL_MS].
     * @param requestHeaders sent with every request of the transfer ([Range] is added to them).
     */
    @Throws(IOException::class)
    fun download(
        url: String,
        part: File,
        finalFile: (RangeResponse) -> File,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
        shouldContinue: () -> Boolean = { true },
        clock: () -> Long = System::currentTimeMillis,
        requestHeaders: Map<String, String> = emptyMap(),
    ): Result {
        part.parentFile?.mkdirs()
        var have = if (part.exists()) part.length() else 0L
        fun rangeFor(from: Long) = if (from > 0) requestHeaders + ("Range" to "bytes=$from-") else requestHeaders
        var response = fetcher.fetch(url, have, rangeFor(have))
        try {
            if (response.code == 416 && have > 0) {
                // The server has less than we do (the file changed, or it was already whole): start over.
                response.close()
                part.delete()
                have = 0L
                response = fetcher.fetch(url, 0L, rangeFor(0L))
            }
            if (response.code != 200 && response.code != 206) throw HttpStatusException(response.code)
            response.contentType?.let { if (isDocument(it)) throw NotAudioException(it) }

            val append: Boolean
            val total: Long
            if (response.code == 206 && have > 0) {
                val range = parseContentRange(response.contentRange)
                if (range == null || range.start != have) {
                    throw IOException("Server resumed at the wrong offset (${response.contentRange})")
                }
                append = true
                total = range.total ?: if (response.contentLength >= 0) have + response.contentLength else 0L
            } else {
                // 200: the whole body, whatever was asked.
                append = false
                have = 0L
                total = if (response.contentLength >= 0) response.contentLength else 0L
            }

            val target = finalFile(response)
            var done = have
            var lastReport = clock() - PROGRESS_INTERVAL_MS
            java.io.FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                onProgress(done, total)
                if (!shouldContinue()) return Result.Cancelled
                while (true) {
                    val n = response.body.read(buffer)
                    // Checked after the (possibly long) read: a stopped download writes nothing more.
                    if (!shouldContinue()) return Result.Cancelled
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    val now = clock()
                    if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                        lastReport = now
                        onProgress(done, total)
                    }
                }
            }
            if (total > 0 && done != total) {
                throw IOException("Connection ended after $done of $total bytes")
            }
            onProgress(done, total.takeIf { it > 0 } ?: done)
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("Could not move the finished download into place")
            return Result.Done(target, done)
        } finally {
            response.close()
        }
    }

    private fun isDocument(contentType: String): Boolean {
        val type = contentType.lowercase()
        return type.startsWith("text/") || "json" in type || "xml" in type
    }

    internal data class ContentRange(val start: Long, val end: Long, val total: Long?)

    internal fun parseContentRange(header: String?): ContentRange? {
        val m = RANGE.matchEntire(header?.trim().orEmpty()) ?: return null
        return ContentRange(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLongOrNull())
    }

    companion object {
        const val PROGRESS_INTERVAL_MS = 250L
        private const val BUFFER_BYTES = 64 * 1024
        private val RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
    }
}
