// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import org.jsoup.Jsoup
import java.net.URLEncoder

enum class EntityKind(val path: String) { ARTIST("artist"), ALBUM("album"), TRACK("track") }

data class SearchHit(
    val provider: Provider,
    val kind: EntityKind,
    val providerId: String,
    val title: String,
    val detailUrl: String,
    val artists: String? = null,
)

/** Public web-search pages only. IDs are provider-specific; never copy an ID between catalogs. */
class CatalogSearch(private val pageClient: PageClient = PageClient()) {
    suspend fun search(provider: Provider, kind: EntityKind, query: String, limit: Int = 10): List<SearchHit> {
        require(query.isNotBlank()) { "query must not be blank" }
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val url = when (provider) {
            Provider.BUGS_MOBILE, Provider.BUGS_PC -> "https://music.bugs.co.kr/search/${kind.path}?q=$q"
            Provider.GENIE -> "https://www.genie.co.kr/search/${when (kind) { EntityKind.ARTIST -> "searchArtist"; EntityKind.ALBUM -> "searchAlbum"; EntityKind.TRACK -> "searchSong" }}?query=$q"
            Provider.MELON -> "https://www.melon.com/search/${when (kind) { EntityKind.ARTIST -> "artist"; EntityKind.ALBUM -> "album"; EntityKind.TRACK -> "song" }}/index.htm?q=$q"
        }
        val html = pageClient.get(url) ?: return emptyList()
        val doc = Jsoup.parse(html, url)
        val pattern = when (provider) {
            Provider.BUGS_MOBILE, Provider.BUGS_PC -> Regex("/${kind.path}/(\\d+)")
            Provider.GENIE -> when (kind) {
                EntityKind.ARTIST -> Regex("(?:xxnm=|fnViewArtist(?:Info)?\\(['\"])(\\d+)")
                EntityKind.ALBUM -> Regex("(?:axnm=|fnViewAlbum(?:Info|Layer)?\\(['\"])(\\d+)")
                EntityKind.TRACK -> Regex("(?:xgnm=|fnViewSongInfo\\(['\"])(\\d+)")
            }
            Provider.MELON -> when (kind) {
                EntityKind.ARTIST -> Regex("(?:artistId=|goArtistDetail\\(['\"])(\\d+)")
                EntityKind.ALBUM -> Regex("(?:albumId=|goAlbumDetail\\(['\"])(\\d+)")
                EntityKind.TRACK -> Regex("(?:songId=|goSongDetail\\(['\"])(\\d+)")
            }
        }
        val hits = LinkedHashMap<String, SearchHit>()
        doc.select("a[href]").forEach { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val target = "$href ${a.attr("onclick")}"
            val id = pattern.find(target)?.groupValues?.getOrNull(1) ?: return@forEach
            val row = a.closest("tr")
            val titleNode = if (kind == EntityKind.TRACK) when (provider) {
                Provider.BUGS_MOBILE, Provider.BUGS_PC -> row?.selectFirst("p.title a")
                Provider.GENIE -> row?.selectFirst("a.title")
                Provider.MELON -> row?.selectFirst("a.fc_gray[title]")
            } else null
            val title = (titleNode?.attr("title")?.takeIf(String::isNotBlank)
                ?: titleNode?.clone()?.apply { select(".icon").remove() }?.text()
                ?: a.attr("title").takeIf { it.isNotBlank() && it !in setOf("곡정보 보기", "곡정보") }
                ?: a.text()).removeSuffix(" 상세정보 페이지 이동").removeSuffix(" - 페이지 이동")
                .replace(Regex("\\s+"), " ").trim().ifBlank { return@forEach }
            if (title.length > 220 || title in setOf("바로가기", "곡정보", "곡 제목 정보 페이지")) return@forEach
            val artistNode = if (kind == EntityKind.TRACK) row?.selectFirst("p.artist, a.artist, #artistName") else null
            val artists = artistNode?.let { node ->
                node.select("a").map { it.text().trim() }.filter(String::isNotBlank).distinct()
                    .joinToString(", ").ifBlank { node.text().trim() }.takeIf(String::isNotBlank)
            }
            val detail = when (provider) {
                Provider.BUGS_MOBILE, Provider.BUGS_PC -> "https://music.bugs.co.kr/${kind.path}/$id"
                Provider.GENIE -> when (kind) {
                    EntityKind.ARTIST -> "https://www.genie.co.kr/detail/artistInfo?xxnm=$id"
                    EntityKind.ALBUM -> "https://www.genie.co.kr/detail/albumInfo?axnm=$id"
                    EntityKind.TRACK -> "https://www.genie.co.kr/detail/songInfo?xgnm=$id"
                }
                Provider.MELON -> when (kind) {
                    EntityKind.ARTIST -> "https://www.melon.com/artist/detail.htm?artistId=$id"
                    EntityKind.ALBUM -> "https://www.melon.com/album/detail.htm?albumId=$id"
                    EntityKind.TRACK -> "https://www.melon.com/song/detail.htm?songId=$id"
                }
            }
            hits.putIfAbsent(id, SearchHit(provider, kind, id, title, detail, artists))
        }
        val queryLower = query.trim().lowercase()
        return hits.values.sortedWith(compareByDescending<SearchHit> { it.title.lowercase().contains(queryLower) }).take(limit)
    }

    suspend fun searchAll(kind: EntityKind, query: String, limitPerProvider: Int = 5): List<SearchHit> =
        Provider.entries.flatMap { provider -> attemptOrNull { search(provider, kind, query, limitPerProvider) }.orEmpty() }
}
