/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.architecture

import android.net.TestUri
import chromahub.rhythm.app.features.local.data.repository.LibraryQueryDelegate
import chromahub.rhythm.app.features.local.presentation.viewmodel.QueueBoard
import chromahub.rhythm.app.infrastructure.service.coordinator.AudioEffectsCoordinator
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.ArtworkMediaUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureDelegatesTest {

    private fun song(id: String, path: String? = null) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        album = "Album",
        duration = 180_000L,
        uri = TestUri("content", "/media/$id"),
        path = path
    )

    @Test
    fun queueBoard_handlesShufflePinningPlayNextMoveAndRemoveAccurately() {
        val list = listOf(song("1"), song("2"), song("3"), song("4"))

        val shuffled = QueueBoard.buildShuffledQueue(list, startIndex = 2, pinStartIndex = true)
        assertEquals("3", shuffled.songs.first().id)
        assertEquals(4, shuffled.songs.size)

        val playNext = QueueBoard.insertPlayNext(list, currentIndex = 1, song = song("99"))
        assertEquals(listOf("1", "2", "99", "3", "4"), playNext.songs.map { it.id })
        assertEquals(2, playNext.targetIndex)

        val moved = QueueBoard.moveItemWithIndex(list, currentIndex = 1, fromIndex = 0, toIndex = 3)
        assertEquals(listOf("2", "3", "4", "1"), moved.songs.map { it.id })
        assertEquals(0, moved.targetIndex)

        val removed = QueueBoard.removeAt(list, removeIndex = 0, currentPlayingIndex = 2)
        assertEquals(listOf("2", "3", "4"), removed.songs.map { it.id })
        assertEquals(1, removed.targetIndex)
    }

    @Test
    fun audioEffectsCoordinator_interpolatesLogarithmicallyAndClampsMillibels() {
        val tenBands = floatArrayOf(4f, 3f, 2f, 0f, -1f, -2f, 0f, 2f, 4f, 5f)
        val fiveBands = AudioEffectsCoordinator.interpolateBands(tenBands, outputBands = 5)
        assertEquals(5, fiveBands.size)
        assertEquals(3.0f, fiveBands[0], 0.01f)

        val hwBands = AudioEffectsCoordinator.interpolateBands(
            tenBands,
            outputBands = 3,
            hardwareCenterFreqsHz = floatArrayOf(62f, 1000f, 8000f)
        )
        assertEquals(3.0f, hwBands[0], 0.01f)
        assertEquals(-2.0f, hwBands[1], 0.01f)
        assertEquals(4.0f, hwBands[2], 0.01f)

        assertEquals(1500.toShort(), AudioEffectsCoordinator.clampMillibel(2500, -1500, 1500))
    }

    @Test
    fun artworkAndLibraryDelegates_computeExpectedSampleSizesAndAlbumKeys() {
        assertEquals(8, ArtworkMediaUtils.calculateInSampleSize(2048, 2048, 256, 256))
        assertEquals(1, ArtworkMediaUtils.calculateInSampleSize(200, 200, 512, 512))

        val discSong = song("10", path = "/storage/emulated/0/Music/Daft Punk/Discovery/CD 01/01.flac")
        assertTrue(LibraryQueryDelegate.getRootAlbumFolder(discSong.path)?.endsWith("/discovery") == true)
        assertTrue(LibraryQueryDelegate.getAlbumGroupingKey(discSong).startsWith("folder_"))
        assertEquals("Daft Punk", LibraryQueryDelegate.extractPrimaryArtist("Daft Punk feat. Pharrell"))
    }
}
