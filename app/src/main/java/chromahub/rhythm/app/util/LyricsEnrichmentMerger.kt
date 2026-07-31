package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData
import java.util.Locale
import kotlin.math.abs

/**
 * Merges timestamp-aligned translation and Romanization tracks without replacing a selected
 * local or embedded original. When that selected track is itself Romaji-only and an online
 * source supplies the CJK original, the CJK track becomes primary and the local track is kept
 * as its Romanization.
 */
object LyricsEnrichmentMerger {
    private const val SUPPLEMENTAL_MATCH_WINDOW_MS = 1_800L

    fun merge(baseLyrics: LyricsData, supplementalLyrics: LyricsData): LyricsData {
        val baseParsed = baseLyrics.syncedLyrics?.let(LyricsParser::parseLyrics).orEmpty()
        val supplementalParsed =
            supplementalLyrics.syncedLyrics?.let(LyricsParser::parseLyrics).orEmpty()
        if (baseParsed.isEmpty()) return supplementalLyrics
        if (supplementalParsed.isEmpty()) return baseLyrics

        val promoteCjkOriginal =
            !baseLyrics.hasCjkOriginalCoverage() &&
                supplementalLyrics.hasCjkOriginalCoverage() &&
                baseLyrics.hasUsableTimedRomanization()
        val primary = if (promoteCjkOriginal) supplementalLyrics else baseLyrics
        val secondary = if (promoteCjkOriginal) baseLyrics else supplementalLyrics
        val primaryParsed = if (promoteCjkOriginal) supplementalParsed else baseParsed
        val secondaryParsed = if (promoteCjkOriginal) baseParsed else supplementalParsed
        val primarySynced = primary.syncedLyrics ?: return secondary

        val translations = secondaryParsed.mapNotNull { line ->
            line.translation
                ?.let { LyricsTranslationPolicy.selectLine(line.text, it) }
                ?.let { line.timestamp to it }
        }
        val romanizations = secondaryParsed.mapNotNull { line ->
            LyricsRomanizationPolicy.selectLine(line.text, line.romanization)
                ?.let { line.timestamp to it }
        }

        fun nearest(values: List<Pair<Long, String>>, timestamp: Long): String? =
            values.minByOrNull { (candidateTimestamp, _) ->
                abs(candidateTimestamp - timestamp)
            }?.takeIf { (candidateTimestamp, _) ->
                abs(candidateTimestamp - timestamp) <= SUPPLEMENTAL_MATCH_WINDOW_MS
            }?.second

        val supplementalLrc = buildList {
            primaryParsed.forEach { line ->
                if (line.translation.isNullOrBlank()) {
                    nearest(translations, line.timestamp)?.let { translation ->
                        add("${formatLrcTimestamp(line.timestamp)}($translation)")
                    }
                }
                if (
                    LyricsRomanizationPolicy.selectLine(
                        line.text,
                        line.romanization
                    ) == null
                ) {
                    nearest(romanizations, line.timestamp)?.let { romanization ->
                        add("${formatLrcTimestamp(line.timestamp)}[$romanization]")
                    }
                }
            }
        }.distinct()
        if (supplementalLrc.isEmpty()) {
            return if (
                supplementalLyrics.requirementsScore() > baseLyrics.requirementsScore()
            ) supplementalLyrics else baseLyrics
        }

        val mergedSynced = "$primarySynced\n${supplementalLrc.joinToString("\n")}"
        val mergedWordByWord = primary.wordByWordLyrics
            ?.let(RhythmLyricsParser::parseWordByWordLyrics)
            ?.takeIf { it.isNotEmpty() }
            ?.map { line ->
                line.copy(
                    translation = line.translation
                        ?: nearest(translations, line.lineTimestamp),
                    romanization = line.romanization
                        ?: nearest(romanizations, line.lineTimestamp)
                )
            }
            ?.let(RhythmLyricsParser::toWordByWordJson)

        val addedTranslation = supplementalLrc.any { it.substringAfter(']').startsWith("(") }
        val addedRomanization = supplementalLrc.any { it.substringAfter(']').startsWith("[") }
        val sourceSuffix = buildList {
            if (addedTranslation) add("Translation")
            if (addedRomanization) add("Romaji")
        }.joinToString("/")
        val secondarySource = secondary.source ?: "supplemental track"

        return primary.copy(
            syncedLyrics = mergedSynced,
            wordByWordLyrics = mergedWordByWord ?: primary.wordByWordLyrics,
            source = if (sourceSuffix.isBlank()) {
                primary.source
            } else {
                "${primary.source ?: "Lyrics"} + $sourceSuffix: $secondarySource"
            }
        )
    }

    private fun LyricsData.requirementsScore(): Int =
        (if (hasUsableTimedRomanization()) 1 else 0) +
            (if (hasUsableTimedTranslation()) 1 else 0) +
            (if (hasCjkOriginalCoverage()) 1 else 0)

    private fun formatLrcTimestamp(timestampMs: Long): String {
        val minutes = timestampMs / 60_000L
        val seconds = (timestampMs % 60_000L) / 1_000L
        val centiseconds = (timestampMs % 1_000L) / 10L
        return "[%02d:%02d.%02d]".format(
            Locale.ROOT,
            minutes,
            seconds,
            centiseconds
        )
    }
}

fun LyricsData.hasCjkOriginalCoverage(): Boolean {
    val parsed = syncedLyrics?.let(LyricsParser::parseLyrics).orEmpty()
    val vocalLines = parsed.filter { line -> line.text.any(Character::isLetter) }
    if (vocalLines.isEmpty()) return false
    val cjkLines = vocalLines.count { line ->
        line.text.codePoints().anyMatch { codePoint ->
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL,
                Character.UnicodeScript.BOPOMOFO -> true
                else -> false
            }
        }
    }
    return cjkLines.toDouble() / vocalLines.size >= 0.35
}
