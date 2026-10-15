// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.VideoStream

class YouTubeStreamSelectionTest {
    private fun stream(height: Int, format: MediaFormat, fps: Int = 30, rate: Int = 800_000, host: String = "r1.googlevideo.com", videoOnly: Boolean = true): VideoStream {
        val itag = ItagItem(247, ItagItem.ItagType.VIDEO_ONLY, format, "${height}p", fps)
        itag.height = height; itag.width = height * 16 / 9; itag.bitrate = rate
        return VideoStream.Builder().setId("$height-$format-$fps").setContent("https://$host/video", true)
            .setIsVideoOnly(videoOnly).setMediaFormat(format).setResolution("${height}p").setItagItem(itag).build()
    }
    @Test fun prefersCompact720WebmAndNeverDownloads1080Or60fpsOrAnAudioDuplicate() {
        val selected = YouTubeVideoDownload.choose(listOf(stream(1080, MediaFormat.WEBM), stream(720, MediaFormat.WEBM, 60),
            stream(720, MediaFormat.MPEG_4), stream(720, MediaFormat.WEBM), stream(480, MediaFormat.WEBM),
            stream(720, MediaFormat.WEBM, videoOnly = false)), 240)
        assertEquals(720, selected.height); assertEquals(30, selected.fps); assertEquals(MediaFormat.WEBM, selected.format)
    }
    @Test fun avoidsOversizedStreamAndRejectsUntrustedMediaOrigin() {
        val selected = YouTubeVideoDownload.choose(listOf(stream(720, MediaFormat.WEBM, rate = 10_000_000), stream(480, MediaFormat.WEBM)), 240)
        assertEquals(480, selected.height)
        assertTrue(runCatching { YouTubeVideoDownload.choose(listOf(stream(720, MediaFormat.WEBM, host = "attacker.invalid")), 240) }.isFailure)
    }
}
