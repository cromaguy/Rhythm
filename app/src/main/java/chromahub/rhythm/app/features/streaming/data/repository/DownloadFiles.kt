/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import java.io.File

/**
 * Where a downloaded track lives on disk.
 *
 * Kept outside [StreamingMusicRepositoryImpl] so it needs nothing from that class's
 * initialisation: the repository reads the download index from its `init` block, and an
 * instance field it depends on there is still null (the field initialisers below the `init`
 * block have not run yet). That is how FLAC downloads vanished on every restart.
 */
internal object DownloadFiles {

    /** Extensions a download may have been saved with; the server decides the real one. */
    val AUDIO_EXTENSIONS: List<String> =
        listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".wma", ".webm")

    /** The song id as a file name: ids such as `SUBSONIC::abc` cannot be used verbatim. */
    fun safeName(songId: String): String =
        songId.replace(":", "_").replace("/", "_").replace("\\", "_")

    /**
     * The file for [songId] in [directory]: the one with [extension] if it exists, else the
     * first existing file with any of [AUDIO_EXTENSIONS], else the (missing) file with
     * [extension] so callers can create it.
     */
    fun resolve(directory: File, songId: String, extension: String = ".mp3"): File {
        val safeName = safeName(songId)
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        val exactFile = File(directory, "$safeName$ext")
        if (exactFile.exists() && exactFile.length() > 0) return exactFile

        for (candidateExt in AUDIO_EXTENSIONS) {
            val candidate = File(directory, "$safeName$candidateExt")
            if (candidate.exists() && candidate.length() > 0) {
                return candidate
            }
        }
        return exactFile
    }
}
