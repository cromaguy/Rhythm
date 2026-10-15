// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.video

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.zip.GZIPInputStream

/** Real pinned extractor, recorded upstream requests, no live YouTube dependency. */
class YouTubeRecordedExtractionTest {
    private data class Fixture(val request: Request, val response: Response)
    @Test fun extractsUsableVideoOnlyStreamsFromRecordedCreativeCommonsResponses() {
        // NewPipeExtractor v0.26.5 CCLicensed fixture, GPL-3.0-or-later (see resources/README.md).
        val fixtures = javaClass.getResourceAsStream("/youtube-cc-fixture.json.gz")!!.use { stream ->
            GZIPInputStream(stream).reader().use { Gson().fromJson(it, Array<Fixture>::class.java).associate { f -> f.request to f.response } }
        }
        var requests = 0
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                requests++
                return fixtures[request] ?: error("Unrecorded upstream request: ${request.url()}")
            }
        })
        YoutubeParsingHelper.setConsentAccepted(false)
        YoutubeParsingHelper.resetClientVersion()
        YoutubeParsingHelper.setNumberGenerator(java.util.Random(1))
        YoutubeJavaScriptPlayerManager.clearAllCaches()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=M4gD1WSo5mA")
        assertEquals("M4gD1WSo5mA", info.id)
        val stream = YouTubeVideoDownload.choose(info.videoOnlyStreams, info.duration)
        assertTrue(stream.isVideoOnly); assertTrue(stream.height in 1..720); assertTrue(stream.fps <= 30)
        assertTrue(stream.content.startsWith("https://")); assertTrue(requests > 0)
    }
}
