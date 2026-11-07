/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.domain.model

import java.util.Locale

/**
 * How much of a playlist or album is on the device, and whether what is there is lossless.
 * Pure data so the badges can be unit tested without Compose.
 */
data class OfflineSummary(
    val total: Int,
    val downloaded: Int,
    val losslessDownloaded: Int
) {
    val isComplete: Boolean get() = total > 0 && downloaded >= total
    val isPartial: Boolean get() = downloaded in 1 until total
    val isAllLossless: Boolean get() = downloaded > 0 && losslessDownloaded >= downloaded

    companion object {
        val NONE = OfflineSummary(total = 0, downloaded = 0, losslessDownloaded = 0)

        /** Duplicates collapse on id, so a track that appears twice in a playlist counts once. */
        fun <T> of(
            items: List<T>,
            downloadedIds: Set<String>,
            id: (T) -> String,
            codec: (T) -> String?
        ): OfflineSummary {
            val distinct = items.distinctBy(id)
            val downloaded = distinct.filter { id(it) in downloadedIds }
            return OfflineSummary(
                total = distinct.size,
                downloaded = downloaded.size,
                losslessDownloaded = downloaded.count { AudioQuality.isLossless(codec(it)) }
            )
        }
    }
}

/** Format and bitrate of a track as a short chip label, and the lossless test behind the badges. */
object AudioQuality {
    private val LOSSLESS_CODECS = setOf(
        "flac", "alac", "wav", "wave", "aiff", "aif", "ape", "wv", "wavpack", "tta", "dsf", "dff", "dsd", "pcm", "shn"
    )

    fun isLossless(codec: String?): Boolean = normalise(codec) in LOSSLESS_CODECS

    /**
     * "FLAC 96k", "FLAC", "MP3 320", "320 kbps", or null when the track carries no format information.
     * [bitrateBps] is in bits per second (the model stores it that way), [sampleRateHz] in hertz.
     */
    fun label(codec: String?, bitrateBps: Int?, sampleRateHz: Int?): String? {
        val format = normalise(codec)?.uppercase(Locale.US)
        if (format != null && isLossless(format)) {
            val rate = sampleRateHz?.takeIf { it > 0 }?.let { formatKhz(it) }
            return if (rate != null) "$format $rate" else format
        }
        val kbps = bitrateBps?.takeIf { it > 0 }?.let { it / 1000 }
        return when {
            format != null && kbps != null -> "$format $kbps"
            format != null -> format
            kbps != null -> "$kbps kbps"
            else -> null
        }
    }

    private fun normalise(codec: String?): String? =
        codec?.trim()?.lowercase(Locale.US)?.substringAfter('/')?.takeIf { it.isNotBlank() }

    private fun formatKhz(hz: Int): String {
        val khz = hz / 1000.0
        return if (khz == Math.floor(khz)) "${khz.toInt()}k" else String.format(Locale.US, "%.1fk", khz)
    }
}
