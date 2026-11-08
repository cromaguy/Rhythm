/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadFilesTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("download-files-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun resolve_findsFlacWhenAskedForTheDefaultMp3Name() {
        // The index loader asks with the default extension; a FLAC download must still be found.
        val flac = File(dir, "SUBSONIC__abc.flac").apply { writeText("flac bytes") }

        assertEquals(flac, DownloadFiles.resolve(dir, "SUBSONIC::abc"))
    }

    @Test
    fun resolve_prefersTheRequestedExtension() {
        File(dir, "SUBSONIC__abc.flac").writeText("flac bytes")
        val ogg = File(dir, "SUBSONIC__abc.ogg").apply { writeText("ogg bytes") }

        assertEquals(ogg, DownloadFiles.resolve(dir, "SUBSONIC::abc", "ogg"))
    }

    @Test
    fun resolve_ignoresEmptyFilesAndFallsBackToTheRequestedName() {
        File(dir, "SUBSONIC__abc.flac").writeText("")

        val resolved = DownloadFiles.resolve(dir, "SUBSONIC::abc")

        assertEquals(File(dir, "SUBSONIC__abc.mp3"), resolved)
        assertFalse(resolved.exists())
    }

    @Test
    fun safeName_replacesPathAndIdSeparators() {
        assertEquals("JELLYFIN__a_b_c", DownloadFiles.safeName("JELLYFIN::a/b\\c"))
    }
}
