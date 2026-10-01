package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingLocalMediaClassifierTest {

    private val staleMediaStoreItem = IOException(
        FileNotFoundException("No item at content://media/external/audio/media/1234")
    )

    @Test
    fun fileNotFoundCodeOnContentUriIsMissing() {
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, null, "content"
            )
        )
    }

    @Test
    fun fileNotFoundCauseIsMissingEvenWithGenericCode() {
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED, staleMediaStoreItem, "content"
            )
        )
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED, staleMediaStoreItem, "file"
            )
        )
        assertTrue(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED, staleMediaStoreItem, null
            )
        )
    }

    @Test
    fun remoteItemsAreNeverTreatedAsMissingLocalMedia() {
        assertFalse(
            MissingLocalMediaClassifier.isMissingLocalItem(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, staleMediaStoreItem, "https"
            )
        )
    }

    @Test
    fun otherErrorTypesAreNotMasked() {
        listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        ).forEach { code ->
            assertFalse(
                "code $code",
                MissingLocalMediaClassifier.isMissingLocalItem(code, IOException("read failed"), "content")
            )
        }
    }
}
