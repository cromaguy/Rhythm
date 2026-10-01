package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException

/**
 * Decides whether a playback error means a *local* queue item no longer exists
 * (e.g. a MediaStore entry that was deleted or re-scanned under a new id, which
 * surfaces as `FileNotFoundException: No item at content://media/...`).
 *
 * Such items can never play again, so they are skipped and pruned from the queue
 * instead of stopping playback. Every other error type is left to the regular
 * error handling.
 */
object MissingLocalMediaClassifier {

    private const val MAX_CAUSE_DEPTH = 8

    /** `content://` and `file://` URIs, plus bare file paths (no scheme). */
    fun isLocalScheme(scheme: String?): Boolean =
        scheme.isNullOrEmpty() ||
            scheme.equals("content", ignoreCase = true) ||
            scheme.equals("file", ignoreCase = true)

    fun isMissingLocalItem(errorCode: Int, cause: Throwable?, uriScheme: String?): Boolean {
        if (!isLocalScheme(uriScheme)) return false
        return errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND || hasFileNotFoundCause(cause)
    }

    private fun hasFileNotFoundCause(cause: Throwable?): Boolean {
        var current = cause
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is FileNotFoundException) return true
            current = current.cause
            depth++
        }
        return false
    }
}
