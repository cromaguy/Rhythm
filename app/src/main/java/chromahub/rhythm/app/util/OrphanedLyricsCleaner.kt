/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import java.io.File

/**
 * Safely prunes orphaned cached lyric files (`.lrc`, `.ttml`, `.srt`, `.json`) from Rhythm's
 * internal app lyrics directory once library songs have been deleted from the device.
 */
object OrphanedLyricsCleaner {

    private val SAFE_CHARS_REGEX = Regex("[^a-zA-Z0-9._-]")
    private val LYRICS_EXTENSIONS = setOf("json", "lrc", "ttml", "srt")

    fun expectedCacheStems(artist: String, title: String): Set<String> {
        val primary = "${artist}_${title}".replace(SAFE_CHARS_REGEX, "_")
        val lowercase = primary.lowercase()
        return setOf(primary, lowercase)
    }

    /**
     * Deletes files inside [lyricsCacheDir] whose base stem does not match any active song.
     * No-ops when [activeSongs] is empty to guard against transient scan failures or revoked permissions.
     */
    fun pruneOrphanedAppLyricsCache(
        lyricsCacheDir: File,
        activeSongs: List<Pair<String, String>>
    ): Int {
        if (activeSongs.isEmpty() || !lyricsCacheDir.exists() || !lyricsCacheDir.isDirectory) {
            return 0
        }

        val validStems = HashSet<String>(activeSongs.size * 2)
        activeSongs.forEach { (artist, title) ->
            validStems.addAll(expectedCacheStems(artist, title))
        }

        var deletedCount = 0
        lyricsCacheDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val ext = file.extension.lowercase()
            if (ext !in LYRICS_EXTENSIONS) return@forEach
            val stem = file.nameWithoutExtension
            if (!validStems.contains(stem) && !validStems.contains(stem.lowercase())) {
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }
}
