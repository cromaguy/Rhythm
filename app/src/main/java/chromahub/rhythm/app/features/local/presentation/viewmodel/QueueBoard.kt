/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.viewmodel

import chromahub.rhythm.app.shared.data.model.Song

/**
 * Pure queue-mutation state helpers extracted from [MusicViewModel] so queue ordering,
 * Play Next insertion, manual shuffle pinning, and drag-move index calculation remain
 * deterministic and unit-testable.
 */
object QueueBoard {

    data class QueueUpdate(
        val songs: List<Song>,
        val targetIndex: Int
    )

    fun buildShuffledQueue(
        songs: List<Song>,
        startIndex: Int,
        pinStartIndex: Boolean
    ): QueueUpdate {
        if (songs.isEmpty()) return QueueUpdate(emptyList(), 0)
        val validStart = startIndex.coerceIn(0, songs.lastIndex)
        val mutable = songs.toMutableList()
        if (pinStartIndex) {
            val anchor = mutable.removeAt(validStart)
            mutable.shuffle()
            mutable.add(0, anchor)
        } else {
            mutable.shuffle()
        }
        return QueueUpdate(mutable, 0)
    }

    fun insertPlayNext(
        currentSongs: List<Song>,
        currentIndex: Int,
        song: Song
    ): QueueUpdate {
        if (currentSongs.isEmpty()) {
            return QueueUpdate(listOf(song), 0)
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, currentSongs.size)
        val updated = currentSongs.toMutableList().apply { add(insertIndex, song) }
        return QueueUpdate(updated, insertIndex)
    }

    fun moveItem(
        currentSongs: List<Song>,
        fromIndex: Int,
        toIndex: Int
    ): List<Song> {
        if (fromIndex !in currentSongs.indices || toIndex !in currentSongs.indices || fromIndex == toIndex) {
            return currentSongs
        }
        return currentSongs.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(toIndex, moved)
        }
    }

    fun moveItemWithIndex(
        currentSongs: List<Song>,
        currentIndex: Int,
        fromIndex: Int,
        toIndex: Int
    ): QueueUpdate {
        if (fromIndex !in currentSongs.indices || toIndex !in currentSongs.indices || fromIndex == toIndex) {
            return QueueUpdate(currentSongs, currentIndex)
        }
        val movedList = moveItem(currentSongs, fromIndex, toIndex)
        val newCurrentIndex = when {
            fromIndex == currentIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }.coerceIn(0, movedList.lastIndex)
        return QueueUpdate(movedList, newCurrentIndex)
    }

    fun removeAt(
        currentSongs: List<Song>,
        removeIndex: Int,
        currentPlayingIndex: Int
    ): QueueUpdate {
        if (removeIndex !in currentSongs.indices) {
            return QueueUpdate(currentSongs, currentPlayingIndex)
        }
        val updated = currentSongs.toMutableList().apply { removeAt(removeIndex) }
        if (updated.isEmpty()) {
            return QueueUpdate(emptyList(), -1)
        }
        val nextIndex = when {
            removeIndex < currentPlayingIndex -> (currentPlayingIndex - 1).coerceAtLeast(0)
            removeIndex == currentPlayingIndex -> currentPlayingIndex.coerceAtMost(updated.lastIndex)
            else -> currentPlayingIndex.coerceIn(0, updated.lastIndex)
        }
        return QueueUpdate(updated, nextIndex)
    }
}
