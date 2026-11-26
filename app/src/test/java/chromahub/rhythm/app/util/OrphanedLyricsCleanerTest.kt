/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OrphanedLyricsCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun pruneOrphanedAppLyricsCache_removesDeletedTracksAndKeepsActiveAndNonLyricFiles() {
        val lyricsDir = tempFolder.newFolder("lyrics")
        val keepJson = File(lyricsDir, "Daft_Punk_Aerodynamic.json").apply { writeText("{}") }
        val keepLrc = File(lyricsDir, "M83_Midnight_City.lrc").apply { writeText("[00:01.00]test") }
        val orphanTtml = File(lyricsDir, "Deleted_Artist_Removed_Track.ttml").apply { writeText("<tt/>") }
        val orphanSrt = File(lyricsDir, "Deleted_Song.srt").apply { writeText("1\n00:00:01,000") }
        val nonLyricFile = File(lyricsDir, "readme.txt").apply { writeText("keep") }

        val deleted = OrphanedLyricsCleaner.pruneOrphanedAppLyricsCache(
            lyricsDir,
            activeSongs = listOf(
                "Daft Punk" to "Aerodynamic",
                "M83" to "Midnight City"
            )
        )

        assertEquals(2, deleted)
        assertTrue(keepJson.exists())
        assertTrue(keepLrc.exists())
        assertFalse(orphanTtml.exists())
        assertFalse(orphanSrt.exists())
        assertTrue(nonLyricFile.exists())
    }

    @Test
    fun pruneOrphanedAppLyricsCache_noOpsWhenActiveSongsEmpty() {
        val lyricsDir = tempFolder.newFolder("lyrics_guard")
        val existing = File(lyricsDir, "Artist_Song.json").apply { writeText("{}") }

        assertEquals(0, OrphanedLyricsCleaner.pruneOrphanedAppLyricsCache(lyricsDir, emptyList()))
        assertTrue(existing.exists())
    }
}
