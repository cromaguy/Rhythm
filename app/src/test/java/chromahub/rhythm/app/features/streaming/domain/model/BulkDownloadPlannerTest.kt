/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.domain.model

import chromahub.rhythm.app.core.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkDownloadPlannerTest {

    private fun song(id: String) = StreamingSong(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        artworkUri = null,
        sourceType = SourceType.SUBSONIC,
        streamingUrl = null,
        previewUrl = null
    )

    @Test
    fun pending_skipsDownloadedAndDownloadingTracks() {
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))

        val pending = BulkDownloadPlanner.pending(
            songs,
            downloadedIds = setOf("b"),
            downloadingIds = setOf("d")
        )

        assertEquals(listOf("a", "c"), pending.map { it.id })
    }

    @Test
    fun pending_keepsPlaylistOrderAndDropsDuplicates() {
        val songs = listOf(song("z"), song("a"), song("z"), song("m"))

        val pending = BulkDownloadPlanner.pending(songs, emptySet(), emptySet())

        assertEquals(listOf("z", "a", "m"), pending.map { it.id })
    }

    @Test
    fun pending_isEmptyWhenEverythingIsAlreadyDownloaded() {
        val songs = listOf(song("a"), song("b"))

        val pending = BulkDownloadPlanner.pending(songs, setOf("a", "b"), emptySet())

        assertTrue(pending.isEmpty())
    }

    @Test
    fun progress_isDoneWhenCompletedAndFailedReachTotal() {
        val running = BulkDownloadProgress(targetId = "p", label = "Mix", total = 3, completed = 1, failed = 1)
        val done = running.copy(completed = 2)

        assertFalse(running.isDone)
        assertEquals(2, running.finished)
        assertTrue(done.isDone)
    }
}
