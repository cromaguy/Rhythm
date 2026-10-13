// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Duration
import kotlin.test.*

class ProviderParsingTest {
    private fun fixture(html: String) = PageClient(minInterval = Duration.ZERO, client = OkHttpClient.Builder()
        .addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(html.toResponseBody("text/html; charset=utf-8".toMediaType())).build() }
        .build())

    @Test fun `Bugs SSR preserves lyric lines and provenance in editor document`() = runBlocking {
        val html = """<script>window.__INITIAL_STATE__ = {"1":{"data":{"track":{"result":{
            "track_id":"7","track_title":"테스트 곡","artists":[{"artist_nm":"가수"}],
            "lyrics":{"normal":"첫 줄\n둘째 줄"},
            "album":{"title":"앨범","release_ymd":"20261013","image":{"path":"cover.jpg"}}
        }}}}};</script>"""
        val track = assertNotNull(BugsMobileProvider(fixture(html)).track("7"))
        assertEquals("첫 줄\n둘째 줄", track.lyrics?.value)
        assertEquals(2026, track.releaseDate?.value.yearOrNull())
        val document = CatalogDocument.from(track)
        assertEquals("테스트 곡", document[CatalogField.TRACK_TITLE])
        assertEquals("앨범", document[CatalogField.TRACK_ALBUM_TITLE])
        assertTrue(document.values.all { it.sourceUrl == "https://m.bugs.co.kr/track/7" })
    }

    @Test fun `Melon parses onclick IDs and deduplicates matching links`() = runBlocking {
        val html = """<a href="#" onclick="melon.link.goSongDetail('37323516');">Love wins all</a>
            <a href="#" onclick="melon.link.goSongDetail('37323516');">duplicate</a>
            <a href="#" onclick="melon.link.goAlbumDetail('99');">album</a>"""
        val hits = CatalogSearch(fixture(html)).search(Provider.MELON, EntityKind.TRACK, "Love")
        assertEquals(listOf("37323516"), hits.map { it.providerId })
        assertEquals("https://www.melon.com/song/detail.htm?songId=37323516", hits.single().detailUrl)
    }

    @Test fun `Genie song search ignores artist and album IDs`() = runBlocking {
        val html = """<a href="/detail/songInfo?xgnm=123">song</a>
            <a href="/detail/artistInfo?xxnm=456">artist</a><a href="/detail/albumInfo?axnm=789">album</a>"""
        assertEquals(listOf("123"), CatalogSearch(fixture(html)).search(Provider.GENIE, EntityKind.TRACK, "song").map { it.providerId })
    }

    @Test fun `Bugs search displays the song title rather than the detail link label`() = runBlocking {
        val html = """<table><tr><td><a href="/track/123">곡정보</a></td>
            <th><p class="title"><a href="#" title="Song title">Song title</a></p></th>
            <td><p class="artist"><a href="/artist/99">Artist</a></p></td></tr></table>"""
        val hit = CatalogSearch(fixture(html)).search(Provider.BUGS_MOBILE, EntityKind.TRACK, "Song").single()
        assertEquals("Song title", hit.title)
        assertEquals("Artist", hit.artists)
    }

    @Test fun `Genie detail onclick uses the title and artist from its search row`() = runBlocking {
        val html = """<table><tr><td><a href="#" onclick="fnViewSongInfo('123');">곡 제목 정보 페이지</a></td>
            <td><a class="title" href="#"><span class="icon">TITLE</span>Song</a>
            <a class="artist" href="#" onclick="fnViewArtist('99');">Artist</a></td></tr></table>"""
        val hit = CatalogSearch(fixture(html)).search(Provider.GENIE, EntityKind.TRACK, "Song").single()
        assertEquals("123", hit.providerId)
        assertEquals("Song", hit.title)
        assertEquals("Artist", hit.artists)
    }

    @Test fun `Melon and Genie keep line breaks in public lyrics`() = runBlocking {
        val html = """<div class="song_name">Title</div><div class="lyric">one<br>two</div>"""
        assertEquals("one\ntwo", MelonProvider(fixture(html)).track("1")?.lyrics?.value)
        assertEquals("one\ntwo", GenieProvider(fixture(html)).track("1")?.lyrics?.value)
    }

    @Test fun `album imports do not replace the track title`() {
        val document = CatalogDocument.from(AlbumMetadata(title = Evidence("Album", Provider.MELON, "https://example.com", .9)))
        assertNull(document[CatalogField.TRACK_TITLE])
        assertEquals("Album", document[CatalogField.ALBUM_TITLE])
    }
}
