/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.offline

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.local.data.database.entity.OfflineTrackEntity
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOfflineRepositoryTest {

    private fun streamingSong(id: String, title: String, url: String?): StreamingSong = StreamingSong(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        artworkUri = null,
        sourceType = SourceType.SUBSONIC,
        streamingUrl = url,
        previewUrl = null
    )

    @Test
    fun uniqueWorkName_prefixesDeterministically() {
        assertEquals("cloud_dl_subsonic::42", CloudOfflineRepository.uniqueWorkName("subsonic::42"))
    }

    @Test
    fun computeSyncDelta_skipsMatchingCompletedAndUpsertsNewOrChanged() {
        val existingCompleted = OfflineTrackEntity(
            songId = "subsonic::1",
            provider = "SUBSONIC",
            serverUrl = "",
            localPath = "file:///cache/1.flac",
            title = "Track One",
            artist = "Artist",
            album = "Album",
            artworkPath = null,
            fileSizeBytes = 1024L,
            status = OfflineDownloadStatus.COMPLETED,
            progress = 1f,
            downloadedAtMs = 1000L,
            errorMessage = null
        )
        val existingMoved = OfflineTrackEntity(
            songId = "subsonic::2",
            provider = "SUBSONIC",
            serverUrl = "",
            localPath = "file:///old/2.flac",
            title = "Track Two",
            artist = "Artist",
            album = "Album",
            artworkPath = null,
            fileSizeBytes = 1024L,
            status = OfflineDownloadStatus.FAILED,
            progress = 0f,
            downloadedAtMs = 2000L,
            errorMessage = "stale"
        )

        val downloaded = listOf(
            streamingSong("subsonic::1", "Track One", "file:///cache/1.flac"),
            streamingSong("subsonic::2", "Track Two", "file:///cache/2.flac"),
            streamingSong("subsonic::3", "Track Three", "file:///cache/3.flac")
        )

        val delta = CloudOfflineRepository.computeSyncDelta(
            existingById = mapOf("subsonic::1" to existingCompleted, "subsonic::2" to existingMoved),
            downloadedSongs = downloaded,
            nowMs = 9999L
        )

        assertEquals(2, delta.size)
        val second = delta.first { it.songId == "subsonic::2" }
        assertEquals(OfflineDownloadStatus.COMPLETED, second.status)
        assertEquals("file:///cache/2.flac", second.localPath)
        assertEquals(2000L, second.downloadedAtMs)

        val third = delta.first { it.songId == "subsonic::3" }
        assertEquals(OfflineDownloadStatus.COMPLETED, third.status)
        assertEquals(9999L, third.downloadedAtMs)
        assertTrue(delta.none { it.songId == "subsonic::1" })
    }
}
