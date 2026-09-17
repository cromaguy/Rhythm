/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.viewmodel

import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the state of the playback queue, including original order preservation
 * for shuffle operations.
 */
class QueueStateHolder(private val appSettings: AppSettings? = null) {

    /**
     * The original queue order before shuffling.
     * This is preserved so we can restore the original order when shuffle is disabled.
     */
    private val _originalQueueOrder = MutableStateFlow<List<Song>>(emptyList())
    val originalQueueOrder: List<Song>
        get() = _originalQueueOrder.value

    /**
     * The source name of the current queue (e.g., "Album: Greatest Hits", "Playlist: Favorites").
     * Used for persistence and display purposes.
     */
    private val _currentQueueSourceName = MutableStateFlow<String?>(null)
    val currentQueueSourceName: StateFlow<String?> = _currentQueueSourceName.asStateFlow()

    /**
     * Whether the current queue has an original order saved.
     */
    fun hasOriginalQueue(): Boolean = _originalQueueOrder.value.isNotEmpty()

    /**
     * Sets the original queue order. This should be called before enabling shuffle
     * to preserve the unshuffled state.
     */
    fun setOriginalQueueOrder(songs: List<Song>) {
        _originalQueueOrder.value = songs.toList()
        appSettings?.setSavedOriginalQueue(songs.map { it.id })
    }

    /**
     * Saves the original queue state including the source name.
     */
    fun saveOriginalQueueState(songs: List<Song>, sourceName: String?) {
        setOriginalQueueOrder(songs)
        _currentQueueSourceName.value = sourceName
        appSettings?.setSavedOriginalQueueSource(sourceName)
    }

    /**
     * Restores the original queue state (e.g. on application restart).
     */
    fun restoreOriginalQueueState(songs: List<Song>, sourceName: String?) {
        _originalQueueOrder.value = songs.toList()
        _currentQueueSourceName.value = sourceName
    }

    /**
     * Clears the original queue order. This should be called when shuffle is disabled
     * or when a new queue is loaded.
     */
    fun clearOriginalQueue() {
        _originalQueueOrder.value = emptyList()
        _currentQueueSourceName.value = null
        appSettings?.clearSavedOriginalQueue()
    }

    /**
     * Gets the original queue order, filtering out any songs that may have been
     * removed from the current queue.
     */
    fun getFilteredOriginalQueue(currentSongs: List<Song>): List<Song> {
        if (_originalQueueOrder.value.isEmpty()) return currentSongs

        val currentIds = currentSongs.map { it.id }.toSet()
        return _originalQueueOrder.value.filter { it.id in currentIds }
    }

    /**
     * Updates the source name of the current queue.
     */
    fun setQueueSourceName(sourceName: String?) {
        _currentQueueSourceName.value = sourceName
    }
}
