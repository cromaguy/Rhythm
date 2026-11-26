/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

data class SettingsSearchMatch(
    val item: SearchableSettingItem,
    val score: Int,
    val titleMatchedRanges: List<IntRange> = emptyList(),
    val descriptionMatchedRanges: List<IntRange> = emptyList()
)

/**
 * Weighted subsequence & multi-term fuzzy matcher for Rhythm Settings Search 2.0.
 * Handles exact hits, prefix hits, word-boundary substrings, multi-word queries,
 * and typo-tolerant ordered character subsequences ("eqlz" -> "Equalizer", "usbdac" -> "USB Exclusive").
 */
object SettingsFuzzyMatcher {

    private const val MIN_SCORE_THRESHOLD = 20
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun search(query: String, index: List<SearchableSettingItem>): List<SettingsSearchMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val lowerQuery = trimmed.lowercase(Locale.ROOT)
        val queryWords = lowerQuery.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val results = ArrayList<SettingsSearchMatch>(index.size)

        for (item in index) {
            val titleLower = item.title.lowercase(Locale.ROOT)
            val descLower = item.description.lowercase(Locale.ROOT)
            var score = 0
            val titleRanges = mutableListOf<IntRange>()
            val descRanges = mutableListOf<IntRange>()

            when {
                titleLower == lowerQuery -> {
                    score += 150
                    titleRanges.add(0 until item.title.length)
                }
                titleLower.startsWith(lowerQuery) -> {
                    score += 110
                    titleRanges.add(0 until lowerQuery.length)
                }
                titleLower.contains(lowerQuery) -> {
                    val idx = titleLower.indexOf(lowerQuery)
                    val isWordBoundary = idx == 0 || !titleLower[idx - 1].isLetterOrDigit()
                    score += if (isWordBoundary) 95 else 80
                    titleRanges.add(idx until (idx + lowerQuery.length))
                }
                else -> {
                    val fuzzy = fuzzySubsequenceScore(lowerQuery, titleLower, titleRanges)
                    if (fuzzy > 0) score += (fuzzy * 0.9f).toInt()
                }
            }

            var bestKeywordScore = 0
            for (keyword in item.keywords) {
                val kwLower = keyword.lowercase(Locale.ROOT)
                val kwScore = when {
                    kwLower == lowerQuery -> 85
                    kwLower.startsWith(lowerQuery) -> 70
                    kwLower.contains(lowerQuery) -> {
                        val idx = kwLower.indexOf(lowerQuery)
                        val isWordBoundary = idx == 0 || !kwLower[idx - 1].isLetterOrDigit()
                        if (isWordBoundary) 60 else 45
                    }
                    else -> {
                        val sub = fuzzySubsequenceScore(lowerQuery, kwLower, null)
                        if (sub > 0) (sub * 0.5f).toInt() else 0
                    }
                }
                if (kwScore > bestKeywordScore) bestKeywordScore = kwScore
            }
            score += bestKeywordScore

            when {
                descLower.contains(lowerQuery) -> {
                    val idx = descLower.indexOf(lowerQuery)
                    score += 36
                    descRanges.add(idx until (idx + lowerQuery.length))
                }
                lowerQuery.length >= 3 -> {
                    val descFuzzy = fuzzySubsequenceScore(lowerQuery, descLower, descRanges)
                    if (descFuzzy > 0) score += (descFuzzy * 0.4f).toInt()
                }
            }

            if (item.parentScreen.lowercase(Locale.ROOT).contains(lowerQuery)) {
                score += 22
            }

            if (queryWords.size > 1) {
                var matchedWords = 0
                val allText = "$titleLower $descLower ${item.keywords.joinToString(" ").lowercase(Locale.ROOT)} ${item.parentScreen.lowercase(Locale.ROOT)}"
                for (word in queryWords) {
                    if (allText.contains(word)) {
                        matchedWords++
                        val tIdx = titleLower.indexOf(word)
                        if (tIdx >= 0) titleRanges.add(tIdx until (tIdx + word.length))
                        val dIdx = descLower.indexOf(word)
                        if (dIdx >= 0) descRanges.add(dIdx until (dIdx + word.length))
                    }
                }
                if (matchedWords == queryWords.size) score += 55
                else if (matchedWords > 0) score += matchedWords * 16
            }

            if (score >= MIN_SCORE_THRESHOLD) {
                results.add(
                    SettingsSearchMatch(
                        item = item,
                        score = score,
                        titleMatchedRanges = mergeRanges(titleRanges),
                        descriptionMatchedRanges = mergeRanges(descRanges)
                    )
                )
            }
        }

        return results.sortedWith(
            compareByDescending<SettingsSearchMatch> { it.score }
                .thenBy { it.item.title.lowercase(Locale.ROOT) }
        )
    }

    internal fun fuzzySubsequenceScore(
        query: String,
        target: String,
        outRanges: MutableList<IntRange>?
    ): Int {
        if (query.isEmpty() || target.isEmpty() || query.length > target.length) return 0
        var qIdx = 0
        var tIdx = 0
        var score = 0
        var consecutive = 0
        var runStart = -1

        while (qIdx < query.length && tIdx < target.length) {
            if (query[qIdx] == target[tIdx]) {
                if (runStart == -1) runStart = tIdx
                consecutive++
                score += 6 + (consecutive * 4)
                if (tIdx == 0 || !target[tIdx - 1].isLetterOrDigit()) score += 8
                qIdx++
            } else if (runStart != -1) {
                outRanges?.add(runStart until tIdx)
                runStart = -1
                consecutive = 0
            }
            tIdx++
        }
        if (runStart != -1) outRanges?.add(runStart until tIdx)
        if (qIdx < query.length) {
            outRanges?.clear()
            return 0
        }
        val spreadPenalty = (tIdx - query.length).coerceAtMost(24)
        return (score - spreadPenalty).coerceAtLeast(1)
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size <= 1) return ranges
        val sorted = ranges.sortedBy { it.first }
        val merged = ArrayList<IntRange>(sorted.size)
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            current = if (next.first <= current.last + 1) {
                current.first..maxOf(current.last, next.last)
            } else {
                merged.add(current)
                next
            }
        }
        merged.add(current)
        return merged
    }

    fun highlightMatchedText(
        text: String,
        ranges: List<IntRange>,
        highlightColor: Color
    ): AnnotatedString {
        if (ranges.isEmpty()) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                if (end > start) {
                    addStyle(
                        SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
}
