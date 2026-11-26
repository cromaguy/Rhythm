/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.viewmodel

import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for multi-selection functionality in LibraryScreen tabs.
 * Manages selection state with order preservation using a LinkedHashSet internally.
 *
 * Selection order is maintained - the first selected song is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
class MultiSelectionStateHolder {

    // Internal mutable state - uses List to preserve selection order
    // LinkedHashSet behavior is enforced via toggle logic
    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())
    
    /**
     * Immutable flow of selected songs, preserving selection order.
     */
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs.asStateFlow()
    
    /**
     * Set of selected song IDs for efficient lookup.
     */
    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds.asStateFlow()
    
    /**
     * Whether selection mode is currently active (at least one song selected).
     */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * Current count of selected songs.
     */
    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    /**
     * Toggles the selection state of a song.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param song The song to toggle
     */
    fun toggleSelection(song: Song) {
        val currentList = _selectedSongs.value.toMutableList()
        val currentIds = _selectedSongIds.value.toMutableSet()
        
        if (currentIds.contains(song.id)) {
            // Remove from selection
            currentList.removeAll { it.id == song.id }
            currentIds.remove(song.id)
        } else {
            // Add to selection (preserving order)
            currentList.add(song)
            currentIds.add(song.id)
        }
        
        updateState(currentList, currentIds)
    }

    /**
     * Selects all songs from the provided list.
     * Previously selected songs that are in the new list maintain their position.
     * New songs are appended in their list order.
     *
     * @param songs The complete list of songs to select
     */
    fun selectAll(songs: List<Song>) {
        val currentIds = _selectedSongIds.value
        val currentList = _selectedSongs.value.toMutableList()
        
        // Add songs that aren't already selected
        songs.forEach { song ->
            if (!currentIds.contains(song.id)) {
                currentList.add(song)
            }
        }
        
        val newIds = currentList.map { it.id }.toSet()
        updateState(currentList, newIds)
    }

    /**
     * Clears all selected songs, exiting selection mode.
     */
    fun clearSelection() {
        updateState(emptyList(), emptySet())
    }

    /**
     * Checks if a song is currently selected.
     *
     * @param songId The ID of the song to check
     * @return True if the song is selected, false otherwise
     */
    fun isSelected(songId: String): Boolean {
        return _selectedSongIds.value.contains(songId)
    }

    /**
     * Gets the selection index (0-based) of a song for display purposes.
     * Returns null if the song is not selected.
     *
     * @param songId The ID of the song
     * @return 0-based selection index, or null if not selected
     */
    fun getSelectionIndex(songId: String): Int? {
        val index = _selectedSongs.value.indexOfFirst { it.id == songId }
        return if (index >= 0) index else null
    }

    /**
     * Removes a specific song from selection if it exists.
     * Useful when a song is deleted from the library.
     *
     * @param songId The ID of the song to remove
     */
    fun removeFromSelection(songId: String) {
        if (!_selectedSongIds.value.contains(songId)) return
        
        val currentList = _selectedSongs.value.filter { it.id != songId }
        val currentIds = _selectedSongIds.value - songId
        updateState(currentList, currentIds)
    }

    /**
     * Applies a contiguous drag-to-select range update while preserving baseline selections
     * from before the drag began.
     */
    fun applyDragRange(
        items: List<Song>,
        range: IntRange,
        select: Boolean,
        baselineIds: Set<String>
    ) {
        val rangeIds = buildSet {
            for (i in range) {
                items.getOrNull(i)?.id?.let(::add)
            }
        }
        val targetIds = if (select) baselineIds + rangeIds else baselineIds - rangeIds
        val existingById = _selectedSongs.value.associateBy { it.id }
        val catalogById = items.associateBy { it.id }
        val orderedList = ArrayList<Song>(targetIds.size)
        for (song in _selectedSongs.value) {
            if (song.id in targetIds) orderedList.add(song)
        }
        val already = orderedList.mapTo(HashSet()) { it.id }
        for (i in range) {
            val s = items.getOrNull(i) ?: continue
            if (s.id in targetIds && s.id !in already) {
                orderedList.add(s)
                already.add(s.id)
            }
        }
        for (id in targetIds) {
            if (id !in already) {
                (existingById[id] ?: catalogById[id])?.let(orderedList::add)
            }
        }
        updateState(orderedList, targetIds)
    }

    /**
     * Updates all state flows atomically.
     */
    private fun updateState(songs: List<Song>, ids: Set<String>) {
        _selectedSongs.value = songs
        _selectedSongIds.value = ids
        _selectedCount.value = songs.size
        _isSelectionMode.value = songs.isNotEmpty()
    }
}
