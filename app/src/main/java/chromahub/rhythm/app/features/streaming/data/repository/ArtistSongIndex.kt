/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong

/**
 * Groups songs by case-insensitive credited artist name, indexed once per batch.
 * De-duplicates songs by id while preserving input order.
 */
internal class ArtistSongIndex(
    songs: List<StreamingSong>,
    splitArtistNames: (String) -> List<String>
) {
    private val songsByArtist: Map<String, List<StreamingSong>>

    init {
        val index = HashMap<String, MutableList<StreamingSong>>()
        val seenIds = HashSet<String>()
        for (song in songs) {
            if (song.artist.isBlank() || !seenIds.add(song.id)) continue
            splitArtistNames(song.artist)
                .map { it.lowercase() }
                .distinct()
                .forEach { name -> index.getOrPut(name) { mutableListOf() }.add(song) }
        }
        songsByArtist = index
    }

    fun songsFor(artistName: String): List<StreamingSong> {
        if (artistName.isBlank()) return emptyList()
        return songsByArtist[artistName.lowercase()].orEmpty()
    }
}
