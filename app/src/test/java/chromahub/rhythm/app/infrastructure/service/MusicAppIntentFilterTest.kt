/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MusicAppIntentFilterTest {

    @Test
    fun testManifestContainsRequiredMusicAppIntentFilters() {
        // Resolve AndroidManifest.xml path across execution environments
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }

        assertTrue("AndroidManifest.xml should exist", manifestFile != null && manifestFile.exists())

        val content = manifestFile!!.readText()

        // 1. Check MainActivity music player action
        assertTrue(
            "MainActivity must declare android.intent.action.MUSIC_PLAYER",
            content.contains("""<action android:name="android.intent.action.MUSIC_PLAYER"""")
        )

        // 2. Check MainActivity category APP_MUSIC
        assertTrue(
            "MainActivity must declare android.intent.category.APP_MUSIC",
            content.contains("""<category android:name="android.intent.category.APP_MUSIC"""")
        )

        // 3. Check MainActivity MEDIA_PLAY_FROM_SEARCH for Samsung Routines & Google Assistant
        assertTrue(
            "MainActivity must declare android.media.action.MEDIA_PLAY_FROM_SEARCH",
            content.contains("""<action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH"""")
        )

        // 4. Check MediaPlaybackService MEDIA_BUTTON action for Tasker & MacroDroid media events
        assertTrue(
            "MediaPlaybackService must declare android.intent.action.MEDIA_BUTTON",
            content.contains("""<action android:name="android.intent.action.MEDIA_BUTTON"""")
        )
    }
}
