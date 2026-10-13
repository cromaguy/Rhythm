// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

/** Public catalog durations: mm:ss, hh:mm:ss, ISO 8601, or numeric seconds. */
object TrackDuration {
    fun parse(value: String?): Long? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val millis = when {
            text.startsWith("PT", true) -> runCatching { java.time.Duration.parse(text.uppercase()).toMillis() }.getOrNull()
            ':' in text -> {
                val parts = text.split(':')
                if (parts.size !in 2..3) return null
                val numbers = parts.map { it.toLongOrNull() ?: return null }
                if (numbers.any { it < 0 } || numbers.drop(1).any { it >= 60 }) return null
                numbers.fold(0L) { total, n -> total * 60 + n } * 1000
            }
            else -> text.toDoubleOrNull()?.takeIf { it.isFinite() }?.times(1000)?.toLong()
        }
        return millis?.takeIf { it in 1_000..14_400_000 }
    }
}
