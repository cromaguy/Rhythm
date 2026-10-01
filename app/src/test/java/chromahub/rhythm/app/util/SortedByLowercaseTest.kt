/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SortedByLowercaseTest {

    private data class Item(val title: String, val index: Int)

    /** Titles with mixed case, duplicates (for stability) and non-ASCII characters. */
    private fun library(size: Int): List<Item> {
        val random = Random(42)
        val words = listOf("alpha", "Beta", "GAMMA", "delta", "Épée", "zulu", "ß", "İstanbul", "10 Songs", "2 Songs")
        return List(size) { i ->
            val title = (0 until 3).joinToString(" ") { words[random.nextInt(words.size)] }
            Item(if (random.nextBoolean()) title.uppercase() else title, i)
        }
    }

    @Test
    fun sameOrderAsSortingByLowercaseInTheComparator() {
        val items = library(5_000)

        assertEquals(items.sortedBy { it.title.lowercase() }, items.sortedByLowercase { it.title })
        assertEquals(
            items.sortedByDescending { it.title.lowercase() },
            items.sortedByLowercase(descending = true) { it.title }
        )
    }

    @Test
    fun largeLibraryIsFasterThanLowercasingInTheComparator() {
        // A 36k-song library, the size that caused the ANR when sorted in composition.
        val items = library(36_000)
        repeat(3) { items.sortedByLowercase { it.title }; items.sortedBy { it.title.lowercase() } } // warm-up

        val keyedMs = bestOf(5) { items.sortedByLowercase { it.title } }
        val comparatorMs = bestOf(5) { items.sortedBy { it.title.lowercase() } }

        println("sortedByLowercase ${keyedMs}ms vs comparator lowercase ${comparatorMs}ms for 36k items")
        assertTrue("keyed sort ($keyedMs ms) should beat lowercasing per comparison ($comparatorMs ms)", keyedMs < comparatorMs)
    }

    private fun bestOf(runs: Int, block: () -> Unit): Long =
        (1..runs).minOf { val start = System.nanoTime(); block(); (System.nanoTime() - start) / 1_000_000 }
}
