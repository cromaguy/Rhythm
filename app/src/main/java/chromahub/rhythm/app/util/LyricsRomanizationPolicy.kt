package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData

/**
 * Defines what is safe to expose when the user explicitly asks for Romanized lyrics.
 * A plain Latin-script .lrc is a valid Romanization source even when it does not use
 * Rhythm's supplemental `[romaji]` convention.
 */
object LyricsRomanizationPolicy {
    private const val MIN_LATIN_LETTERS = 2
    private const val MIN_LATIN_SHARE = 0.75

    fun selectLine(original: String, supplemental: String?): String? {
        val explicit = supplemental?.trim().orEmpty()
        if (explicit.isNotEmpty() && isLatinDominant(explicit)) return explicit

        val base = original.trim()
        return base.takeIf(::isLatinDominant)
    }

    fun hasUsableCoverage(original: List<String>, supplemental: List<String>): Boolean {
        val vocalLines = original.indices.filter { index ->
            original[index].any(Character::isLetter)
        }
        if (vocalLines.isEmpty()) return false

        return vocalLines.all { index ->
            selectLine(original[index], supplemental.getOrNull(index)) != null
        }
    }

    fun isLatinDominant(text: String): Boolean {
        var letters = 0
        var latinLetters = 0
        var containsCjk = false

        text.codePoints().forEach { codePoint ->
            if (!Character.isLetter(codePoint)) return@forEach
            letters++
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.LATIN -> latinLetters++
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL,
                Character.UnicodeScript.BOPOMOFO -> containsCjk = true
                else -> Unit
            }
        }

        return !containsCjk &&
            latinLetters >= MIN_LATIN_LETTERS &&
            letters > 0 &&
            latinLetters.toDouble() / letters >= MIN_LATIN_SHARE
    }
}

fun LyricsData.hasUsableTimedRomanization(): Boolean {
    val parsed = syncedLyrics?.let(LyricsParser::parseLyrics).orEmpty()
    return LyricsRomanizationPolicy.hasUsableCoverage(
        original = parsed.map { it.text },
        supplemental = parsed.map { it.romanization.orEmpty() }
    )
}
