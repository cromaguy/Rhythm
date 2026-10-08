/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Utility functions for queue operations, including shuffle algorithms.
 */
object QueueUtils {

    /**
     * Builds an anchored shuffle queue using the Fisher-Yates algorithm.
     *
     * The current song remains at its current position in the queue, while all other
     * songs are shuffled around it. This provides a more intuitive shuffle experience
     * where the currently playing song doesn't suddenly jump to a different position.
     *
     * @param originalQueue The original queue of songs
     * @param anchorIndex The index of the song that should remain in its current position
     * @return A new list with the anchored shuffle applied
     */
    fun buildAnchoredShuffleQueue(originalQueue: List<Song>, anchorIndex: Int): List<Song> {
        if (originalQueue.size <= 1) return originalQueue.toList()
        if (anchorIndex < 0 || anchorIndex >= originalQueue.size) {
            // Invalid anchor, fall back to regular shuffle
            return buildShuffleQueue(originalQueue)
        }

        val result = originalQueue.toMutableList()
        val anchorSong = result[anchorIndex]

        // Remove the anchor song temporarily
        result.removeAt(anchorIndex)

        // Shuffle the remaining songs using Fisher-Yates
        val random = Random(System.currentTimeMillis())
        for (i in result.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            // Swap elements
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }

        // Insert the anchor song back at its original position
        result.add(anchorIndex, anchorSong)

        return result
    }

    /**
     * Suspendable version of buildAnchoredShuffleQueue for large queues.
     * Runs the shuffle operation on a background thread to avoid blocking the UI.
     *
     * @param originalQueue The original queue of songs
     * @param anchorIndex The index of the song that should remain in its current position
     * @return A new list with the anchored shuffle applied
     */
    suspend fun buildAnchoredShuffleQueueSuspending(
        originalQueue: List<Song>,
        anchorIndex: Int
    ): List<Song> = withContext(Dispatchers.Default) {
        buildAnchoredShuffleQueue(originalQueue, anchorIndex)
    }

    /**
     * Builds a completely random shuffle queue using the Fisher-Yates algorithm.
     *
     * @param originalQueue The original queue of songs
     * @return A new list with all songs randomly shuffled
     */
    fun buildShuffleQueue(originalQueue: List<Song>): List<Song> {
        if (originalQueue.size <= 1) return originalQueue.toList()

        val result = originalQueue.toMutableList()
        val random = Random(System.currentTimeMillis())

        // Fisher-Yates shuffle algorithm
        for (i in result.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            // Swap elements
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }

        return result
    }

    /**
     * Prepares a shuffled queue by building it and ensuring the anchor song
     * is at the correct position.
     *
     * @param originalQueue The original queue of songs
     * @param currentSongId The ID of the currently playing song
     * @return A shuffled queue with the current song properly positioned
     */
    fun prepareShuffledQueue(originalQueue: List<Song>, currentSongId: String?): List<Song> {
        if (originalQueue.isEmpty()) return emptyList()

        val currentIndex = if (currentSongId != null) {
            originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }

        return buildAnchoredShuffleQueue(originalQueue, currentIndex)
    }

    /**
     * Restores the upcoming portion of a shuffled queue to the relative order specified by
     * [originalOrder], while keeping all already played songs (indices before [currentIndex])
     * and the currently playing song in their exact positions.
     *
     * Any songs added to [currentSongs] that were not present in [originalOrder] are preserved
     * at the end of the upcoming segment.
     */
    fun <T> restoreQueueOrderOnShuffleDisable(
        currentSongs: List<T>,
        currentIndex: Int,
        originalOrder: List<T>,
        idSelector: (T) -> String
    ): List<T> {
        if (currentSongs.isEmpty()) return emptyList()

        val safeIndex = currentIndex.coerceIn(0, currentSongs.size - 1)
        val played = if (safeIndex > 0) currentSongs.subList(0, safeIndex) else emptyList()
        val current = currentSongs[safeIndex]
        val upcoming = if (safeIndex + 1 < currentSongs.size) {
            currentSongs.subList(safeIndex + 1, currentSongs.size)
        } else {
            emptyList()
        }

        val upcomingIds = upcoming.map(idSelector).toSet()
        val restoredUpcoming = originalOrder.filter { idSelector(it) in upcomingIds } +
            upcoming.filter { song -> originalOrder.none { idSelector(it) == idSelector(song) } }

        return buildList(currentSongs.size) {
            addAll(played)
            add(current)
            addAll(restoredUpcoming)
        }
    }

    /**
     * Overload for [Song] queues.
     */
    fun restoreQueueOrderOnShuffleDisable(
        currentSongs: List<Song>,
        currentIndex: Int,
        originalOrder: List<Song>
    ): List<Song> = restoreQueueOrderOnShuffleDisable(currentSongs, currentIndex, originalOrder) { it.id }
}

/**
 * Represents an entry in a playback queue paired with a stable, persistent entryId
 * that survives reordering, removals, and duplicate items.
 */
data class QueueEntry<T>(
    val entryId: Long,
    val item: T
)

/**
 * Tracks and preserves stable entry IDs for queue items across updates and reorders,
 * even when identical items / duplicate song IDs are present.
 */
class QueueEntryKeyTracker<T>(private val idSelector: (T) -> String) {
    private var nextEntryId = 1L
    private var currentEntries = listOf<QueueEntry<T>>()

    fun sync(items: List<T>): List<QueueEntry<T>> {
        val pool = currentEntries.toMutableList()
        val result = ArrayList<QueueEntry<T>>(items.size)
        for (item in items) {
            val key = idSelector(item)
            val idx = pool.indexOfFirst { idSelector(it.item) == key }
            if (idx >= 0) {
                val matched = pool.removeAt(idx)
                result.add(matched.copy(item = item))
            } else {
                result.add(QueueEntry(entryId = nextEntryId++, item = item))
            }
        }
        currentEntries = result
        return result
    }

    fun updateEntries(entries: List<QueueEntry<T>>) {
        currentEntries = entries
    }

    fun currentEntries(): List<QueueEntry<T>> = currentEntries
}

