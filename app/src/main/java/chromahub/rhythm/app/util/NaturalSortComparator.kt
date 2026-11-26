/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import java.util.Collections

object NaturalSortComparator {

    private val splitRegex = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
    private val leadingArticleRegex = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)

    private val tokenCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<String>>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean {
                return size > 256
            }
        }
    )

    fun compare(
        s1: String,
        s2: String,
        ignoreCase: Boolean = true,
        stripLeadingArticles: Boolean = false
    ): Int {
        val left = if (stripLeadingArticles) leadingArticleRegex.replaceFirst(s1.trim(), "") else s1
        val right = if (stripLeadingArticles) leadingArticleRegex.replaceFirst(s2.trim(), "") else s2
        val parts1 = tokenize(left)
        val parts2 = tokenize(right)
        val minLen = minOf(parts1.size, parts2.size)

        for (i in 0 until minLen) {
            val token1 = parts1[i]
            val token2 = parts2[i]
            val num1 = token1.toLongOrNull()
            val num2 = token2.toLongOrNull()
            val cmp = if (num1 != null && num2 != null) {
                val numCmp = num1.compareTo(num2)
                if (numCmp != 0) numCmp else token1.length.compareTo(token2.length)
            } else {
                if (ignoreCase) token1.compareTo(token2, ignoreCase = true)
                else token1.compareTo(token2)
            }
            if (cmp != 0) return cmp
        }
        return parts1.size.compareTo(parts2.size)
    }

    fun <T> comparator(
        ignoreCase: Boolean = true,
        stripLeadingArticles: Boolean = false,
        extract: (T) -> String
    ): Comparator<T> = Comparator { a, b ->
        compare(extract(a), extract(b), ignoreCase, stripLeadingArticles)
    }

    private fun tokenize(s: String): List<String> {
        tokenCache[s]?.let { return it }
        val tokens = s.split(splitRegex).filter { it.isNotBlank() }
        tokenCache[s] = tokens
        return tokens
    }
}

typealias AlphaNumericComparator = NaturalSortComparator
