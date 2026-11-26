/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.scan

import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryChangeTrackerTest {

    private fun entity(id: String, title: String, modified: Long) = SongEntity(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = "alb",
        duration = 180_000L,
        uri = "content://media/$id",
        artworkUri = null,
        trackNumber = 1,
        year = 2024,
        genre = null,
        dateAdded = 1000L,
        dateModified = modified,
        albumArtist = null,
        bitrate = null,
        sampleRate = null,
        channels = null,
        codec = null,
        discNumber = 1,
        path = "/Music/$id.flac"
    )

    @Test
    fun computeIncrementalDiff_detectsOnlyChangedAddedAndRemovedRows() {
        val e1 = entity("1", "Unchanged", 100L)
        val e2 = entity("2", "Modified Old", 200L)
        val e3 = entity("3", "Deleted", 300L)

        val e1Same = entity("1", "Unchanged", 100L)
        val e2Updated = entity("2", "Modified New", 250L)
        val e4Added = entity("4", "Added", 400L)

        val diff = LocalLibraryChangeTracker.computeIncrementalDiff(
            existingSongsById = mapOf("1" to e1, "2" to e2, "3" to e3),
            scannedSongs = listOf(e1Same, e2Updated, e4Added)
        )

        assertTrue(diff.hasChanges)
        assertEquals(1, diff.unchangedCount)
        assertEquals(listOf("2", "4"), diff.toUpsert.map { it.id })
        assertEquals(listOf("3"), diff.removedIds)

        val noOpDiff = LocalLibraryChangeTracker.computeIncrementalDiff(
            existingSongsById = mapOf("1" to e1),
            scannedSongs = listOf(e1Same)
        )
        assertFalse(noOpDiff.hasChanges)
        assertEquals(1, noOpDiff.unchangedCount)
    }
}
