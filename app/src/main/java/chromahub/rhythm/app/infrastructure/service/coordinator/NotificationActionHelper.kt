/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.coordinator

import android.os.Bundle

/**
 * Pure parser & coordinator for MediaSession custom command bundles related to active
 * lyrics synchronization, extracted from [MediaPlaybackService].
 */
object NotificationActionHelper {

    data class LyricsSyncPayload(
        val texts: List<String>,
        val translations: List<String>,
        val romanizations: List<String>,
        val timestamps: LongArray,
        val plainLyricsLines: List<String>
    )

    fun parseLyricsDataBundle(args: Bundle): LyricsSyncPayload {
        val texts = args.getStringArrayList("lyric_texts") ?: emptyList()
        val translations = args.getStringArrayList("lyric_translations") ?: emptyList()
        val romanizations = args.getStringArrayList("lyric_romanizations") ?: emptyList()
        val timestamps = args.getLongArray("lyric_timestamps") ?: longArrayOf()
        val plainLyrics = args.getString("plain_lyrics")
        val plainLines = if (!plainLyrics.isNullOrBlank()) {
            plainLyrics.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        return LyricsSyncPayload(
            texts = texts,
            translations = translations,
            romanizations = romanizations,
            timestamps = timestamps,
            plainLyricsLines = plainLines
        )
    }
}
