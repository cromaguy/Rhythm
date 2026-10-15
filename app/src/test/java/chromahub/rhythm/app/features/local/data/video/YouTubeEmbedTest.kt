// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import org.junit.Assert.*
import org.junit.Test

class YouTubeEmbedTest {
    @Test fun releaseAndDebugIdentifyTheActuallyInstalledApp() {
        assertEquals("https://chromahub.rhythm.app", YouTubeEmbed.origin("chromahub.rhythm.app"))
        assertEquals("https://chromahub.rhythm.app.debug", YouTubeEmbed.origin("chromahub.rhythm.app.debug"))
    }

    @Test fun iframeAndItsHttpsRedirectsCanLoadWithoutNavigatingTheHostPage() {
        assertTrue(YouTubeEmbed.allowNavigation("https://www.youtube.com/embed/abcdefghijk", false))
        assertTrue(YouTubeEmbed.allowNavigation("https://accounts.google.com/ServiceLogin", false))
        assertFalse(YouTubeEmbed.allowNavigation("https://www.youtube.com/watch?v=abcdefghijk", true))
        assertFalse(YouTubeEmbed.allowNavigation("javascript:alert(1)", false))
        assertFalse(YouTubeEmbed.allowNavigation("intent://youtube.com", false))
        assertFalse(YouTubeEmbed.allowNavigation("file:///data/local/tmp/test", false))
        assertFalse(YouTubeEmbed.allowNavigation("http://www.youtube.com/embed/abcdefghijk", false))
    }

    @Test fun badRestoredIdentifiersCannotInjectScripts() {
        assertThrows(IllegalArgumentException::class.java) { YouTubeEmbed.page("", "chromahub.rhythm.app", "';alert(1)", 0, true) }
        assertThrows(IllegalArgumentException::class.java) { YouTubeEmbed.origin("app.example/path?q='") }
    }

    @Test fun negativeOffsetNeverCreatesAnInvalidStartParameter() {
        assertEquals("0.0 false", YouTubeEmbed.page("__START_SECONDS__ __PLAYING__", "chromahub.rhythm.app", "abcdefghijk", -5000, false))
    }
}
