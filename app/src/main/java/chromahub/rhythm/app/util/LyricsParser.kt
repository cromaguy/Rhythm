/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.util.Log
import java.util.regex.Pattern
import java.util.Locale
import chromahub.rhythm.app.shared.data.model.AppSettings

object LyricsParser {

    private const val MAX_PARSE_INPUT_CHARS = 250_000
    private const val MAX_PARSE_LINES = 6_000
    private const val MAX_LINE_LENGTH = 4_000
    private const val MAX_TIMESTAMPS_PER_LINE = 80
    private const val MAX_PARSED_LYRIC_LINES = 12_000

    // Enhanced regex pattern to support various LRC timestamp formats
    // Supports: [mm:ss.xx], [mm:ss:xx], [mm:ss.xxx], [mm:ss], and even [hh:mm:ss.xxx]
    private val timestampPattern = Pattern.compile("\\[\\s*(\\d{1,3})\\s*:\\s*(\\d{2})(?:\\s*[.:]?\\s*(\\d{0,3}))?\\s*\\]")
    
    // Metadata pattern includes:
    // - Standard LRC metadata: ar (artist), ti (title), al (album), by (creator), offset, re (editor), ve (version), length
    // - Voice/part tags: v1, v2, v3, etc. (used in duets/multi-voice songs to indicate different singers)
    // - Tool tags: editor, tool, version (lyrics editor/tool information)
    // Voice tags (v1:, v2:, etc.) are commonly used to label different singers in duets or harmonies
    private val metadataPattern = Pattern.compile("\\[(ar|ti|al|by|offset|re|ve|length|v\\d+|version|editor|tool):[^\\]]*\\]", Pattern.CASE_INSENSITIVE)
    
    // Enhanced LRC word-level timestamp pattern: <mm:ss.xx> or <mm:ss.xxx>
    private val wordTimestampPattern = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:\\.(\\d{2,3}))?>")
    
    // Pattern to detect voice tags in lyrics text (e.g., "v1: text", "[v1] text", "<v1> text", "(v1) text", "{v1} text", "v1. text", "voice1: text")
    private val voiceTagInLinePattern = Pattern.compile(
        """^(?:\[(v\d+|voice\d+)\][:.]?|<(v\d+|voice\d+)>[:.]?|\((v\d+|voice\d+)\)[:.]?|\{(v\d+|voice\d+)\}[:.]?|(v\d+|voice\d+)[:.])\s*(.*)$""",
        Pattern.CASE_INSENSITIVE
    )

    private val splitWordStopWords = setOf(
        "a", "an", "and", "as", "at", "be", "but", "by", "can", "could", "did",
        "do", "does", "for", "from", "had", "has", "have", "he", "her", "him",
        "i", "if", "in", "is", "it", "me", "my", "no", "not", "of", "on", "or",
        "our", "out", "she", "so", "the", "to", "up", "we", "with", "you", "your",
        "will", "would", "am", "are", "was", "were", "been", "being", "there", "here"
    )
    
    /**
     * Normalizes voice tags to standard format (e.g., "v1", "v2", "v3")
     */
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

    /**
     * Check if lyrics contain word-level timestamps (Enhanced LRC format)
     */
    fun hasWordTimestamps(lrcContent: String): Boolean {
        return wordTimestampPattern.matcher(lrcContent).find()
    }
    
    /**
     * Extract voice tag and text from a line
     * @param text The lyrics text that may contain voice tag
     * @return Pair of (voiceTag, cleanedText) or (null, originalText)
     */
    private fun extractVoiceTag(text: String): Pair<String?, String> {
        val matcher = voiceTagInLinePattern.matcher(text.trim())
        return if (matcher.matches()) {
            val rawTag = matcher.group(1) ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: matcher.group(5) ?: ""
            val cleanedText = matcher.group(6) ?: text
            Pair(normalizeVoiceTag(rawTag), cleanedText.trim())
        } else {
            Pair(null, text)
        }
    }

    /**
     * Normalizes lyric text that has been split into fragments like "some thing" or "catch ing".
     * This keeps plain synced LRC lines readable when the source exported word fragments.
     */
    fun normalizeWordFlowText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed

        val contractionNormalized = trimmed
            .replace(Regex("\\b([A-Za-z]+)\\s+(n['’]?t)\\b"), "$1$2")
            .replace(Regex("\\b([A-Za-z]+)\\s+(['’](?:re|ve|ll|d|m|s|t))\\b"), "$1$2")

        val tokens = contractionNormalized.split(Regex("\\s+"))
        if (tokens.size < 2) return contractionNormalized

        val normalizedTokens = mutableListOf<String>()
        var fragmentRun = mutableListOf<String>()

        fun flushFragmentRun() {
            if (fragmentRun.isEmpty()) return

            val cleanedRun = fragmentRun.map { it.trim() }.filter { it.isNotEmpty() }
            val shouldCollapse = shouldCollapseFragmentRun(cleanedRun, tokens.size)

            if (shouldCollapse) {
                normalizedTokens += cleanedRun.joinToString(separator = "")
            } else {
                normalizedTokens += cleanedRun
            }

            fragmentRun = mutableListOf()
        }

        tokens.forEach { token ->
            val trimmedToken = token.trim()
            if (trimmedToken.matches(Regex("[A-Za-z']+"))) {
                fragmentRun.add(trimmedToken)
            } else {
                flushFragmentRun()
                normalizedTokens.add(trimmedToken)
            }
        }

        flushFragmentRun()

        return normalizedTokens.joinToString(separator = " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("([,.;:!?])(\\S)"), "$1 $2")
    }

    private fun shouldCollapseFragmentRun(run: List<String>, totalTokenCount: Int): Boolean {
        if (run.size < 2) return false

        val normalizedRun = run.map { it.lowercase().trim('\'', '’') }.filter { it.isNotEmpty() }
        if (normalizedRun.size < 2) return false
        if (normalizedRun.any { it in splitWordStopWords }) return false

        val hasUppercase = run.any { token -> token.any { it.isUpperCase() } && token != "I" }
        if (hasUppercase) return false

        val hasShortToken = normalizedRun.any { it.length <= 4 }
        val totalLength = normalizedRun.sumOf { it.length }

        return (hasShortToken && totalTokenCount >= 6 && totalLength <= 18) ||
            (run.size >= 3 && totalLength <= 16)
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
    
    /**
     * Separate main lyrics from translation and romanization lines
     * @param lines List of text lines (first is main lyrics, rest may be translations/romanizations)
     * @return Triple of (mainText, translation, romanization)
     */
    private fun separateTranslation(lines: List<String>): Triple<String, String?, String?> {
        if (lines.isEmpty()) return Triple("", null, null)

        val normalizedLines = lines
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (normalizedLines.isEmpty()) return Triple("", null, null)
        if (normalizedLines.size == 1) return Triple(normalizedLines[0], null, null)

        val mainIndex = normalizedLines.indices
            .maxByOrNull { mainLineScore(normalizedLines[it]) }
            ?: 0

        val mainText = normalizedLines[mainIndex]
        var translation: String? = null
        var romanization: String? = null
        
        // Process additional lines relative to selected main text
        normalizedLines.forEachIndexed { index, rawLine ->
            if (index == mainIndex) return@forEachIndexed

            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            
            // Detect translation patterns:
            // 1. Parentheses: (Translation text)
            // 2. Square brackets: [Translation text]
            // 3. Other languages (contains non-ASCII characters different from main text)
            when {
                line.startsWith("(") && line.endsWith(")") -> {
                    translation = appendSupplementalUnique(
                        translation,
                        line.substring(1, line.length - 1).trim()
                    ).ifBlank { null }
                }
                line.startsWith("[") && line.endsWith("]") -> {
                    romanization = appendSupplementalUnique(
                        romanization,
                        line.substring(1, line.length - 1).trim()
                    ).ifBlank { null }
                }
                // If main text has non-Latin script and candidate line is Latin-based (e.g. Romaji/Pinyin/Romaja),
                // it is romanization (even with CJK/full-width punctuation like 、 or 。 or accented chars)
                hasNonLatinScript(mainText) && isLatinBased(line) -> {
                    romanization = appendSupplementalUnique(romanization, line).ifBlank { null }
                }
                // If main text is Latin and this line has non-Latin, it's likely translation
                isLatinBased(mainText) && hasNonLatinScript(line) -> {
                    translation = appendSupplementalUnique(translation, line).ifBlank { null }
                }
                // If main text has non-ASCII and candidate line has no non-Latin letters, it's romanization
                mainText.any { it.code > 127 } && !hasNonLatinScript(line) && line.any { it.isLetter() } -> {
                    romanization = appendSupplementalUnique(romanization, line).ifBlank { null }
                }
                // Otherwise, treat as translation
                else -> {
                    translation = appendSupplementalUnique(translation, line).ifBlank { null }
                }
            }
        }
        
        return Triple(mainText, translation, romanization)
    }

    private fun mainLineScore(line: String): Int {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return Int.MIN_VALUE

        var score = 0

        if (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length > 2) {
            score -= 120
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            score -= 100
        }

        if (voiceTagInLinePattern.matcher(trimmed).matches()) {
            score += 20
        }

        val nonWhitespaceChars = trimmed.count { !it.isWhitespace() }
        val lettersDigits = trimmed.count { it.isLetterOrDigit() }
        val singleCharTokens = trimmed.split(Regex("\\s+")).count { it.length == 1 }

        score += nonWhitespaceChars
        score += lettersDigits * 2
        score -= singleCharTokens * 3

        return score
    }

    private fun isLikelySupplementalLine(mainText: String, candidateLine: String): Boolean {
        val trimmedCandidate = candidateLine.trim()
        if (trimmedCandidate.isEmpty()) return false

        if (trimmedCandidate.startsWith("(") && trimmedCandidate.endsWith(")") && trimmedCandidate.length > 2) {
            return true
        }

        if (trimmedCandidate.startsWith("[") && trimmedCandidate.endsWith("]") && trimmedCandidate.length > 2) {
            return true
        }

        val mainHasNonLatin = hasNonLatinScript(mainText)
        val candidateIsLatin = isLatinBased(trimmedCandidate)
        val candidateHasNonLatin = hasNonLatinScript(trimmedCandidate)
        if ((mainHasNonLatin && candidateIsLatin) || (isLatinBased(mainText) && candidateHasNonLatin)) {
            return true
        }

        val mainHasNonAscii = mainText.any { it.code > 127 }
        val candidateHasNonAscii = trimmedCandidate.any { it.code > 127 }
        return mainHasNonAscii != candidateHasNonAscii
    }

    private fun isLikelyDuplicateLine(mainText: String, candidateLine: String): Boolean {
        val mainCanonical = canonicalText(mainText)
        val candidateCanonical = canonicalText(candidateLine)
        if (mainCanonical.isEmpty() || candidateCanonical.isEmpty()) return false

        if (mainCanonical == candidateCanonical) return true

        val minLength = minOf(mainCanonical.length, candidateCanonical.length)
        return minLength >= 6 && (
            mainCanonical.contains(candidateCanonical) ||
                candidateCanonical.contains(mainCanonical)
            )
    }

    private fun pickPreferredDuplicateMain(existingMain: String, candidateMain: String): String {
        val existing = existingMain.trim()
        val candidate = candidateMain.trim()

        val existingScore = mainLineScore(existing)
        val candidateScore = mainLineScore(candidate)

        if (candidateScore > existingScore + 2) return candidate
        if (existingScore > candidateScore + 2) return existing

        val existingSpaceCount = existing.count { it.isWhitespace() }
        val candidateSpaceCount = candidate.count { it.isWhitespace() }

        return when {
            candidateSpaceCount < existingSpaceCount -> candidate
            candidateSpaceCount > existingSpaceCount -> existing
            candidate.length < existing.length -> candidate
            else -> existing
        }
    }

    private fun canonicalText(text: String): String {
        return text
            .lowercase()
            .filter { it.isLetterOrDigit() }
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
            return existing.orEmpty()
        }

        return if (existing.isNullOrBlank()) {
            incomingTrimmed
        } else {
            "$existing\n$incomingTrimmed"
        }
    }

    private fun mergeSupplemental(existing: String?, incoming: String?): String? {
        val merged = appendSupplementalUnique(existing, incoming.orEmpty())
        return merged.takeIf { it.isNotBlank() }
    }

    private fun mergeDuplicateLyricLines(lines: List<LyricLine>): List<LyricLine> {
        return lines
            .groupBy { it.timestamp to it.text }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, item ->
                    acc.copy(
                        voiceTag = acc.voiceTag ?: item.voiceTag,
                        translation = mergeSupplemental(acc.translation, item.translation),
                        romanization = mergeSupplemental(acc.romanization, item.romanization)
                    )
                }
            }
            .sortedBy { it.timestamp }
    }

    /**
     * Parse LRC format lyrics into structured lyric lines with timestamps.
     * 
     * Supports multi-line lyrics where untimestamped lines following a timestamped line
     * are combined with the timestamped line using newline separators. This enables:
     * - Translations: [00:00.74]Original text\n(Translation)
     * - Romanizations: [00:00.74]Original text\nRomanization
     * - Multi-line verses: [00:00.74]Line 1\nLine 2\nLine 3
     * 
     * Example input:
     * ```
     * [00:00.74]I've been trying to be every man you saw in me
     * (He estado intentando ser cada hombre que viste en mí)
     * [00:05.20]But in my eyes I just flicker out
     * (Pero en mis ojos solo parpadeo)
     * ```
     * 
     * Output:
     * - LyricLine(timestamp=740, text="I've been trying...\n(He estado...)")
     * - LyricLine(timestamp=5200, text="But in my eyes...\n(Pero en mis ojos...)")
     * 
     * @param lrcContent The LRC format lyrics string
     * @return List of parsed lyric lines sorted by timestamp
     */
    fun parseLyrics(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val trim = runCatching { AppSettings.getInstance(chromahub.rhythm.app.RhythmApplication.instance).trimLyrics.value }.getOrDefault(true)
        val autoWordSync = runCatching { AppSettings.getInstance(chromahub.rhythm.app.RhythmApplication.instance).translationAutoWord.value }.getOrDefault(false)
        val options = LrcUtils.LrcParserOptions(trim = trim, multiLine = true, errorText = null, autoWordSync = autoWordSync)
        val parsed = LrcUtils.parseLyrics(lrcContent, audioMimeType = null, parserOptions = options, format = LrcUtils.LyricFormat.LRC)
        
        if (parsed is SemanticLyrics.SyncedLyrics) {
            val grouped = parsed.text.groupBy { it.start }
            return grouped.map { (start, lines) ->
                val mainLine = lines.firstOrNull { !it.isTranslated } ?: lines.first()
                val mainText = mainLine.text.trim()
                
                var translation: String? = null
                var romanization: String? = null
                
                lines.forEach { lineObj ->
                    if (lineObj == mainLine) return@forEach
                    val line = lineObj.text.trim()
                    if (line.isEmpty()) return@forEach
                    
                    when {
                        line.startsWith("(") && line.endsWith(")") -> {
                            translation = appendSupplementalUnique(
                                translation,
                                line.substring(1, line.length - 1).trim()
                            ).ifBlank { null }
                        }
                        line.startsWith("[") && line.endsWith("]") -> {
                            romanization = appendSupplementalUnique(
                                romanization,
                                line.substring(1, line.length - 1).trim()
                            ).ifBlank { null }
                        }
                        // If main text has non-Latin script and candidate line is Latin-based (e.g. Romaji/Pinyin/Romaja),
                        // it is romanization (even with CJK/full-width punctuation like 、 or 。 or accented chars)
                        hasNonLatinScript(mainText) && isLatinBased(line) -> {
                            romanization = appendSupplementalUnique(romanization, line).ifBlank { null }
                        }
                        // If main text is Latin and this line has non-Latin, it's likely translation
                        isLatinBased(mainText) && hasNonLatinScript(line) -> {
                            translation = appendSupplementalUnique(translation, line).ifBlank { null }
                        }
                        // If main text has non-ASCII and candidate line has no non-Latin letters, it's romanization
                        mainText.any { it.code > 127 } && !hasNonLatinScript(line) && line.any { it.isLetter() } -> {
                            romanization = appendSupplementalUnique(romanization, line).ifBlank { null }
                        }
                        // Otherwise, treat as translation
                        else -> {
                            translation = appendSupplementalUnique(translation, line).ifBlank { null }
                        }
                    }
                }
                
                val (voiceTag, cleanedText) = extractVoiceTag(mainText)
                val resolvedVoiceTag = voiceTag ?: when {
                    mainLine.speaker?.isVoice2 == true -> "v2"
                    mainLine.speaker?.isGroup == true -> "v3"
                    mainLine.speaker?.isWidthLimited == true -> "v1"
                    else -> normalizeVoiceTag(mainLine.speaker?.name)
                }
                LyricLine(
                    timestamp = start.toLong(),
                    text = cleanedText,
                    voiceTag = resolvedVoiceTag,
                    translation = translation,
                    romanization = romanization,
                    endTime = if (mainLine.end > 0uL && !mainLine.endIsImplicit) mainLine.end.toLong() else null
                )
            }.sortedBy { it.timestamp }
        }
        return emptyList()
    }
    
    /**
     * Validates if the provided content contains valid LRC format timestamps
     */
    fun isValidLrcFormat(content: String): Boolean {
        if (content.isBlank()) return false
        
        val lines = content.trim().split("\n")
        var timestampCount = 0
        
        for (line in lines) {
            if (timestampPattern.matcher(line.trim()).find()) {
                timestampCount++
                if (timestampCount >= 2) return true // At least 2 timestamped lines
            }
        }
        
        return false
    }
    
    /**
     * Parses Enhanced LRC format with word-level timestamps into structured format
     * Enhanced LRC format example: [00:12.00]Hello <00:12.50>world <00:13.00>of <00:13.50>lyrics
     * 
     * @param lrcContent Enhanced LRC content with word-level timestamps
     * @return List of lines with word-level timing, or empty if parsing fails
     */
    fun parseEnhancedLRC(lrcContent: String): List<EnhancedLyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val trim = runCatching { AppSettings.getInstance(chromahub.rhythm.app.RhythmApplication.instance).trimLyrics.value }.getOrDefault(true)
        val autoWordSync = runCatching { AppSettings.getInstance(chromahub.rhythm.app.RhythmApplication.instance).translationAutoWord.value }.getOrDefault(false)
        val options = LrcUtils.LrcParserOptions(trim = trim, multiLine = true, errorText = null, autoWordSync = autoWordSync)
        val parsed = LrcUtils.parseLyrics(lrcContent, audioMimeType = null, parserOptions = options, format = LrcUtils.LyricFormat.LRC)
        
        if (parsed is SemanticLyrics.SyncedLyrics) {
            val grouped = parsed.text.groupBy { it.start }
            return grouped.map { (start, lines) ->
                val mainLine = lines.firstOrNull { !it.isTranslated } ?: lines.first()
                val mainText = mainLine.text.trim()
                
                var translation: String? = null
                var romanization: String? = null
                
                lines.forEach { lineObj ->
                    if (lineObj == mainLine) return@forEach
                    val line = lineObj.text.trim()
                    if (line.isEmpty()) return@forEach
                    
                    when {
                        line.startsWith("(") && line.endsWith(")") -> {
                            translation = appendSupplementalUnique(
                                translation,
                                line.substring(1, line.length - 1).trim()
                            ).ifBlank { null }
                        }
                        line.startsWith("[") && line.endsWith("]") -> {
                            romanization = appendSupplementalUnique(
                                romanization,
                                line.substring(1, line.length - 1).trim()
                            ).ifBlank { null }
                        }
                        // If main text has non-Latin script and candidate line is Latin-based (e.g. Romaji/Pinyin/Romaja),
                        // it is romanization (even with CJK/full-width punctuation like 、 or 。 or accented chars)
                        hasNonLatinScript(mainText) && isLatinBased(line) -> {
                            romanization = appendSupplementalUnique(romanization, line).ifBlank { null }
                        }
                        // If main text is Latin and this line has non-Latin, it's likely translation
                        isLatinBased(mainText) && hasNonLatinScript(line) -> {
                            translation = appendSupplementalUnique(translation, line).ifBlank { null }
                        }
                        // If main text has non-ASCII and candidate line has no non-Latin letters, it's romanization
                        mainText.any { it.code > 127 } && !hasNonLatinScript(line) && line.any { it.isLetter() } -> {
                            romanization = appendSupplementalUnique(romanization, line).ifBlank { null }
                        }
                        // Otherwise, treat as translation
                        else -> {
                            translation = appendSupplementalUnique(translation, line).ifBlank { null }
                        }
                    }
                }
                
                val mlWords = mainLine.words
                val enhancedWords = mlWords?.mapIndexed { idx, word ->
                    val rawText = mainLine.text.substring(word.charRange)
                    val trimmedText = rawText.trim()
                    
                    val leadingSpaces = rawText.takeWhile { it.isWhitespace() }.length
                    val trailingSpaces = rawText.takeLastWhile { it.isWhitespace() }.length
                    val trimmedStart = word.charRange.first + leadingSpaces
                    val trimmedEnd = word.charRange.last - trailingSpaces

                    val isPart = if (idx > 0 && trimmedText.isNotEmpty()) {
                        val prevWord = mlWords[idx - 1]
                        val prevRawText = mainLine.text.substring(prevWord.charRange)
                        val prevTrimmedText = prevRawText.trim()
                        if (prevTrimmedText.isNotEmpty()) {
                            val prevTrailingSpaces = prevRawText.takeLastWhile { it.isWhitespace() }.length
                            val prevTrimmedEnd = prevWord.charRange.last - prevTrailingSpaces
                            
                            val gap = mainLine.text.substring(prevTrimmedEnd + 1, trimmedStart)
                            gap.isEmpty()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                    EnhancedWord(
                        text = trimmedText,
                        timestamp = word.begin.toLong(),
                        endtime = (word.endInclusive ?: word.begin).toLong(),
                        isPart = isPart
                    )
                } ?: listOf(
                    EnhancedWord(
                        text = mainLine.text,
                        timestamp = mainLine.start.toLong(),
                        endtime = mainLine.end.toLong(),
                        isPart = false
                    )
                )
                EnhancedLyricLine(
                    words = enhancedWords,
                    lineTimestamp = mainLine.start.toLong(),
                    lineEndtime = mainLine.end.toLong(),
                    translation = translation,
                    romanization = romanization
                )
            }.sortedBy { it.lineTimestamp }
        }
        return emptyList()
    }
    
    // TODO: Implement syllable-level timestamp parsing
    // Enhanced LRC can support syllable breakdowns using hyphenated words with individual timestamps
    // Example: [00:12.00]Hel<00:12.20>lo <00:12.50>wo<00:12.70>rld
    // This would require parsing word parts separated by syllable boundaries
    /**
     * Parse syllable-level timestamps (future enhancement)
     * @param lrcContent Enhanced LRC with syllable-level timing
     * @return List of lines with syllable-level timing
     */
    fun parseSyllableLRC(lrcContent: String): List<EnhancedLyricLine> {
        // TODO: Implement syllable parsing
        // For now, fall back to word-level parsing
        return parseEnhancedLRC(lrcContent)
    }
    
    // TODO: Implement Enhanced LRC export
    /**
     * Convert word-by-word lyrics back to Enhanced LRC format (future enhancement)
     * @param lines List of enhanced lyric lines
     * @return Enhanced LRC formatted string
     */
    fun toEnhancedLRC(lines: List<EnhancedLyricLine>): String {
        // TODO: Implement export to Enhanced LRC format
        return lines.joinToString("\n") { line ->
            val timestamp = formatLRCTimestamp(line.lineTimestamp)
            val words = line.words.joinToString("") { word ->
                val wordTimestamp = formatLRCTimestamp(word.timestamp)
                " <$wordTimestamp>${word.text}"
            }.trimStart()
            "[$timestamp]$words"
        }
    }
    
    private fun formatLRCTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = (milliseconds % 1000) / 10
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, millis)
    }
}

data class LyricLine(
    val timestamp: Long,
    val text: String,
    val voiceTag: String? = null, // Voice tag (v1, v2, v3, etc.) for multi-voice lyrics
    val translation: String? = null, // Translation text (if present)
    val romanization: String? = null, // Romanization text (if present)
    val endTime: Long? = null
)

/**
 * Represents a lyric line with word-level timing (Enhanced LRC format)
 */
data class EnhancedLyricLine(
    val words: List<EnhancedWord>,
    val lineTimestamp: Long,
    val lineEndtime: Long,
    val translation: String? = null,
    val romanization: String? = null
)

/**
 * Represents a word with timing in Enhanced LRC format
 * TODO: Add syllable support - break words into syllable parts with individual timing
 */
data class EnhancedWord(
    val text: String,
    val timestamp: Long, // start time in milliseconds
    val endtime: Long, // end time in milliseconds
    val isPart: Boolean = false,
    val syllables: List<Syllable>? = null // TODO: Future syllable-level timing
)

/**
 * Represents a syllable within a word (future enhancement)
 * TODO: Implement syllable parsing and display
 */
data class Syllable(
    val text: String,
    val timestamp: Long,
    val endtime: Long
)
