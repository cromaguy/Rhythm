/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility object for parsing multiple artists from a single artist string.
 * 
 * Supports configurable delimiters (e.g., ;, /, ,, +, &, custom words like 'feat.', 'ft.')
 * and backslash escape sequences.
 * 
 * Example usage:
 * - "Artist1; Artist2" -> ["Artist1", "Artist2"]
 * - "Artist1/Artist2" -> ["Artist1", "Artist2"]
 * - "AC\\/DC" -> ["AC/DC"] (escaped slash)
 * - "Artist1 feat. Artist2" -> ["Artist1", "Artist2"] (when feat. delimiter enabled)
 */
object ArtistSeparator {
    private const val TAG = "ArtistSeparator"
    const val DEFAULT_DELIMITERS = ";/"
    private const val ESCAPE_CHAR = '\\'
    private const val PLACEHOLDER_PREFIX = "\u0000\u0001"
    private const val PLACEHOLDER_SUFFIX = '\u0002'

    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Parses a raw delimiters string (single characters, JSON array, or newline/pipe delimited)
     * into a list of individual delimiter strings.
     */
    fun parseDelimiters(delimiters: String?): List<String> {
        if (delimiters.isNullOrBlank()) {
            return listOf(";", "/")
        }

        val trimmed = delimiters.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val fromJson = runCatching {
                GsonUtils.gson.fromJson(trimmed, Array<String>::class.java)?.toList()
            }.getOrNull()
            if (!fromJson.isNullOrEmpty()) {
                return fromJson.filter { it.isNotEmpty() }.distinct()
            }
        }

        if (trimmed.contains('\n') || trimmed.contains('|')) {
            val tokens = trimmed.split(Regex("[\n|]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (tokens.isNotEmpty()) {
                return tokens
            }
        }

        // Legacy / compact representation: each character is a delimiter
        return trimmed.map { it.toString() }.distinct()
    }

    /**
     * Serializes a list of delimiter tokens into a string for storage in AppSettings.
     */
    fun serializeDelimiters(delimiters: List<String>): String {
        val clean = delimiters.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return DEFAULT_DELIMITERS
        
        val isSimpleCharsOnly = clean.all { it.length == 1 && !it[0].isWhitespace() && it != "[" && it != "]" }
        return if (isSimpleCharsOnly) {
            clean.joinToString("")
        } else {
            GsonUtils.gson.toJson(clean)
        }
    }

    /**
     * Cleans up unbalanced parentheses, square brackets, and curly braces that often
     * wrap collaboration phrases (e.g. "(ft. Artist B)" or "[feat. Artist B]"),
     * which otherwise leave a trailing "(" on the preceding artist and a trailing ")" on the featured artist.
     * Preserves balanced brackets within an artist's name (e.g. "Band (UK)", "(Hed) P.E.").
     */
    fun cleanArtistSegment(segment: String): String {
        var s = segment.trim()

        // 1. Remove unbalanced trailing opening brackets (e.g., "Artist (", "Artist [", "Artist {")
        while (s.isNotEmpty() && (s.endsWith('(') || s.endsWith('[') || s.endsWith('{'))) {
            s = s.dropLast(1).trim()
        }

        // 2. Remove unbalanced leading closing brackets (e.g., ") Artist", "] Artist", "} Artist")
        while (s.isNotEmpty() && (s.startsWith(')') || s.startsWith(']') || s.startsWith('}'))) {
            s = s.drop(1).trim()
        }

        // 3. Remove unbalanced trailing closing brackets (e.g., "Artist)" or "Artist]")
        // Only remove if there are more closing brackets than opening brackets in this segment
        while (s.isNotEmpty() && s.endsWith(')') && s.count { it == ')' } > s.count { it == '(' }) {
            s = s.dropLast(1).trim()
        }
        while (s.isNotEmpty() && s.endsWith(']') && s.count { it == ']' } > s.count { it == '[' }) {
            s = s.dropLast(1).trim()
        }
        while (s.isNotEmpty() && s.endsWith('}') && s.count { it == '}' } > s.count { it == '{' }) {
            s = s.dropLast(1).trim()
        }

        // 4. Remove unbalanced leading opening brackets (e.g., "(Artist" when the whole artist was enclosed)
        while (s.isNotEmpty() && s.startsWith('(') && s.count { it == '(' } > s.count { it == ')' }) {
            s = s.drop(1).trim()
        }
        while (s.isNotEmpty() && s.startsWith('[') && s.count { it == '[' } > s.count { it == ']' }) {
            s = s.drop(1).trim()
        }
        while (s.isNotEmpty() && s.startsWith('{') && s.count { it == '{' } > s.count { it == '}' }) {
            s = s.drop(1).trim()
        }

        return s
    }

    private fun getOrCreateRegex(tokens: List<String>): Regex {
        val key = tokens.sorted().joinToString("|||")
        return regexCache.getOrPut(key) {
            val sorted = tokens.sortedByDescending { it.length }
            val patternString = sorted.joinToString("|") { token ->
                val escaped = Regex.escape(token)
                val startsWithWordChar = token.firstOrNull()?.isLetterOrDigit() == true
                val endsWithWordChar = token.lastOrNull()?.isLetterOrDigit() == true
                val prefix = if (startsWithWordChar) "(?<!\\w)" else ""
                val suffix = if (endsWithWordChar) "(?!\\w)" else ""
                "$prefix$escaped$suffix"
            }
            patternString.toRegex(RegexOption.IGNORE_CASE)
        }
    }

    /**
     * Splits artist names using a pre-parsed list of delimiter tokens.
     * Prevents re-parsing overhead and ensures multi-character tokens like "ft."
     * are not mangled by string serialization.
     */
    fun splitArtistNames(
        artistName: String?,
        tokens: List<String>,
        enabled: Boolean = true
    ): List<String> {
        if (artistName.isNullOrBlank()) {
            return emptyList()
        }

        if (!enabled || tokens.isEmpty()) {
            return listOf(artistName.trim()).filter { it.isNotBlank() }
        }

        val cleanTokens = tokens.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTokens.isEmpty()) {
            return listOf(artistName.trim()).filter { it.isNotBlank() }
        }

        // Protect escaped "\<delim>" occurrences so they are never treated as split points
        val escapedTokens = mutableListOf<String>()
        val escaped = StringBuilder(artistName.length)
        val sortedTokens = cleanTokens.sortedByDescending { it.length }
        var i = 0

        while (i < artistName.length) {
            val c = artistName[i]
            if (c == ESCAPE_CHAR && i + 1 < artistName.length) {
                val remaining = artistName.substring(i + 1)
                val matchedToken = sortedTokens.firstOrNull { remaining.startsWith(it, ignoreCase = true) }
                if (matchedToken != null) {
                    val originalSlice = artistName.substring(i + 1, i + 1 + matchedToken.length)
                    escapedTokens.add(originalSlice)
                    escaped.append(PLACEHOLDER_PREFIX)
                    escaped.append((escapedTokens.size - 1).toChar())
                    escaped.append(PLACEHOLDER_SUFFIX)
                    i += 1 + matchedToken.length
                    continue
                }
            }
            escaped.append(c)
            i++
        }

        val regex = getOrCreateRegex(sortedTokens)
        return regex.split(escaped.toString())
            .map { segment ->
                var restored = segment
                for ((index, originalToken) in escapedTokens.withIndex()) {
                    val tokenPlaceholder = PLACEHOLDER_PREFIX + index.toChar() + PLACEHOLDER_SUFFIX
                    restored = restored.replace(tokenPlaceholder, originalToken)
                }
                cleanArtistSegment(restored)
            }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Splits artist names using configurable delimiters with escape sequence support
     * and high-performance precompiled Regex caching.
     */
    fun splitArtistNames(
        artistName: String?,
        delimiters: String = DEFAULT_DELIMITERS,
        enabled: Boolean = true
    ): List<String> {
        if (artistName.isNullOrBlank()) {
            return emptyList()
        }

        if (!enabled || delimiters.isEmpty()) {
            return listOf(artistName.trim()).filter { it.isNotBlank() }
        }

        val tokens = parseDelimiters(delimiters)
        return splitArtistNames(artistName, tokens, enabled)
    }

    /**
     * Split an artist string into multiple artists using the provided delimiters.
     * Delegates to [splitArtistNames] for unified behavior.
     */
    fun splitArtists(
        artistString: String?,
        delimiters: String = DEFAULT_DELIMITERS,
        enabled: Boolean = true
    ): List<String> {
        return splitArtistNames(artistString, delimiters, enabled)
    }

    /**
     * Split an artist string into multiple artists using pre-parsed delimiter tokens.
     */
    fun splitArtists(
        artistString: String?,
        tokens: List<String>,
        enabled: Boolean = true
    ): List<String> {
        return splitArtistNames(artistString, tokens, enabled)
    }
    
    /**
     * Get the primary (first) artist from a split artist string.
     * This is useful for display purposes when you need a single artist name.
     */
    fun getPrimaryArtist(
        artistString: String?,
        delimiters: String = DEFAULT_DELIMITERS,
        enabled: Boolean = true
    ): String {
        val artists = splitArtistNames(artistString, delimiters, enabled)
        return artists.firstOrNull() ?: artistString?.trim() ?: "Unknown Artist"
    }

    /**
     * Get the primary (first) artist from a split artist string using pre-parsed tokens.
     */
    fun getPrimaryArtist(
        artistString: String?,
        tokens: List<String>,
        enabled: Boolean = true
    ): String {
        val artists = splitArtistNames(artistString, tokens, enabled)
        return artists.firstOrNull() ?: artistString?.trim() ?: "Unknown Artist"
    }
    
    /**
     * Format multiple artists for display.
     */
    fun formatArtists(
        artists: List<String>,
        separator: String = ", ",
        maxArtists: Int = 3
    ): String {
        if (artists.isEmpty()) return "Unknown Artist"
        if (artists.size == 1) return artists[0]
        
        return if (artists.size <= maxArtists) {
            artists.joinToString(separator)
        } else {
            val visible = artists.take(maxArtists)
            val remaining = artists.size - maxArtists
            "${visible.joinToString(separator)} & $remaining more"
        }
    }
    
    /**
     * Escape delimiters in an artist name to prevent splitting.
     */
    fun escapeArtistName(artistName: String, delimiters: String = DEFAULT_DELIMITERS): String {
        val tokens = parseDelimiters(delimiters).sortedByDescending { it.length }
        var result = artistName
        for (token in tokens) {
            result = result.replace(token, "$ESCAPE_CHAR$token")
        }
        return result
    }
}
