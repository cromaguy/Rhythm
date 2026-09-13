/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.util.Log
import chromahub.rhythm.app.network.RhythmLyricsLine
import chromahub.rhythm.app.network.RhythmLyricsWord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import kotlin.math.abs

object RhythmLyricsParser {
    private const val TAG = "RhythmLyricsParser"
    
    // Pattern to detect voice tags in lyrics text (e.g., "v1: text", "[v1] text", "<v1> text", "(v1) text", "{v1} text", "v1. text", "voice1: text")
    private val voiceTagPattern = java.util.regex.Pattern.compile(
        """^(?:\[(v\d+|voice\d+)\][:.]?|<(v\d+|voice\d+)>[:.]?|\((v\d+|voice\d+)\)[:.]?|\{(v\d+|voice\d+)\}[:.]?|(v\d+|voice\d+)[:.])\s*(.*)$""",
        java.util.regex.Pattern.CASE_INSENSITIVE
    )

    fun normalizeVoiceTag(rawTag: String?): String? {
        if (rawTag.isNullOrBlank()) return null
        val lower = rawTag.trim().lowercase()
        return when {
            lower == "v1" || lower == "voice1" || lower == "voice1background" || lower == "voice" || lower == "1" -> "v1"
            lower == "v2" || lower == "voice2" || lower == "voice2background" || lower == "2" -> "v2"
            lower == "v3" || lower == "voice3" || lower == "group" || lower == "groupbackground" || lower == "3" -> "v3"
            lower.startsWith("voice") -> "v" + lower.removePrefix("voice")
            lower.startsWith("v") -> lower
            else -> lower
        }
    }

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
            val hasOppositeTurnInSong = rhythmLyricsLines.any { it.oppositeTurn == true }
            
            val parsedLines = rhythmLyricsLines.mapNotNull { line ->
                var words = line.text?.mapNotNull { word ->
                    val text = word.text.orEmpty().trim()
                    if (text.isEmpty()) return@mapNotNull null
                    WordByWordWord(
                        text = text,
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
                        val rawTag = matcher.group(1) ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: matcher.group(5) ?: ""
                        voiceTag = normalizeVoiceTag(rawTag)
                        val cleanedText = matcher.group(6)?.trim() ?: ""
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
                
                if (voiceTag == null) {
                    if (line.oppositeTurn == true) {
                        voiceTag = "v2"
                    } else if (hasOppositeTurnInSong) {
                        voiceTag = "v1"
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
                    val rawEnd = line.endtime ?: 0L
                    val safeRawEnd = if (rawEnd > 86_400_000L) lastWordEndtime else rawEnd
                    val lineEnd = maxOf(safeRawEnd, lastWordEndtime, lineStart)

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

            val sortedInitial = parsedLines.sortedWith(
                compareBy<WordByWordLyricLine> { it.lineTimestamp }
                    .thenBy { it.lineEndtime }
                    .thenByDescending { lineQualityScore(it) }
            )

            mergeSupplementalLines(sortedInitial)
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
        val firstHasNonAscii = first.asDisplayText().any { it.code > 127 }
        val secondHasNonAscii = second.asDisplayText().any { it.code > 127 }
        if (firstHasNonAscii && !secondHasNonAscii) return first
        if (!firstHasNonAscii && secondHasNonAscii) return second

        val firstScore = lineQualityScore(first)
        val secondScore = lineQualityScore(second)

        return when {
            secondScore > firstScore -> second
            firstScore > secondScore -> first
            first.background != second.background -> if (!first.background) first else second
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

        if (romanization != null && translation != null && canonicalText(romanization) == canonicalText(translation)) {
            romanization = null
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

        var trans = mergeSupplementalField(preferred.translation, secondary.translation)
        var rom = mergeSupplementalField(preferred.romanization, secondary.romanization)
        if (rom != null && trans != null && canonicalText(rom) == canonicalText(trans)) {
            rom = null
        }

        return preferred.copy(
            lineTimestamp = minOf(previous.lineTimestamp, candidate.lineTimestamp),
            lineEndtime = maxLineEnd(previous, candidate),
            background = preferred.background && secondary.background,
            voiceTag = preferred.voiceTag ?: secondary.voiceTag,
            translation = trans,
            romanization = rom
        )
    }

    private fun lineQualityScore(line: WordByWordLyricLine): Int {
        if (line.words.isEmpty()) return 0

        val nonBlankWords = line.words.count { it.text.isNotBlank() }
        val distinctWordStarts = line.words.map { it.timestamp }.distinct().size
        val hasTrueWordTiming = (line.words.size > 1 && distinctWordStarts > 1) ||
            line.words.any { it.isPart } ||
            (line.words.any { it.endtime > it.timestamp } && (distinctWordStarts > 1 || line.words.size > 1))
        val advancingStarts = line.words.zipWithNext().count { (first, second) ->
            second.timestamp > first.timestamp
        }
        val positiveDurations = line.words.count { it.endtime > it.timestamp }
        val partWords = line.words.count { it.isPart }
        val hasSupplementalMeta = !line.translation.isNullOrBlank() || !line.romanization.isNullOrBlank()

        return (if (hasTrueWordTiming) 10000 else 0) +
            (if (!line.background) 20 else 0) +
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
        return words.joinToString(separator = "") { word ->
            if (word.isPart && word.text.isNotEmpty()) word.text else " ${word.text}"
        }.trim()
    }

    private fun canonicalText(text: String): String {
        return text
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    fun hasNonLatinScript(text: String): Boolean {
        return text.any { c ->
            if (!c.isLetter()) return@any false
            val script = Character.UnicodeScript.of(c.code)
            script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED
        }
    }

    fun isLatinBased(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.all { c ->
            val script = Character.UnicodeScript.of(c.code)
            script == Character.UnicodeScript.LATIN
        }
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

        if (candidateBackground) {
            return SupplementalLineKind.TRANSLATION
        }

        if (hasNonLatinScript(mainText) && isLatinBased(trimmed)) {
            return SupplementalLineKind.ROMANIZATION
        }

        if (isLatinBased(mainText) && hasNonLatinScript(trimmed)) {
            return SupplementalLineKind.TRANSLATION
        }

        val mainHasNonAscii = mainText.any { it.code > 127 }
        val candidateHasNonAscii = trimmed.any { it.code > 127 }

        if (mainHasNonAscii && !candidateHasNonAscii) {
            return SupplementalLineKind.ROMANIZATION
        }

        if (!mainHasNonAscii && candidateHasNonAscii) {
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

        val incomingCanonical = canonicalText(incomingTrimmed)
        val existingCanonicals = existing
            ?.lineSequence()
            ?.map { canonicalText(it) }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

        if (incomingCanonical.isNotEmpty() && incomingCanonical in existingCanonicals) {
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
            line.words.joinToString("") { word ->
                if (word.isPart && word.text.isNotEmpty()) {
                    word.text // syllable, no space before
                } else {
                    " ${word.text}"
                }
            }.trim()
        }
    }
    
    /**
     * Convert word-by-word lyrics to LRC format (for compatibility)
     */
    fun toLRCFormat(wordByWordLines: List<WordByWordLyricLine>): String {
        return wordByWordLines.joinToString("\n") { line ->
            val timestamp = formatLRCTimestamp(line.lineTimestamp)
            val text = line.words.joinToString("") { word ->
                if (word.isPart && word.text.isNotEmpty()) {
                    word.text
                } else {
                    " ${word.text}"
                }
            }.trim()
            "[$timestamp]$text"
        }
    }

    /**
     * Convert word-by-word lyrics to Enhanced LRC format (ELRC) with word-level timestamps <mm:ss.xx>
     */
    fun toEnhancedLRCFormat(wordByWordLines: List<WordByWordLyricLine>): String {
        return wordByWordLines.joinToString("\n") { line ->
            val timestamp = formatLRCTimestamp(line.lineTimestamp)
            val isWordTimed = isLineWordTimed(line)
            val wordsText = if (isWordTimed) {
                line.words.joinToString("") { word ->
                    val wordTime = formatLRCTimestamp(word.timestamp)
                    if (word.isPart && word.text.isNotEmpty()) {
                        "<$wordTime>${word.text}"
                    } else {
                        " <$wordTime>${word.text}"
                    }
                }.trimStart()
            } else {
                line.words.joinToString("") { word ->
                    if (word.isPart && word.text.isNotEmpty()) {
                        word.text
                    } else {
                        " ${word.text}"
                    }
                }.trim()
            }

            buildString {
                append("[$timestamp]$wordsText")
                if (!line.romanization.isNullOrBlank()) {
                    if (hasNonLatinScript(wordsText)) {
                        append("\n[$timestamp]${line.romanization}")
                    } else {
                        append("\n[$timestamp][${line.romanization}]")
                    }
                }
                if (!line.translation.isNullOrBlank()) {
                    append("\n[$timestamp](${line.translation})")
                }
            }
        }
    }

    /**
     * Convert word-by-word lyrics to standard TTML XML format
     */
    fun toTtmlFormat(
        wordByWordLines: List<WordByWordLyricLine>,
        title: String? = null,
        artist: String? = null
    ): String {
        val hasWords = hasWordTiming(wordByWordLines)
        val timingAttr = if (hasWords) "Word" else "Line"

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\" itunes:timing=\"$timingAttr\">")
            appendLine("  <head>")
            appendLine("    <metadata>")
            if (!title.isNullOrBlank() || !artist.isNullOrBlank()) {
                appendLine("      <iTunesMetadata>")
                if (!title.isNullOrBlank()) {
                    appendLine("        <songTitle>${escapeXml(title)}</songTitle>")
                }
                if (!artist.isNullOrBlank()) {
                    appendLine("        <artist>${escapeXml(artist)}</artist>")
                }
                appendLine("      </iTunesMetadata>")
            }
            appendLine("    </metadata>")
            appendLine("  </head>")
            appendLine("  <body>")
            appendLine("    <div>")
            for (line in wordByWordLines) {
                val begin = formatTtmlTimestamp(line.lineTimestamp)
                val effectiveEnd = line.lineEndtime.takeIf { it > line.lineTimestamp }
                    ?: line.words.maxOfOrNull { it.endtime }?.takeIf { it > line.lineTimestamp }
                    ?: (line.lineTimestamp + 3000L)
                val end = formatTtmlTimestamp(effectiveEnd)

                if (isLineWordTimed(line)) {
                    appendLine("      <p begin=\"$begin\" end=\"$end\">")
                    for (word in line.words) {
                        val wBegin = formatTtmlTimestamp(word.timestamp)
                        val wEnd = formatTtmlTimestamp(word.endtime.coerceAtLeast(word.timestamp))
                        val wordText = escapeXml(word.text + if (!word.isPart) " " else "")
                        appendLine("        <span begin=\"$wBegin\" end=\"$wEnd\">$wordText</span>")
                    }
                    appendLine("      </p>")
                } else {
                    val lineText = escapeXml(line.words.joinToString("") { if (it.isPart) it.text else " " + it.text }.trim())
                    appendLine("      <p begin=\"$begin\" end=\"$end\">$lineText</p>")
                }
            }
            appendLine("    </div>")
            appendLine("  </body>")
            appendLine("</tt>")
        }
    }

    /**
     * Parse Enhanced LRC content with word-level timestamps directly into a list of WordByWordLyricLine
     */
    fun parseEnhancedLRCtoWordByWord(lrcContent: String): List<WordByWordLyricLine> {
        val enhanced = LyricsParser.parseEnhancedLRC(lrcContent)
        return enhanced.map { line ->
            WordByWordLyricLine(
                words = line.words.map {
                    WordByWordWord(
                        text = it.text,
                        isPart = it.isPart,
                        timestamp = it.timestamp,
                        endtime = it.endtime
                    )
                },
                lineTimestamp = line.lineTimestamp,
                lineEndtime = line.lineEndtime,
                translation = line.translation,
                romanization = line.romanization
            )
        }
    }

    fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun formatTtmlTimestamp(milliseconds: Long): String {
        val clamped = milliseconds.coerceAtLeast(0L)
        val totalSeconds = clamped / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = clamped % 1000
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
    }

    private fun formatLRCTimestamp(milliseconds: Long): String {
        val clamped = milliseconds.coerceAtLeast(0L)
        val totalSeconds = clamped / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = (clamped % 1000) / 10
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, millis)
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
                oppositeTurn = if (line.voiceTag in listOf("v2", "voice2")) true else null,
                timestamp = line.lineTimestamp,
                endtime = line.lineEndtime,
                endIsImplicit = line.endIsImplicit
            )
        }
        return gson.toJson(rhythmLines)
    }

    /**
     * Checks if the provided text looks like TTML formatted lyrics
     */
    fun isTtmlContent(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (!trimmed.startsWith("<")) return false
        val lower = trimmed.lowercase()
        return lower.contains("<tt") ||
                lower.contains("http://www.w3.org/ns/ttml") ||
                lower.contains("http://music.apple.com/lyric-ttml-internal") ||
                lower.contains("http://itunes.apple.com/lyric-ttml-extensions") ||
                lower.contains("xmlns:ttm") ||
                lower.contains("xmlns:itunes") ||
                (lower.contains("<p ") && (lower.contains("begin=") || lower.contains("dur=") || lower.contains("end="))) ||
                (lower.contains("<span ") && (lower.contains("begin=") || lower.contains("dur=") || lower.contains("end="))) ||
                (lower.contains("<body>") && lower.contains("<p"))
    }

    /**
     * Parse TTML (Timed Text Markup Language) formatted synchronized lyrics.
     * Extracts lines (<p>) and word-by-word timestamps (<span>).
     * Attaches translated lines (e.g. from iTunes translations) to their corresponding original lines.
     */
    fun parseTtmlLyrics(ttmlContent: String): List<RhythmLyricsLine> {
        val parsed = try {
            parseTtml(audioMimeType = null, lyricText = ttmlContent)
        } catch (e: Exception) {
            Log.w(TAG, "SemanticLyrics.parseTtml threw exception: ${e.message}")
            null
        }

        if (parsed is SemanticLyrics.SyncedLyrics && parsed.text.isNotEmpty()) {
            val result = mutableListOf<RhythmLyricsLine>()

            for (semanticLine in parsed.text) {
                if (semanticLine.isTranslated) {
                    // This is a translation line from SemanticLyrics; attach to previous original line
                    val prev = result.lastOrNull()
                    if (prev != null) {
                        val transText = semanticLine.text.trim()
                        if (transText.isNotEmpty()) {
                            val bgList = prev.backgroundText?.toMutableList() ?: mutableListOf()
                            val formattedTrans = "($transText)"
                            if (!bgList.contains(transText) && !bgList.contains(formattedTrans)) {
                                bgList.add(formattedTrans)
                                result[result.lastIndex] = prev.copy(backgroundText = bgList)
                            }
                        }
                    }
                    continue
                }

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
                
                result.add(
                    RhythmLyricsLine(
                        text = rhythmLyricsWords,
                        background = semanticLine.speaker?.isBackground ?: false,
                        backgroundText = null,
                        oppositeTurn = semanticLine.speaker?.isVoice2,
                        timestamp = semanticLine.start.toLong(),
                        endtime = semanticLine.end.toLong(),
                        endIsImplicit = semanticLine.endIsImplicit
                    )
                )
            }
            if (result.isNotEmpty()) {
                return result
            }
        }

        // Fallback to regex-based TTML extraction if XML parsing produced no lines
        val fallback = parseTtmlFallback(ttmlContent)
        if (fallback.isNotEmpty()) {
            Log.d(TAG, "Successfully parsed TTML via fallback parser (${fallback.size} lines)")
            return fallback
        }

        return emptyList()
    }

    /**
     * Regex-based fallback parser for TTML lyrics when XML pull parser cannot parse the document.
     */
    fun parseTtmlFallback(ttmlContent: String): List<RhythmLyricsLine> {
        if (ttmlContent.isBlank()) return emptyList()

        try {
            val ttTimingMatch = Regex("<tt[^>]*?\\b(?:[\\w:-]+:)?timing=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(ttmlContent)
            val ttTiming = ttTimingMatch?.groupValues?.get(1)
            val isLineOrNoneTiming = ttTiming.equals("Line", ignoreCase = true) || ttTiming.equals("None", ignoreCase = true)

            // Extract translations if available
            val translationsMap = mutableMapOf<String, String>()
            val translationRegex = Regex("<text[^>]*?\\bfor=[\"']([^\"']+)[\"'][^>]*>(.*?)</text>", RegexOption.DOT_MATCHES_ALL)
            for (match in translationRegex.findAll(ttmlContent)) {
                val key = match.groupValues[1].trim()
                val text = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                if (key.isNotEmpty() && text.isNotEmpty()) {
                    translationsMap[key] = text
                }
            }

            val pRegex = Regex("<p\\s+([^>]*?)>(.*?)</p>|<p\\s+([^>]*?)/>", RegexOption.DOT_MATCHES_ALL)
            val attrRegex = Regex("([\\w:-]+)=[\"']([^\"']*)[\"']")
            val spanRegex = Regex("<span\\s+([^>]*?)>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)

            val lines = mutableListOf<RhythmLyricsLine>()
            var lineIdx = 1

            for (pMatch in pRegex.findAll(ttmlContent)) {
                val attrsStr = if (pMatch.groupValues[1].isNotEmpty()) pMatch.groupValues[1] else pMatch.groupValues[3]
                val innerContent = if (pMatch.groupValues[2].isNotEmpty()) pMatch.groupValues[2] else ""

                val attrs = mutableMapOf<String, String>()
                for (attrMatch in attrRegex.findAll(attrsStr)) {
                    val key = attrMatch.groupValues[1].substringAfterLast(':').lowercase()
                    attrs[key] = attrMatch.groupValues[2]
                }

                val beginStr = attrs["begin"]
                val endStr = attrs["end"]
                val durStr = attrs["dur"]
                val key = attrs["key"] ?: "L$lineIdx"
                lineIdx++

                val beginMs = parseTtmlTime(beginStr) ?: continue
                val durMs = parseTtmlTime(durStr)
                val endMs = parseTtmlTime(endStr) ?: (if (durMs != null) beginMs + durMs else beginMs + 3000L)

                // Check for spans inside <p>
                val spans = if (!isLineOrNoneTiming) spanRegex.findAll(innerContent).toList() else emptyList()
                val words = mutableListOf<RhythmLyricsWord>()

                if (spans.isNotEmpty()) {
                    for ((spanIdx, spanMatch) in spans.withIndex()) {
                        val spanAttrsStr = spanMatch.groupValues[1]
                        val spanText = spanMatch.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                        if (spanText.isEmpty()) continue

                        val spanAttrs = mutableMapOf<String, String>()
                        for (attrMatch in attrRegex.findAll(spanAttrsStr)) {
                            val k = attrMatch.groupValues[1].substringAfterLast(':').lowercase()
                            spanAttrs[k] = attrMatch.groupValues[2]
                        }

                        val sBegin = parseTtmlTime(spanAttrs["begin"]) ?: beginMs
                        val sEnd = parseTtmlTime(spanAttrs["end"]) ?: (sBegin + 500L)

                        words.add(
                            RhythmLyricsWord(
                                text = spanText,
                                part = spanIdx > 0 && !spanMatch.groupValues[2].startsWith(" "),
                                timestamp = sBegin,
                                endtime = sEnd
                            )
                        )
                    }
                }

                val cleanText = innerContent.replace(Regex("<[^>]*>"), "").trim()
                if (cleanText.isEmpty() && words.isEmpty()) continue

                val finalWords = if (words.isNotEmpty()) words else listOf(
                    RhythmLyricsWord(
                        text = cleanText,
                        part = false,
                        timestamp = beginMs,
                        endtime = endMs
                    )
                )

                val translation = translationsMap[key]
                val bgText = if (translation != null) listOf("($translation)") else null

                val isVoice2 = attrs["agent"]?.lowercase() in listOf("v2", "2", "voice2") ||
                               attrs["role"]?.lowercase() in listOf("v2", "voice2") ||
                               attrs["textalign"]?.lowercase() in listOf("right", "end") ||
                               attrs["align"]?.lowercase() in listOf("right", "end")

                lines.add(
                    RhythmLyricsLine(
                        text = finalWords,
                        background = false,
                        backgroundText = bgText,
                        oppositeTurn = if (isVoice2) true else null,
                        timestamp = beginMs,
                        endtime = endMs,
                        endIsImplicit = false
                    )
                )
            }

            return lines
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseTtmlFallback: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Checks if the parsed word-by-word lines actually contain real word-level timing information.
     * Returns false if the lyrics are line-timed only (e.g. from TTML with itunes:timing="Line",
     * plain LRC, or lines containing only a single word spanning the entire line).
     */
    fun hasWordTiming(lines: List<WordByWordLyricLine>): Boolean {
        if (lines.isEmpty()) return false
        return lines.any { line ->
            isLineWordTimed(line)
        }
    }

    /**
     * Checks if a single lyric line has genuine word-level timing.
     */
    fun isLineWordTimed(line: WordByWordLyricLine): Boolean {
        if (line.words.size > 1) {
            val distinctTimestamps = line.words.map { it.timestamp }.distinct().size
            if (distinctTimestamps > 1) return true
            if (line.words.any { it.isPart }) return true
            val distinctEndtimes = line.words.map { it.endtime }.distinct().size
            if (distinctEndtimes > 1) return true
        }
        return false
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
