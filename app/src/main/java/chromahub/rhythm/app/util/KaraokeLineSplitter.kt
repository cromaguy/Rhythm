/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import kotlin.math.pow

/**
 * Knuth-Plass-lite balanced line wrapping and syllable sweep math shared between
 * Rhythm's draw-phase Canvas karaoke renderer and JVM unit tests.
 */
object KaraokeLineSplitter {

    /**
     * Partition [widths] into contiguous ranges each fitting [maxWidth], minimizing
     * the sum of squared leftover widths so wrapped lyric lines stay visually balanced.
     * Units wider than [maxWidth] fall back to greedy grouping rather than failing.
     */
    fun packBalanced(widths: List<Float>, maxWidth: Float): List<IntRange> {
        if (widths.isEmpty()) return emptyList()
        if (maxWidth <= 0f) return listOf(0 until widths.size)
        val n = widths.size
        val costs = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
        val breaks = IntArray(n + 1)
        costs[0] = 0.0

        for (i in 1..n) {
            var rowWidth = 0f
            for (j in i downTo 1) {
                rowWidth += widths[j - 1]
                if (rowWidth > maxWidth) break
                val slack = (maxWidth - rowWidth).toDouble()
                val badness = slack * slack
                if (costs[j - 1] + badness < costs[i]) {
                    costs[i] = costs[j - 1] + badness
                    breaks[i] = j - 1
                }
            }
        }

        if (costs[n].isInfinite()) {
            val greedy = mutableListOf<IntRange>()
            var start = 0
            var rowWidth = 0f
            widths.forEachIndexed { index, unitWidth ->
                if (rowWidth > 0f && rowWidth + unitWidth > maxWidth) {
                    greedy.add(start until index)
                    start = index
                    rowWidth = 0f
                }
                rowWidth += unitWidth
            }
            greedy.add(start until n)
            return greedy
        }

        val rows = ArrayDeque<IntRange>()
        var index = n
        while (index > 0) {
            val start = breaks[index]
            rows.addFirst(start until index)
            index = start
        }
        return rows.toList()
    }

    /**
     * Build display parts and [IntRange] character offsets for [words] matching
     * Rhythm's separator convention (`isPart` omits leading spaces).
     */
    fun buildRenderedTextAndRanges(words: List<WordByWordWord>): Pair<String, List<IntRange>> {
        if (words.isEmpty()) return "" to emptyList()
        val sb = StringBuilder()
        val ranges = ArrayList<IntRange>(words.size)
        words.forEachIndexed { index, word ->
            if (index > 0 && !word.isPart) {
                sb.append(' ')
            }
            val start = sb.length
            sb.append(word.text)
            ranges.add(start until sb.length)
        }
        return sb.toString() to ranges
    }

    /**
     * Computes eased fractional character fill index and currently active syllable progress
     * at [positionMs] for draw-phase Canvas masking with zero composition churn.
     *
     * @return Pair(activeWordIndex, easedWordProgress in 0f..1f), or (-1, 0f) before line start.
     */
    fun computeWordSweepState(
        words: List<WordByWordWord>,
        lineEndMs: Long,
        positionMs: Long
    ): Pair<Int, Float> {
        if (words.isEmpty() || positionMs < words.first().timestamp) return -1 to 0f

        var activeIdx = -1
        var activeProgress = 0f
        for (i in words.indices) {
            val w = words[i]
            if (positionMs < w.timestamp) break
            val durMs = computeEffectiveWordDurationMs(words, i, lineEndMs)
            val linear = ((positionMs - w.timestamp).toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
            // Fast vocal attack, smooth sustain release curve.
            val eased = 1f - (1f - linear).toDouble().pow(1.4).toFloat()
            activeIdx = i
            activeProgress = eased
            if (eased < 1f) break
        }
        return activeIdx to activeProgress
    }

    /**
     * Smooth normalized upward lift (0f..1f) while a syllable is being sung, decaying softly
     * right after the word ends.
     */
    fun computeSyllableLiftFraction(
        words: List<WordByWordWord>,
        index: Int,
        lineEndMs: Long,
        positionMs: Long
    ): Float {
        val w = words.getOrNull(index) ?: return 0f
        if (positionMs < w.timestamp) return 0f
        val durMs = computeEffectiveWordDurationMs(words, index, lineEndMs)
        val endMs = w.timestamp + durMs
        if (positionMs <= endMs) {
            val p = ((positionMs - w.timestamp).toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
            return kotlin.math.sin(p * Math.PI).toFloat().coerceIn(0f, 1f) * 0.45f + p * 0.55f
        }
        val decayMs = 220f
        val elapsed = (positionMs - endMs).toFloat()
        return (1f - (elapsed / decayMs)).coerceIn(0f, 1f) * 0.55f
    }

    private fun computeEffectiveWordDurationMs(
        words: List<WordByWordWord>,
        index: Int,
        lineEndMs: Long
    ): Long {
        val w = words[index]
        val raw = w.endtime - w.timestamp
        if (raw > 0L) return raw
        val nextStart = words.getOrNull(index + 1)?.timestamp
        if (nextStart != null && nextStart > w.timestamp) {
            return (nextStart - w.timestamp).coerceIn(120L, 1500L)
        }
        if (lineEndMs > w.timestamp) {
            return (lineEndMs - w.timestamp).coerceIn(150L, 1000L)
        }
        return 350L
    }
}
