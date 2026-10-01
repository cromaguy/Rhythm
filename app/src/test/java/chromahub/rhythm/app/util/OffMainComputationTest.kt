/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class OffMainComputationTest {

    private data class LibraryItem(val title: String, val originalOrder: Int)

    @Test
    fun testSortedByLowercase_matchesStandardLowercaseSorting() {
        val sampleTitles = listOf(
            "Bohemian Rhapsody",
            "africa",
            "Zebra",
            "1999",
            "Hotel California",
            "hotel california (Live)",
            "Épée",
            "über",
            "",
            "  ",
            "Radioactive"
        )
        val items = sampleTitles.mapIndexed { idx, title -> LibraryItem(title, idx) }

        val standardSorted = items.sortedBy { it.title.lowercase() }
        val optimizedSorted = items.sortedByLowercase { it.title }
        assertEquals(standardSorted, optimizedSorted)

        val standardDescending = items.sortedByDescending { it.title.lowercase() }
        val optimizedDescending = items.sortedByLowercase(descending = true) { it.title }
        assertEquals(standardDescending, optimizedDescending)
    }

    @Test
    fun testSortedByLowercase_preservesSortStabilityForEquivalentKeys() {
        val duplicates = listOf(
            LibraryItem("Track A", 0),
            LibraryItem("track a", 1),
            LibraryItem("TRACK A", 2),
            LibraryItem("Track B", 3),
            LibraryItem("track b", 4)
        )

        val sorted = duplicates.sortedByLowercase { it.title }
        assertEquals(listOf(0, 1, 2, 3, 4), sorted.map { it.originalOrder })
    }

    @Test
    fun testSortedByLowercase_handlesLargeDatasetDeterministically() {
        val random = Random(12345)
        val vocabulary = listOf("Echo", "whisper", "NIGHT", "Dawn", "Solitude", "rhythm", "123", "Sound", "wave")
        val largeList = List(10_000) { index ->
            val words = (1..3).map { vocabulary[random.nextInt(vocabulary.size)] }
            val rawTitle = words.joinToString(" ")
            LibraryItem(if (random.nextBoolean()) rawTitle.uppercase() else rawTitle, index)
        }

        val expected = largeList.sortedBy { it.title.lowercase() }
        val actual = largeList.sortedByLowercase { it.title }
        assertEquals(expected, actual)
    }
}
