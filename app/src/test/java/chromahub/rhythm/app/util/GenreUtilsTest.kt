package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreUtilsTest {
    @Test
    fun summarizeGenres_countsNormalizedGenresOncePerSong() {
        val summaries = GenreUtils.summarizeGenres(
            listOf(
                "Rock; Pop; rock",
                "pop, Jazz",
                "Unknown",
                null
            )
        )

        assertEquals(
            listOf(
                GenreUtils.Summary("Jazz", 1),
                GenreUtils.Summary("Pop", 2),
                GenreUtils.Summary("Rock", 1)
            ),
            summaries
        )
    }
}
