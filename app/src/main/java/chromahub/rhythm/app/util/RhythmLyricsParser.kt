package chromahub.rhythm.app.util

import android.util.Log
import chromahub.rhythm.app.network.RhythmLyricsLine
import chromahub.rhythm.app.network.RhythmLyricsWord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.abs

object RhythmLyricsParser {
    private const val TAG = "RhythmLyricsParser"
    
    // Pattern to detect voice tags in lyrics text (e.g., "v1: text" or "v2: text")
    private val voiceTagPattern = java.util.regex.Pattern.compile("^(v\\d+):\\s*(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)

    private enum class SupplementalLineKind {
        TRANSLATION,
        ROMANIZATION
    }

    /**
    * Parses Rhythm word-by-word lyrics JSON into structured format
    * @param jsonContent JSON string containing word-by-word lyrics data
     * @return List of parsed word-level lyrics, or empty if parsing fails
     */
    fun parseWordByWordLyrics(jsonContent: String): List<WordByWordLyricLine> {
        if (jsonContent.isBlank()) return emptyList()
        
        return try {
            val gson = Gson()
            val listType = object : TypeToken<List<RhythmLyricsLine>>() {}.type
            val rhythmLyricsLines: List<RhythmLyricsLine> = gson.fromJson(jsonContent, listType)
            
            val parsedLines = rhythmLyricsLines.mapNotNull { line ->
                var words = line.text?.map { word ->
                    WordByWordWord(
                        text = word.text.orEmpty(),
                        isPart = word.part ?: false,
                        timestamp = word.timestamp,
                        endtime = word.endtime
                    )
                } ?: emptyList()

                if (words.isNotEmpty()) {
                    val sortedWords = words
                        .sortedWith(compareBy<WordByWordWord> { it.timestamp }.thenBy { it.endtime })
                        .map { word ->
                            val normalizedEnd = maxOf(word.endtime, word.timestamp)
                            word.copy(endtime = normalizedEnd)
                        }
                    
                    words = sortedWords
                }
                
                // Check if first word contains voice tag and extract it
                var voiceTag: String? = null
                if (words.isNotEmpty()) {
                    val firstWordText = words.first().text
                    val matcher = voiceTagPattern.matcher(firstWordText)
                    if (matcher.matches()) {
                        voiceTag = matcher.group(1)?.lowercase()
                        val cleanedText = matcher.group(2)?.trim() ?: ""
                        // Replace first word with cleaned text (without voice tag)
                        if (cleanedText.isNotEmpty()) {
                            words = listOf(
                                words.first().copy(text = cleanedText)
                            ) + words.drop(1)
                        } else {
                            // If cleaned text is empty, remove the first word entirely
                            words = words.drop(1)
                        }
                    }
                }
                
                if (words.isNotEmpty()) {
                    val mainText = words.joinToString(separator = "") { word ->
                        if (word.isPart && word.text.isNotEmpty()) word.text else " ${word.text}"
                    }.trim()
                    
                    var translation: String? = null
                    var romanization: String? = null
                    
                    line.backgroundText?.forEach { bgText ->
                        val trimmedBg = bgText.trim()
                        if (trimmedBg.isEmpty()) return@forEach
                        
                        val kind = inferSupplementalKind(
                            mainText = mainText,
                            candidateText = trimmedBg,
                            candidateBackground = line.background ?: false
                        )
                        
                        val strippedText = stripSupplementalDelimiters(trimmedBg)
                        if (kind == SupplementalLineKind.ROMANIZATION) {
                            romanization = appendSupplementalUnique(romanization, strippedText).takeIf { it.isNotBlank() }
                        } else {
                            translation = appendSupplementalUnique(translation, strippedText).takeIf { it.isNotBlank() }
                        }
                    }

                    val firstWordTimestamp = words.firstOrNull()?.timestamp ?: 0L
                    val lastWordEndtime = words.maxOfOrNull { it.endtime } ?: firstWordTimestamp
                    val lineStart = maxOf(0L, line.timestamp ?: firstWordTimestamp)
                    val lineEnd = maxOf(line.endtime ?: 0L, lastWordEndtime, lineStart)

                    WordByWordLyricLine(
                        words = words,
                        lineTimestamp = lineStart,
                        lineEndtime = lineEnd,
                        background = line.background ?: false,
                        voiceTag = voiceTag,
                        translation = translation,
                        romanization = romanization,
                        endIsImplicit = line.endIsImplicit ?: false
                    )
                } else {
                    null
                }
            }

            mergeSupplementalLines(parsedLines)
                .sortedWith(compareBy<WordByWordLyricLine> { it.lineTimestamp }.thenBy { it.lineEndtime })
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Rhythm word-by-word lyrics", e)
            emptyList()
        }
    }

    private fun mergeSupplementalLines(lines: List<WordByWordLyricLine>): List<WordByWordLyricLine> {
        if (lines.size < 2) return lines

        val merged = mutableListOf<WordByWordLyricLine>()

        lines.forEach { candidate ->
            val previous = merged.lastOrNull()
            if (previous != null && isSameLyricMoment(previous, candidate)) {
                if (isLikelyDuplicateMainLine(previous, candidate)) {
                    merged[merged.lastIndex] = mergeDuplicateMainLines(previous, candidate)
                    return@forEach
                }

                val mainLine = choosePreferredMainLine(previous, candidate)
                val supplementalLine = if (mainLine === previous) candidate else previous
                val supplementalKind = inferSupplementalKind(
                    mainText = mainLine.asDisplayText(),
                    candidateText = supplementalLine.asDisplayText(),
                    candidateBackground = supplementalLine.background
                )

                if (supplementalKind != null) {
                    merged[merged.lastIndex] = mergeMainWithSupplemental(mainLine, supplementalLine, supplementalKind)
                    return@forEach
                }
            }

            merged += candidate
        }

        return merged
    }

    private fun isSameLyricMoment(previous: WordByWordLyricLine, candidate: WordByWordLyricLine): Boolean {
        val startDiff = abs(previous.lineTimestamp - candidate.lineTimestamp)
        return startDiff <= 60L
    }

    private fun isLikelyDuplicateMainLine(previous: WordByWordLyricLine, candidate: WordByWordLyricLine): Boolean {
        val previousCanonical = canonicalText(previous.asDisplayText())
        val candidateCanonical = canonicalText(candidate.asDisplayText())
        if (previousCanonical.isEmpty() || candidateCanonical.isEmpty()) return false
        return previousCanonical == candidateCanonical
    }

    private fun choosePreferredMainLine(
        first: WordByWordLyricLine,
        second: WordByWordLyricLine
    ): WordByWordLyricLine {
        if (first.background != second.background) {
            return if (!first.background) first else second
        }

        val firstScore = lineQualityScore(first)
        val secondScore = lineQualityScore(second)

        return when {
            secondScore > firstScore -> second
            firstScore > secondScore -> first
            lineTimingSpan(second) > lineTimingSpan(first) -> second
            else -> first
        }
    }

    private fun mergeMainWithSupplemental(
        main: WordByWordLyricLine,
        supplemental: WordByWordLyricLine,
        kind: SupplementalLineKind
    ): WordByWordLyricLine {
        val mainCanonical = canonicalText(main.asDisplayText())
        val supplementalText = stripSupplementalDelimiters(supplemental.asDisplayText())
        val supplementalCanonical = canonicalText(supplementalText)

        var translation = mergeSupplementalField(main.translation, supplemental.translation)
        var romanization = mergeSupplementalField(main.romanization, supplemental.romanization)

        if (supplementalCanonical.isNotEmpty() && supplementalCanonical != mainCanonical) {
            when (kind) {
                SupplementalLineKind.TRANSLATION -> {
                    translation = appendSupplementalUnique(translation, supplementalText).takeIf { it.isNotBlank() }
                }

                SupplementalLineKind.ROMANIZATION -> {
                    romanization = appendSupplementalUnique(romanization, supplementalText).takeIf { it.isNotBlank() }
                }
            }
        }

        return main.copy(
            lineTimestamp = minOf(main.lineTimestamp, supplemental.lineTimestamp),
            lineEndtime = maxLineEnd(main, supplemental),
            background = main.background && supplemental.background,
            voiceTag = main.voiceTag ?: supplemental.voiceTag,
            translation = translation,
            romanization = romanization
        )
    }

    private fun mergeDuplicateMainLines(previous: WordByWordLyricLine, candidate: WordByWordLyricLine): WordByWordLyricLine {
        val preferred = if (lineQualityScore(candidate) > lineQualityScore(previous)) candidate else previous
        val secondary = if (preferred === previous) candidate else previous

        return preferred.copy(
            lineTimestamp = minOf(previous.lineTimestamp, candidate.lineTimestamp),
            lineEndtime = maxLineEnd(previous, candidate),
            background = preferred.background && secondary.background,
            voiceTag = preferred.voiceTag ?: secondary.voiceTag,
            translation = mergeSupplementalField(preferred.translation, secondary.translation),
            romanization = mergeSupplementalField(preferred.romanization, secondary.romanization)
        )
    }

    private fun lineQualityScore(line: WordByWordLyricLine): Int {
        if (line.words.isEmpty()) return 0

        val nonBlankWords = line.words.count { it.text.isNotBlank() }
        val distinctWordStarts = line.words.map { it.timestamp }.distinct().size
        val advancingStarts = line.words.zipWithNext().count { (first, second) ->
            second.timestamp > first.timestamp
        }
        val positiveDurations = line.words.count { it.endtime > it.timestamp }
        val partWords = line.words.count { it.isPart }
        val hasSupplementalMeta = !line.translation.isNullOrBlank() || !line.romanization.isNullOrBlank()

        return (if (!line.background) 20 else 0) +
            (distinctWordStarts * 32) +
            (advancingStarts * 24) +
            (partWords * 16) +
            (positiveDurations * 8) +
            (nonBlankWords * 2) +
            (if (hasSupplementalMeta) 6 else 0)
    }

    private fun lineTimingSpan(line: WordByWordLyricLine): Long {
        if (line.words.isEmpty()) return (line.lineEndtime - line.lineTimestamp).coerceAtLeast(0L)
        val minWordTimestamp = line.words.minOf { it.timestamp }
        val maxWordEnd = line.words.maxOf { it.endtime }
        return (maxWordEnd - minWordTimestamp).coerceAtLeast(0L)
    }

    private fun maxLineEnd(first: WordByWordLyricLine, second: WordByWordLyricLine): Long {
        val firstWordEnd = first.words.maxOfOrNull { it.endtime } ?: first.lineEndtime
        val secondWordEnd = second.words.maxOfOrNull { it.endtime } ?: second.lineEndtime
        return maxOf(first.lineEndtime, second.lineEndtime, firstWordEnd, secondWordEnd)
    }

    private fun WordByWordLyricLine.asDisplayText(): String {
        return joinWords(words)
    }

    /** Treats `part` as a join hint without collapsing ordinary Latin word boundaries. */
    private fun joinWords(words: List<WordByWordWord>): String {
        val nonBlankWords = words.filter { it.text.isNotBlank() }
        if (nonBlankWords.isEmpty()) return ""
        val hasExplicitBoundaries = nonBlankWords.drop(1).any { !it.isPart }

        return buildString {
            nonBlankWords.forEachIndexed { index, word ->
                val text = word.text.trim()
                val previous = nonBlankWords.getOrNull(index - 1)?.text?.trim().orEmpty()
                val attach = index == 0 ||
                    shouldAttachWithoutSpace(
                        previous = previous,
                        current = text,
                        isPart = word.isPart,
                        hasExplicitBoundaries = hasExplicitBoundaries
                    )
                if (!attach) append(' ')
                append(text)
            }
        }
    }

    private fun shouldAttachWithoutSpace(
        previous: String,
        current: String,
        isPart: Boolean,
        hasExplicitBoundaries: Boolean
    ): Boolean {
        if (previous.isEmpty() || current.isEmpty()) return true
        if (containsCjk(previous) || containsCjk(current)) return true
        if (current.first() in ".,!?;:%)]}»”’") return true
        if (previous.last() in "([{«“") return true
        if (current.first() in "'’-‐‑–—" || previous.last() in "'’-‐‑–—") return true
        return isPart && hasExplicitBoundaries
    }

    private fun containsCjk(text: String): Boolean =
        text.codePoints().anyMatch { codePoint ->
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL,
                Character.UnicodeScript.BOPOMOFO -> true
                else -> false
            }
        }

    private fun canonicalText(text: String): String {
        return text
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    private fun inferSupplementalKind(
        mainText: String,
        candidateText: String,
        candidateBackground: Boolean
    ): SupplementalLineKind? {
        val trimmed = candidateText.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length > 2) {
            return SupplementalLineKind.TRANSLATION
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            return SupplementalLineKind.ROMANIZATION
        }

        val mainHasNonAscii = mainText.any { it.code > 127 }
        val candidateHasNonAscii = trimmed.any { it.code > 127 }

        if (mainHasNonAscii && !candidateHasNonAscii) {
            return SupplementalLineKind.ROMANIZATION
        }

        if (!mainHasNonAscii && candidateHasNonAscii) {
            return SupplementalLineKind.TRANSLATION
        }

        if (candidateBackground) {
            return SupplementalLineKind.TRANSLATION
        }

        return null
    }

    private fun stripSupplementalDelimiters(text: String): String {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length > 2 -> {
                trimmed.substring(1, trimmed.length - 1).trim()
            }

            trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2 -> {
                trimmed.substring(1, trimmed.length - 1).trim()
            }

            else -> trimmed
        }
    }

    private fun appendSupplementalUnique(existing: String?, incoming: String): String {
        val incomingTrimmed = incoming.trim()
        if (incomingTrimmed.isEmpty()) return existing.orEmpty()

        val existingLines = existing
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet()
            ?: mutableSetOf()

        if (incomingTrimmed in existingLines) {
            return existing.orEmpty().ifEmpty { incomingTrimmed }
        }

        return if (existing.isNullOrBlank()) {
            incomingTrimmed
        } else {
            "$existing\n$incomingTrimmed"
        }
    }

    private fun mergeSupplementalField(primary: String?, secondary: String?): String? {
        val merged = appendSupplementalUnique(primary, secondary.orEmpty())
        return merged.takeIf { it.isNotBlank() }
    }
    
    /**
     * Convert word-by-word lyrics to plain text (for display when word highlighting is not needed)
     */
    fun toPlainText(wordByWordLines: List<WordByWordLyricLine>): String {
        return wordByWordLines.joinToString("\n") { line ->
            joinWords(line.words)
        }
    }
    
    /**
     * Convert word-by-word lyrics to LRC format (for compatibility)
     */
    fun toLRCFormat(wordByWordLines: List<WordByWordLyricLine>): String {
        return wordByWordLines.flatMap { line ->
            val timestamp = formatLRCTimestamp(line.lineTimestamp)
            val text = joinWords(line.words)
            buildList {
                add("[$timestamp]$text")
                line.translation?.takeIf { it.isNotBlank() }?.let { add("[$timestamp]($it)") }
                line.romanization?.takeIf { it.isNotBlank() }?.let { add("[$timestamp][$it]") }
            }
        }
            .joinToString("\n")
    }
    
    private fun formatLRCTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = (milliseconds % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, millis)
    }

    /**
     * Parses a TTML time expression string to a Long representing milliseconds.
     * Supports formats like:
     * - hh:mm:ss.ms or mm:ss.ms
     * - Metric values like 5.5s, 120ms, etc.
     * - Raw decimal/double values (treated as seconds)
     */
    fun parseTtmlTime(timeStr: String?): Long? {
        if (timeStr == null || timeStr.isBlank()) return null
        val cleanStr = timeStr.trim()
        
        // Try to match time offset with metric suffix (e.g. "5.5s", "120ms")
        val metricMatch = Regex("([0-9.]+)\\s*(s|ms|h|m)").matchEntire(cleanStr)
        if (metricMatch != null) {
            val value = metricMatch.groupValues[1].toDoubleOrNull() ?: return null
            val metric = metricMatch.groupValues[2]
            return when (metric) {
                "ms" -> value.toLong()
                "s" -> (value * 1000).toLong()
                "m" -> (value * 60000).toLong()
                "h" -> (value * 3600000).toLong()
                else -> null
            }
        }
        
        // Or it might be a raw double (in seconds, e.g. "5.5")
        val rawSeconds = cleanStr.toDoubleOrNull()
        if (rawSeconds != null) {
            return (rawSeconds * 1000).toLong()
        }
        
        // Try clock formats: hh:mm:ss.sss or mm:ss.sss
        val parts = cleanStr.split(":")
        if (parts.size >= 2) {
            try {
                var hours = 0L
                var minutes = 0L
                var seconds = 0.0
                
                if (parts.size == 3) {
                    hours = parts[0].toLongOrNull() ?: 0L
                    minutes = parts[1].toLongOrNull() ?: 0L
                    seconds = parts[2].toDoubleOrNull() ?: 0.0
                } else if (parts.size == 2) {
                    minutes = parts[0].toLongOrNull() ?: 0L
                    seconds = parts[1].toDoubleOrNull() ?: 0.0
                }
                
                val totalMs = (hours * 3600 * 1000) + (minutes * 60 * 1000) + (seconds * 1000).toLong()
                return totalMs
            } catch (e: Exception) {
                // Ignore and try fallback
            }
        }
        
        return null
    }

    /**
     * Convert word-by-word lyrics back to JSON format (inverse of parseWordByWordLyrics).
     * Handles voice tags, translations, and romanization.
     */
    fun toWordByWordJson(lines: List<WordByWordLyricLine>): String {
        val gson = Gson()
        val rhythmLines = lines.map { line ->
            val words = line.words.map { word ->
                RhythmLyricsWord(
                    text = word.text,
                    part = word.isPart,
                    timestamp = word.timestamp,
                    endtime = word.endtime
                )
            }
            val wordsWithVoice = if (line.voiceTag != null && words.isNotEmpty()) {
                listOf(words.first().copy(text = "${line.voiceTag}: ${words.first().text}")) + words.drop(1)
            } else {
                words
            }
            val backgroundText = buildList {
                line.translation?.let { add("($it)") }
                line.romanization?.let { add("[$it]") }
            }.ifEmpty { null }

            RhythmLyricsLine(
                text = wordsWithVoice,
                background = if (line.background) true else null,
                backgroundText = backgroundText,
                oppositeTurn = null,
                timestamp = line.lineTimestamp,
                endtime = line.lineEndtime,
                endIsImplicit = line.endIsImplicit
            )
        }
        return gson.toJson(rhythmLines)
    }

    /**
     * Parse TTML (Timed Text Markup Language) formatted synchronized lyrics.
     * Extracts lines (<p>) and word-by-word timestamps (<span>).
     */
    fun parseTtmlLyrics(ttmlContent: String): List<RhythmLyricsLine> {
        val parsed = parseTtml(audioMimeType = null, lyricText = ttmlContent)
        if (parsed is SemanticLyrics.SyncedLyrics) {
            return parsed.text.map { semanticLine ->
                val slWords = semanticLine.words
                val rhythmLyricsWords = slWords?.mapIndexed { idx, word ->
                    val rawText = semanticLine.text.substring(word.charRange)
                    val trimmedText = rawText.trim()
                    
                    val leadingSpaces = rawText.takeWhile { it.isWhitespace() }.length
                    val trailingSpaces = rawText.takeLastWhile { it.isWhitespace() }.length
                    val trimmedStart = word.charRange.first + leadingSpaces
                    val trimmedEnd = word.charRange.last - trailingSpaces

                    val isPart = if (idx > 0 && trimmedText.isNotEmpty()) {
                        val prevWord = slWords[idx - 1]
                        val prevRawText = semanticLine.text.substring(prevWord.charRange)
                        val prevTrimmedText = prevRawText.trim()
                        if (prevTrimmedText.isNotEmpty()) {
                            val prevTrailingSpaces = prevRawText.takeLastWhile { it.isWhitespace() }.length
                            val prevTrimmedEnd = prevWord.charRange.last - prevTrailingSpaces
                            
                            val gap = semanticLine.text.substring(prevTrimmedEnd + 1, trimmedStart)
                            gap.isEmpty()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                    RhythmLyricsWord(
                        text = trimmedText,
                        part = isPart,
                        timestamp = word.begin.toLong(),
                        endtime = (word.endInclusive ?: word.begin).toLong()
                    )
                } ?: listOf(
                    RhythmLyricsWord(
                        text = semanticLine.text,
                        part = false,
                        timestamp = semanticLine.start.toLong(),
                        endtime = semanticLine.end.toLong()
                    )
                )
                
                RhythmLyricsLine(
                    text = rhythmLyricsWords,
                    background = semanticLine.speaker?.isBackground ?: false,
                    backgroundText = if (semanticLine.isTranslated) listOf(semanticLine.text) else null,
                    oppositeTurn = semanticLine.speaker?.isVoice2,
                    timestamp = semanticLine.start.toLong(),
                    endtime = semanticLine.end.toLong(),
                    endIsImplicit = semanticLine.endIsImplicit
                )
            }
        }
        return emptyList()
    }
}

/**
 * Represents a line of lyrics with word-level timing
 */
data class WordByWordLyricLine(
    val words: List<WordByWordWord>,
    val lineTimestamp: Long,
    val lineEndtime: Long,
    val background: Boolean = false,
    val voiceTag: String? = null, // Voice tag (v1, v2, v3, etc.) for multi-voice lyrics
    val translation: String? = null,
    val romanization: String? = null,
    val endIsImplicit: Boolean = false
)

/**
 * Represents a single word with precise timing
 */
data class WordByWordWord(
    val text: String,
    val isPart: Boolean, // true if this is a syllable/part of a split word
    val timestamp: Long, // start time in milliseconds
    val endtime: Long // end time in milliseconds
)
