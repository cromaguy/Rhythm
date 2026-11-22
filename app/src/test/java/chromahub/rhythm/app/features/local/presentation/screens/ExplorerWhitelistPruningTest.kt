/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens

import android.content.Context
import android.content.ContextWrapper
import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.MediaScanMode
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExplorerWhitelistPruningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context

    private class MockTestContext(private val baseFilesDir: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = baseFilesDir
    }

    @Before
    fun setUp() {
        mockContext = MockTestContext(tempFolder.root)
    }

    @Test
    fun `isFolderRelevantToWhitelist recognizes exact, ancestor, and descendant folders`() {
        val whitelistedFolder = "/storage/emulated/0/Music"

        // Exact match
        assertTrue(isFolderRelevantToWhitelist("/storage/emulated/0/Music", whitelistedFolder))
        assertTrue(isFolderRelevantToWhitelist("/storage/emulated/0/Music/", whitelistedFolder))

        // Ancestor folders (must allow drilling down from root to reach the whitelisted folder)
        assertTrue(isFolderRelevantToWhitelist("/storage/emulated/0", whitelistedFolder))
        assertTrue(isFolderRelevantToWhitelist("/storage", whitelistedFolder))

        // Descendant folders (subdirectories inside the whitelisted folder)
        assertTrue(isFolderRelevantToWhitelist("/storage/emulated/0/Music/Rock", whitelistedFolder))
        assertTrue(isFolderRelevantToWhitelist("/storage/emulated/0/Music/Jazz/Modern", whitelistedFolder))

        // Unrelated folders must be pruned immediately
        assertFalse(isFolderRelevantToWhitelist("/storage/emulated/0/DCIM", whitelistedFolder))
        assertFalse(isFolderRelevantToWhitelist("/storage/emulated/0/Pictures", whitelistedFolder))
        assertFalse(isFolderRelevantToWhitelist("/storage/emulated/0/Download", whitelistedFolder))

        // Sibling folders sharing prefix (e.g. Music2, MusicVideos) must NOT match
        assertFalse(isFolderRelevantToWhitelist("/storage/emulated/0/Music2", whitelistedFolder))
        assertFalse(isFolderRelevantToWhitelist("/storage/emulated/0/MusicVideos", whitelistedFolder))
        assertFalse(isFolderRelevantToWhitelist("/storage/emulated/0/Music_Backup", whitelistedFolder))

        // Different storage roots
        assertFalse(isFolderRelevantToWhitelist("/storage/SDCARD", whitelistedFolder))
    }

    @Test
    fun `Song getSongPath returns path immediately when present or from file uri without ContentResolver`() {
        val songWithPath = Song(
            id = "1",
            title = "Track With Path",
            artist = "Artist",
            album = "Album",
            duration = 180000L,
            uri = TestUri("content", "/media/external/audio/media/1"),
            path = "/storage/emulated/0/Music/song1.mp3"
        )

        assertEquals("/storage/emulated/0/Music/song1.mp3", songWithPath.getSongPath(mockContext))

        val songWithFileUri = Song(
            id = "2",
            title = "Track With File URI",
            artist = "Artist",
            album = "Album",
            duration = 180000L,
            uri = TestUri("file", "/storage/emulated/0/Music/song2.flac"),
            path = null
        )

        assertEquals("/storage/emulated/0/Music/song2.flac", songWithFileUri.getSongPath(mockContext))
    }

    @Test
    fun `buildSongPathMap indexes songs with direct paths or file URIs rapidly`() {
        val songs = listOf(
            Song(
                id = "1",
                title = "Song One",
                artist = "Artist",
                album = "Album",
                duration = 200000L,
                uri = TestUri("content", "/media/external/audio/media/1"),
                path = "/storage/emulated/0/Music/Rock/song1.mp3"
            ),
            Song(
                id = "2",
                title = "Song Two",
                artist = "Artist",
                album = "Album",
                duration = 210000L,
                uri = TestUri("file", "/storage/emulated/0/Music/Jazz/song2.flac"),
                path = null
            )
        )

        val pathMap = buildSongPathMap(songs, mockContext)
        assertEquals(2, pathMap.size)
        assertEquals("Song One", pathMap["/storage/emulated/0/Music/Rock/song1.mp3"]?.title)
        assertEquals("Song Two", pathMap["/storage/emulated/0/Music/Jazz/song2.flac"]?.title)
    }

    @Test
    fun `getDirectoryContentsOptimized prunes non-whitelisted folders when whitelist mode active`() {
        val baseDir = tempFolder.newFolder("explorer_test")
        val musicFolder = File(baseDir, "Music").apply { mkdirs() }
        val dcimFolder = File(baseDir, "DCIM").apply { mkdirs() }
        val music2Folder = File(baseDir, "Music2").apply { mkdirs() }

        val songFile = File(musicFolder, "song.mp3").apply { writeText("audio content") }
        val whitelistedFolders = listOf(musicFolder.absolutePath)

        val song = Song(
            id = "100",
            title = "Test Audio",
            artist = "Test Artist",
            album = "Test Album",
            duration = 100000L,
            uri = TestUri("file", songFile.absolutePath),
            path = songFile.absolutePath
        )
        val songPathMap = mapOf(songFile.absolutePath to song)

        val items = getDirectoryContentsOptimized(
            directoryPath = baseDir.absolutePath,
            songPathMap = songPathMap,
            context = mockContext,
            mediaScanMode = MediaScanMode.WHITELIST,
            whitelistedFolders = whitelistedFolders
        )

        val folderNames = items.filter { it.isDirectory }.map { it.name }
        assertTrue("Music folder must be included", folderNames.contains("Music"))
        assertFalse("DCIM folder must be pruned", folderNames.contains("DCIM"))
        assertFalse("Music2 sibling folder must be pruned", folderNames.contains("Music2"))
    }
}
