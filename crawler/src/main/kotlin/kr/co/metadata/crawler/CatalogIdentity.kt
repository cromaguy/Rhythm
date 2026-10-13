// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import java.text.Normalizer

object CatalogIdentity {
    /** Duration is corroborating evidence, never a replacement for title and artist identity. */
    fun durationMatches(expectedMs: Long, candidateMs: Long?, tolerancePercent: Double = 3.0): Boolean {
        require(tolerancePercent.isFinite() && tolerancePercent in 0.0..30.0)
        if (expectedMs <= 0) return true
        if (candidateMs == null || candidateMs <= 0) return false
        val tolerance = (expectedMs * tolerancePercent / 100.0).toLong().coerceAtLeast(1_000L)
        return kotlin.math.abs(expectedMs - candidateMs) <= tolerance
    }
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase().filter(Char::isLetterOrDigit)
    fun missing(value: String?): Boolean = value.isNullOrBlank() || value.trim().lowercase() in setOf(
        "unknown", "<unknown>", "unknown title", "unknown artist", "unknown album", "unknown genre", "알 수 없음",
    )
    private fun aliases(value: String): Set<String> = (value.split(Regex("[,;·]")) +
        Regex("\\(([^)]+)\\)").findAll(value).map { it.groupValues[1] }.toList() +
        value.replace(Regex("\\([^)]*\\)"), "")).map(::normalize).filter(String::isNotBlank).toSet()
    fun artistMatches(expected: String, candidate: String): Boolean =
        !missing(expected) && aliases(expected).intersect(aliases(candidate)).isNotEmpty()
    fun titleMatches(expected: String, candidate: String): Boolean =
        !missing(expected) && normalize(expected) == normalize(candidate)
    fun select(hits: List<SearchHit>, name: String, artist: String?): SearchHit? {
        val matching = hits.filter { hit ->
            if (hit.kind == EntityKind.ARTIST) artistMatches(name, hit.title)
            else titleMatches(name, hit.title) && (hit.artists == null || artist == null || artistMatches(artist, hit.artists))
        }.distinctBy { it.providerId }
        return matching.singleOrNull() // Ambiguous matches need a human; never import the first search result blindly.
    }
}
