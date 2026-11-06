// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A failed row always says why: the HTTP status or the exception class, never nothing. */
class DownloadErrorsTest {

    @Test
    fun httpStatusesAreNamed() {
        assertEquals("HTTP 404 (not found)", DownloadErrors.describe(HttpStatusException(404)))
        assertEquals("HTTP 503 (server unavailable)", DownloadErrors.describe(HttpStatusException(503)))
        assertEquals("HTTP 418", DownloadErrors.describe(HttpStatusException(418)))
    }

    @Test
    fun theServerSendingADocumentIsSaid() {
        assertEquals("The server sent application/json instead of audio", DownloadErrors.describe(NotAudioException("application/json")))
    }

    @Test
    fun networkFailuresReadAsWords() {
        assertTrue(DownloadErrors.describe(SocketTimeoutException("timeout")).contains("did not answer in time"))
        assertTrue(DownloadErrors.describe(UnknownHostException("music.lan")).contains("music.lan"))
        assertTrue(DownloadErrors.describe(UnknownHostException()).contains("unknown host"))
        assertEquals("Could not connect to the server", DownloadErrors.describe(ConnectException("refused")))
        assertTrue(DownloadErrors.describe(SSLHandshakeException("bad cert")).startsWith("Secure connection failed"))
        assertTrue(DownloadErrors.describe(EOFException()).contains("EOFException"))
        assertEquals("Connection lost (SocketException: Socket closed)", DownloadErrors.describe(SocketException("Socket closed")))
    }

    @Test
    fun anExceptionWithoutAMessageIsNamedByItsClass() {
        assertEquals("IOException", DownloadErrors.describe(IOException()))
        assertEquals("NullPointerException", DownloadErrors.describe(NullPointerException()))
        assertEquals("IllegalStateException: boom", DownloadErrors.describe(IllegalStateException("boom")))
    }

    @Test
    fun nothingRecordedIsStillAnAnswer() {
        assertEquals("The download failed (no error reported)", DownloadErrors.describe(null as Throwable?))
        assertEquals("The download failed", DownloadErrors.describe(message = null))
        assertEquals("The download failed", DownloadErrors.describe(message = "   "))
        assertEquals("custom", DownloadErrors.describe(message = null, fallback = "custom"))
        assertEquals("HTTP 500", DownloadErrors.describe(message = " HTTP 500 "))
    }

    @Test
    fun aLongMessageIsCut() {
        val text = DownloadErrors.describe(IOException("x".repeat(1_000)))
        assertEquals(160, text.length)
    }

    @Test
    fun wrappingKeepsTheCauseAndTheLineTheRepositoryRecords() {
        val original = HttpStatusException(403)
        val wrapped = DownloadErrors.wrap(original)
        assertEquals("HTTP 403 (forbidden)", wrapped.message)
        assertSame(original, wrapped.cause)
        assertSame("not wrapped twice", wrapped, DownloadErrors.wrap(wrapped))
        assertEquals("HTTP 403 (forbidden)", DownloadErrors.describe(wrapped))
        assertTrue(DownloadErrors.wrap(IOException()).message == "IOException")
    }
}
