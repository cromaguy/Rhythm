/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePathUtilsTest {

    @Test
    fun sanitizeAndNormalizePath_handlesNormalPath() {
        val input = "/storage/emulated/0/Music"
        assertEquals("/storage/emulated/0/Music", StoragePathUtils.sanitizeAndNormalizePath(input))
    }

    @Test
    fun sanitizeAndNormalizePath_stripsTrailingSlashes() {
        val input = "/storage/emulated/0/Music/"
        assertEquals("/storage/emulated/0/Music", StoragePathUtils.sanitizeAndNormalizePath(input))

        val inputMultiSlash = "/storage/emulated/0/Music///"
        assertEquals("/storage/emulated/0/Music", StoragePathUtils.sanitizeAndNormalizePath(inputMultiSlash))
    }

    @Test
    fun sanitizeAndNormalizePath_normalizesDuplicateSlashes() {
        val input = "/storage/emulated/0//Music///Rock"
        assertEquals("/storage/emulated/0/Music/Rock", StoragePathUtils.sanitizeAndNormalizePath(input))
    }

    @Test
    fun sanitizeAndNormalizePath_stripsRawPrefix() {
        val input = "raw:/storage/emulated/0/Download"
        assertEquals("/storage/emulated/0/Download", StoragePathUtils.sanitizeAndNormalizePath(input))
    }

    @Test
    fun sanitizeAndNormalizePath_healsDuplicatedPrefixes() {
        val input1 = "/storage/emulated/0//storage/emulated/0/Download"
        assertEquals("/storage/emulated/0/Download", StoragePathUtils.sanitizeAndNormalizePath(input1))

        val input2 = "/storage/emulated/0/storage/emulated/0/Music/MySongs"
        assertEquals("/storage/emulated/0/Music/MySongs", StoragePathUtils.sanitizeAndNormalizePath(input2))
    }

    @Test
    fun sanitizeAndNormalizePath_handlesRootAndEmpty() {
        assertEquals("", StoragePathUtils.sanitizeAndNormalizePath(""))
        assertEquals("/", StoragePathUtils.sanitizeAndNormalizePath("/"))
    }

    @Test
    fun isFolderSubdirectoryOrEqual_exactMatch() {
        assertTrue(
            StoragePathUtils.isFolderSubdirectoryOrEqual(
                parentFolder = "/storage/emulated/0/Music",
                childPath = "/storage/emulated/0/Music"
            )
        )
    }

    @Test
    fun isFolderSubdirectoryOrEqual_nestedSubdirectory() {
        assertTrue(
            StoragePathUtils.isFolderSubdirectoryOrEqual(
                parentFolder = "/storage/emulated/0/Music",
                childPath = "/storage/emulated/0/Music/Rock/Classic"
            )
        )
    }

    @Test
    fun isFolderSubdirectoryOrEqual_preventsFalseSiblingMatch() {
        assertFalse(
            StoragePathUtils.isFolderSubdirectoryOrEqual(
                parentFolder = "/storage/emulated/0/Music",
                childPath = "/storage/emulated/0/Music2"
            )
        )
        assertFalse(
            StoragePathUtils.isFolderSubdirectoryOrEqual(
                parentFolder = "/storage/emulated/0/Music",
                childPath = "/storage/emulated/0/Music-Archive"
            )
        )
    }

    @Test
    fun isFolderSubdirectoryOrEqual_differentRoots() {
        assertFalse(
            StoragePathUtils.isFolderSubdirectoryOrEqual(
                parentFolder = "/storage/emulated/0/Download",
                childPath = "/storage/emulated/0/Music"
            )
        )
    }

    @Test
    fun isFolderSubdirectoryOrEqual_trailingSlashTolerance() {
        assertTrue(
            StoragePathUtils.isFolderSubdirectoryOrEqual(
                parentFolder = "/storage/emulated/0/Music/",
                childPath = "/storage/emulated/0/Music/Favorites/"
            )
        )
    }

    @Test
    fun isPathInFolders_fileInFolderOrSubfolder() {
        val folders = setOf("/storage/emulated/0/Music", "/storage/emulated/0/Podcasts")

        assertTrue(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music/song.mp3", folders))
        assertTrue(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music/Pop/song.flac", folders))
        assertTrue(StoragePathUtils.isPathInFolders("/storage/emulated/0/Podcasts/ep1.m4a", folders))
    }

    @Test
    fun isPathInFolders_fileInSiblingFolderReturnsFalse() {
        val folders = setOf("/storage/emulated/0/Music")

        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music2/song.mp3", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music2", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music-Archive", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music-Archive/track.flac", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/MusicOld", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Download/song.mp3", folders))
    }

    @Test
    fun isPathInFolders_caseInsensitiveAndTrailingSlashHandling() {
        val folders = listOf("/storage/emulated/0/Music/")

        assertTrue(StoragePathUtils.isPathInFolders("/storage/emulated/0/music", folders))
        assertTrue(StoragePathUtils.isPathInFolders("/STORAGE/EMULATED/0/MUSIC/", folders))
        assertTrue(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music/Pop/song.mp3", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music2", folders))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/music2/song.mp3", folders))
    }

    @Test
    fun isPathInFolders_emptyFoldersOrNullPath() {
        assertFalse(StoragePathUtils.isPathInFolders(null, setOf("/storage/emulated/0/Music")))
        assertFalse(StoragePathUtils.isPathInFolders("", setOf("/storage/emulated/0/Music")))
        assertFalse(StoragePathUtils.isPathInFolders("/storage/emulated/0/Music/song.mp3", emptySet()))
    }

    @Test
    fun extractFolderRelativePath_extractsCorrectRelativeDirectory() {
        val rel = StoragePathUtils.extractFolderRelativePath(
            baseFolder = "/storage/emulated/0/Music",
            fullFilePath = "/storage/emulated/0/Music/Rock/Heavy/song.mp3"
        )
        assertEquals("Rock/Heavy", rel)

        val rootRel = StoragePathUtils.extractFolderRelativePath(
            baseFolder = "/storage/emulated/0/Music",
            fullFilePath = "/storage/emulated/0/Music/song.mp3"
        )
        assertEquals("", rootRel)
    }

    @Test
    fun isAudioFile_recognizesAudioExtensions() {
        assertTrue(StoragePathUtils.isAudioFile("song.mp3"))
        assertTrue(StoragePathUtils.isAudioFile("song.FLAC"))
        assertTrue(StoragePathUtils.isAudioFile("track.opus"))
        assertTrue(StoragePathUtils.isAudioFile("audio.m4a"))
        assertTrue(StoragePathUtils.isAudioFile("audio.wav"))
        assertTrue(StoragePathUtils.isAudioFile("audio.ogg"))

        assertFalse(StoragePathUtils.isAudioFile("cover.jpg"))
        assertFalse(StoragePathUtils.isAudioFile("lyrics.lrc"))
        assertFalse(StoragePathUtils.isAudioFile(".nomedia"))
        assertFalse(StoragePathUtils.isAudioFile("video.mp4"))
    }
}
