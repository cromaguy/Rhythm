/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.automix

import kotlin.math.abs

@JvmInline
value class CamelotKey(val code: String) {
    val number: Int get() = code.dropLast(1).toIntOrNull() ?: 0
    val letter: Char get() = code.lastOrNull()?.uppercaseChar() ?: '?'
    val isValid: Boolean get() = number in 1..12 && (letter == 'A' || letter == 'B')

    override fun toString(): String = if (isValid) "$number$letter" else "?"

    companion object {
        val UNKNOWN = CamelotKey("?")
    }
}

enum class HarmonicCompatibilityTier {
    PERFECT_LOCK,
    ENERGY_OR_MODAL,
    DISSONANT
}

data class HarmonicMatchEvaluation(
    val tier: HarmonicCompatibilityTier,
    val score: Float,
    val keyPairLabel: String,
    val suggestedPitchShiftSemitones: Int = 0,
    val allowedDurationMultiplier: Float = 1.0f
)

/**
 * Normalizes musical keys (ID3/Vorbis text like `"F#m"`, `"Bb major"`, `"CSharp"`, `"8A"`)
 * onto the 24-cell Camelot Wheel and scores harmonic compatibility between adjacent tracks.
 */
object AutoMixCamelotClassifier {
    const val UNKNOWN_HARMONIC_DISTANCE_FACTOR = 0.65f

    private val PITCH_CLASS_TO_CAMELOT_MAJOR = mapOf(
        11 to "1B", 6 to "2B", 1 to "3B", 8 to "4B", 3 to "5B", 10 to "6B",
        5 to "7B", 0 to "8B", 7 to "9B", 2 to "10B", 9 to "11B", 4 to "12B"
    )

    private val PITCH_CLASS_TO_CAMELOT_MINOR = mapOf(
        8 to "1A", 3 to "2A", 10 to "3A", 5 to "4A", 0 to "5A", 7 to "6A",
        2 to "7A", 9 to "8A", 4 to "9A", 11 to "10A", 6 to "11A", 1 to "12A"
    )

    fun parseToCamelotKey(rawKey: String?): CamelotKey {
        if (rawKey.isNullOrBlank()) return CamelotKey.UNKNOWN
        val clean = rawKey.trim()

        val camelotRegex = Regex("""^([1-9]|1[0-2])[a-bA-B]$""")
        if (camelotRegex.matches(clean)) {
            val num = clean.filter { it.isDigit() }
            val letter = clean.last().uppercaseChar()
            return CamelotKey("$num$letter")
        }

        val compact = clean.lowercase()
            .replace("sharp", "#")
            .replace("flat", "b")
            .replace("♯", "#")
            .replace("♭", "b")
            .filterNot(Char::isWhitespace)
        val match = Regex("""^([a-g])([#b]?)(major|maj|minor|min|m)?$""").matchEntire(compact)
            ?: return CamelotKey.UNKNOWN
        val (letter, accidental, mode) = match.destructured
        val natural = mapOf('c' to 0, 'd' to 2, 'e' to 4, 'f' to 5, 'g' to 7, 'a' to 9, 'b' to 11)[letter[0]]
            ?: return CamelotKey.UNKNOWN
        val pitchClass = (natural + when (accidental) {
            "#" -> 1
            "b" -> -1
            else -> 0
        }).mod(12)
        val isMinor = mode.startsWith("m") && !mode.startsWith("maj")
        val code = if (isMinor) PITCH_CLASS_TO_CAMELOT_MINOR[pitchClass] else PITCH_CLASS_TO_CAMELOT_MAJOR[pitchClass]
        return code?.let(::CamelotKey) ?: CamelotKey.UNKNOWN
    }

    fun evaluateCompatibility(
        keyOut: CamelotKey,
        keyIn: CamelotKey
    ): HarmonicMatchEvaluation {
        if (!keyOut.isValid || !keyIn.isValid) {
            val label = "${keyOut} -> ${keyIn}"
            return HarmonicMatchEvaluation(
                tier = HarmonicCompatibilityTier.ENERGY_OR_MODAL,
                score = UNKNOWN_HARMONIC_DISTANCE_FACTOR,
                keyPairLabel = label,
                allowedDurationMultiplier = 0.85f
            )
        }

        val label = "${keyOut} -> ${keyIn}"
        val rawDiff = abs(keyOut.number - keyIn.number)
        val wheelStep = minOf(rawDiff, 12 - rawDiff)
        val sameLetter = keyOut.letter == keyIn.letter

        return when {
            (sameLetter && wheelStep <= 1) || (!sameLetter && wheelStep == 0) -> HarmonicMatchEvaluation(
                tier = HarmonicCompatibilityTier.PERFECT_LOCK,
                score = if (wheelStep == 0 && sameLetter) 1.0f else 0.92f,
                keyPairLabel = label,
                allowedDurationMultiplier = 1.25f
            )
            (sameLetter && wheelStep == 2) || (!sameLetter && wheelStep <= 1) -> HarmonicMatchEvaluation(
                tier = HarmonicCompatibilityTier.ENERGY_OR_MODAL,
                score = 0.72f,
                keyPairLabel = label,
                allowedDurationMultiplier = 0.95f
            )
            else -> HarmonicMatchEvaluation(
                tier = HarmonicCompatibilityTier.DISSONANT,
                score = 0.35f,
                keyPairLabel = label,
                allowedDurationMultiplier = 0.65f
            )
        }
    }
}
