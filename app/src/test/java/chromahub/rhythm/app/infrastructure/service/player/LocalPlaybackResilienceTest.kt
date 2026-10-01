/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class LocalPlaybackResilienceTest {

    private val missingFileException = IOException(
        FileNotFoundException("No item at content://media/external/audio/media/98765")
    )

    @Test
    fun testIsLocalScheme_identifiesLocalSchemesCorrectly() {
        assertTrue(MissingLocalMediaClassifier.isLocalScheme("content"))
        assertTrue(MissingLocalMediaClassifier.isLocalScheme("file"))
        assertTrue(MissingLocalMediaClassifier.isLocalScheme(""))
        assertTrue(MissingLocalMediaClassifier.isLocalScheme(null))

        assertFalse(MissingLocalMediaClassifier.isLocalScheme("http"))
        assertFalse(MissingLocalMediaClassifier.isLocalScheme("https"))
        assertFalse(MissingLocalMediaClassifier.isLocalScheme("rtsp"))
        assertFalse(MissingLocalMediaClassifier.isLocalScheme("ftp"))
    }

    @Test
    fun testIsMissingLocalItem_fileNotFoundErrorCodeOnLocalMedia() {
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                cause = null,
                uriScheme = "content"
            )
        )
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                cause = null,
                uriScheme = "file"
            )
        )
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                cause = null,
                uriScheme = null
            )
        )
    }

    @Test
    fun testIsMissingLocalItem_nestedFileNotFoundCauseDetected() {
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                cause = missingFileException,
                uriScheme = "content"
            )
        )

        // Nested 4 levels deep
        val deeplyNested = RuntimeException(
            IllegalStateException(
                IOException(
                    FileNotFoundException("Media file missing from storage")
                )
            )
        )
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                cause = deeplyNested,
                uriScheme = "content"
            )
        )
    }

    @Test
    fun testIsMissingLocalItem_remoteMediaNeverClassifiedAsMissingLocal() {
        assertFalse(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                cause = missingFileException,
                uriScheme = "https"
            )
        )
        assertFalse(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                cause = missingFileException,
                uriScheme = "http"
            )
        )
    }

    @Test
    fun testIsMissingLocalItem_preservesOtherPlaybackErrors() {
        val nonMissingCodes = listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
        )

        for (code in nonMissingCodes) {
            assertFalse(
                "Code $code should not be classified as missing local media",
                MissingLocalMediaClassifier.isMissingLocalItem(
                    code,
                    cause = IOException("Read error"),
                    uriScheme = "content"
                )
            )
        }
    }
}
