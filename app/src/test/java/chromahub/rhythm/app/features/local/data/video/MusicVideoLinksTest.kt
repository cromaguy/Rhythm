// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import org.junit.Assert.*
import org.junit.Test

class MusicVideoLinksTest {
    @Test fun `official link forms normalize to validated embed IDs`() {
        val id = "AbcDef123_-"
        listOf("https://youtu.be/$id?t=3", "https://www.youtube.com/watch?v=$id&list=abc", "https://music.youtube.com/watch?v=$id", "https://youtube.com/shorts/$id").forEach { assertEquals(id, MusicVideoLinks.youtubeId(it)) }
    }
    @Test fun `fake YouTube hosts and HTML payloads cannot enter an embed`() {
        listOf("https://youtube.com.evil.test/watch?v=AbcDef123_-", "javascript:alert(1)", "file:///tmp/test", "https://youtube.com/watch?v=%3Cscript%3E", "https://youtu.be/short").forEach { assertNull(MusicVideoLinks.youtubeId(it)) }
    }
    @Test fun `WebM container is used only for supported native codecs`() {
        assertEquals("webm", MusicVideoLinks.muxerFormat("video/x-vnd.on2.vp9").second)
        assertEquals("webm", MusicVideoLinks.muxerFormat("video/x-vnd.on2.vp8").second)
        assertEquals("mp4", MusicVideoLinks.muxerFormat("video/avc").second)
    }
}
