package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData

/**
 * Decides when a supplemental translation is actually worth showing.
 *
 * Provider matching is responsible for rejecting the wrong song. Once a provider returns a
 * distinct translation, it remains valid even when the original uses Latin script (for example,
 * an English original translated to Portuguese). Suppressing all such pairs would make the
 * advertised translation feature fail for most Western music.
 */
object LyricsTranslationPolicy {
    private const val MIN_TIMED_LINE_COVERAGE = 0.50

    /**
     * Returns the translation to display alongside [original], or null when it adds nothing:
     * blank or identical to the original.
     */
    fun selectLine(original: String, translation: String?): String? {
        val candidate = translation?.trim().orEmpty()
        if (candidate.isEmpty()) return null

        val base = original.trim()
        if (candidate.equals(base, ignoreCase = true)) return null

        return candidate
    }

    fun hasUsableCoverage(original: List<String>, supplemental: List<String>): Boolean {
        val vocalLines = original.indices.filter { index ->
            original[index].any(Character::isLetter)
        }
        if (vocalLines.isEmpty()) return false

        val translatedLines = vocalLines.count { index ->
            selectLine(original[index], supplemental.getOrNull(index)) != null
        }
        return translatedLines.toDouble() / vocalLines.size >= MIN_TIMED_LINE_COVERAGE
    }
}

fun LyricsData.hasUsableTimedTranslation(): Boolean {
    val parsed = syncedLyrics?.let(LyricsParser::parseLyrics).orEmpty()
    return LyricsTranslationPolicy.hasUsableCoverage(
        original = parsed.map { it.text },
        supplemental = parsed.map { it.translation.orEmpty() }
    )
}
