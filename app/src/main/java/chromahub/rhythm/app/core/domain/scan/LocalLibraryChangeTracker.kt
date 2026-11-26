/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.scan

import chromahub.rhythm.app.features.local.data.database.entity.SongEntity

/**
 * Computes incremental MediaStore -> Room library synchronization deltas so untouched
 * tracks preserve their SQLite `rowid` (and FTS4 index entries) across library refreshes.
 */
object LocalLibraryChangeTracker {

    data class LibraryDiff(
        val toUpsert: List<SongEntity>,
        val removedIds: List<String>,
        val unchangedCount: Int
    ) {
        val hasChanges: Boolean
            get() = toUpsert.isNotEmpty() || removedIds.isNotEmpty()
    }

    fun computeIncrementalDiff(
        existingSongsById: Map<String, SongEntity>,
        scannedSongs: List<SongEntity>
    ): LibraryDiff {
        if (existingSongsById.isEmpty()) {
            return LibraryDiff(
                toUpsert = scannedSongs,
                removedIds = emptyList(),
                unchangedCount = 0
            )
        }

        val scannedIds = HashSet<String>(scannedSongs.size)
        val toUpsert = ArrayList<SongEntity>(scannedSongs.size / 8 + 8)
        var unchanged = 0

        scannedSongs.forEach { fresh ->
            scannedIds.add(fresh.id)
            val old = existingSongsById[fresh.id]
            if (old == null || hasMaterialChange(old, fresh)) {
                toUpsert.add(fresh)
            } else {
                unchanged++
            }
        }

        val removedIds = existingSongsById.keys.filter { it !in scannedIds }
        return LibraryDiff(
            toUpsert = toUpsert,
            removedIds = removedIds,
            unchangedCount = unchanged
        )
    }

    private fun hasMaterialChange(old: SongEntity, fresh: SongEntity): Boolean {
        return old.dateModified != fresh.dateModified ||
            old.title != fresh.title ||
            old.artist != fresh.artist ||
            old.album != fresh.album ||
            old.albumId != fresh.albumId ||
            old.duration != fresh.duration ||
            old.uri != fresh.uri ||
            old.artworkUri != fresh.artworkUri ||
            old.trackNumber != fresh.trackNumber ||
            old.year != fresh.year ||
            old.genre != fresh.genre ||
            old.albumArtist != fresh.albumArtist ||
            old.discNumber != fresh.discNumber ||
            old.path != fresh.path
    }
}
