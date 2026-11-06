// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * An [IOException] whose message is already the line to show ([DownloadErrors.describe] made it), so callers that only
 * keep `e.message` (the repository's `noteError`) keep something worth showing. [cause] is the original failure.
 */
class DescribedIOException(message: String, cause: Throwable?) : IOException(message, cause)

/**
 * Turns whatever a download died of into the one line the ledger keeps and the Downloads screen shows. A failure
 * with no message (an `IOException()`, a `NullPointerException`) used to be recorded as nothing at all; this always
 * names the HTTP status or the exception class, so a row that says "failed" says why.
 */
object DownloadErrors {

    private const val MAX_LENGTH = 160

    /** [error] as a short, always non-blank line. Null means "no exception was recorded". */
    fun describe(error: Throwable?): String {
        if (error == null) return "The download failed (no error reported)"
        val text = when (error) {
            is DescribedIOException -> error.message.orEmpty().ifBlank { named(error) }
            is HttpStatusException -> http(error.code)
            is NotAudioException -> "The server sent ${error.contentType} instead of audio"
            is SocketTimeoutException -> "The server did not answer in time (timeout)"
            is UnknownHostException -> "The server's address could not be found (${error.message.orEmpty().ifBlank { "unknown host" }})"
            is ConnectException, is NoRouteToHostException -> "Could not connect to the server"
            is SSLException -> "Secure connection failed (${named(error)})"
            is EOFException -> "The connection ended early (${named(error)})"
            is SocketException -> "Connection lost (${named(error)})"
            else -> named(error)
        }
        return text.take(MAX_LENGTH)
    }

    /** [error] as an [IOException] carrying [describe]'s line; [error] itself if it already is one. */
    fun wrap(error: IOException): IOException = if (error is DescribedIOException) error else DescribedIOException(describe(error), error)

    /** Same as [describe] for a message that is all a caller kept (the repository's `noteError`): [message], or the fallback. */
    fun describe(message: String?, fallback: String = "The download failed"): String =
        message?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LENGTH) ?: fallback

    /** An HTTP status as words: "HTTP 404 (not found)". */
    fun http(code: Int): String {
        val meaning = when (code) {
            400 -> "bad request"
            401 -> "login refused"
            403 -> "forbidden"
            404 -> "not found"
            408 -> "request timeout"
            410 -> "gone"
            416 -> "range not satisfiable"
            429 -> "too many requests"
            500 -> "server error"
            502 -> "bad gateway"
            503 -> "server unavailable"
            504 -> "gateway timeout"
            else -> null
        }
        return if (meaning != null) "HTTP $code ($meaning)" else "HTTP $code"
    }

    /** "SomeException: message", or just the class name when it has no message. */
    private fun named(error: Throwable): String {
        val cls = error.javaClass.simpleName.ifEmpty { error.javaClass.name }
        val message = error.message?.trim().orEmpty()
        return if (message.isEmpty()) cls else "$cls: $message"
    }
}
