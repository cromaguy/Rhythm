/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database

/**
 * Builds safe SQLite FTS4 prefix queries (`token1* token2*`) from arbitrary user search strings
 * without throwing FTS MATCH syntax errors on punctuation, quotes, operators, or symbols.
 */
object FtsQuerySanitizer {
    private val TOKEN_REGEX = Regex("[\\p{L}\\p{N}_]+")

    fun buildPrefixMatchQuery(rawQuery: String): String {
        val tokens = TOKEN_REGEX.findAll(rawQuery)
            .map { it.value.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .toList()
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }
}
