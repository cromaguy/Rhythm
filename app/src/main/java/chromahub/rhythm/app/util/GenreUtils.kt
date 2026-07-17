package chromahub.rhythm.app.util

/**
 * Utility methods for parsing and matching multi-genre metadata.
 *
 * Supports comma, semicolon, and newline-separated values, e.g.:
 * - "Mahur, Shur"
 * - "Mahur;Shur"
 * - "Mahur\nShur"
 */
object GenreUtils {
    private val genreSplitRegex = Regex("[,;\\n\\r]+")

    data class Summary(
        val name: String,
        val songCount: Int
    )

    fun splitGenres(rawGenre: String?): List<String> {
        if (rawGenre.isNullOrBlank()) return emptyList()

        // Some tags use NUL separators; normalize them to newline before splitting.
        val normalized = rawGenre.replace('\u0000', '\n')
        val seen = LinkedHashSet<String>()
        val genres = mutableListOf<String>()

        normalized
            .split(genreSplitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) && it != "-" }
            .forEach { genre ->
                val key = genre.lowercase()
                if (seen.add(key)) {
                    genres.add(genre)
                }
            }

        return genres
    }

    fun matchesGenre(rawGenre: String?, genre: String): Boolean {
        val target = genre.trim()
        if (target.isEmpty()) return false
        return splitGenres(rawGenre).any { it.equals(target, ignoreCase = true) }
    }

    fun matchesGenreQuery(rawGenre: String?, query: String): Boolean {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return false

        return splitGenres(rawGenre).any { genre ->
            val normalizedGenre = genre.lowercase()
            normalizedGenre == normalizedQuery ||
                normalizedGenre.startsWith(normalizedQuery) ||
                normalizedGenre.contains(normalizedQuery)
        }
    }

    /**
     * Builds the genre browse model in one pass over the library. Each song is counted at most
     * once for a normalized genre, while the first spelling/capitalization is kept for display.
     */
    fun summarizeGenres(rawGenres: Iterable<String?>): List<Summary> {
        val summaries = LinkedHashMap<String, Summary>()

        rawGenres.forEach { rawGenre ->
            splitGenres(rawGenre).forEach { genre ->
                val key = genre.lowercase()
                val current = summaries[key]
                summaries[key] = if (current == null) {
                    Summary(name = genre, songCount = 1)
                } else {
                    current.copy(songCount = current.songCount + 1)
                }
            }
        }

        return summaries.values.sortedBy { it.name.lowercase() }
    }
}
