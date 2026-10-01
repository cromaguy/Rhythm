/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException

/**
 * Classifies playback exceptions to detect missing local media items (e.g. deleted or rescanned MediaStore files).
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
