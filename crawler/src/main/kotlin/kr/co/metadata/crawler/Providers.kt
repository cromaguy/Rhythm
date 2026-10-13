// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

interface MusicProvider {
    val provider: Provider
    suspend fun artist(id: String): ArtistMetadata?
    suspend fun album(id: String): AlbumMetadata?
    suspend fun track(id: String): TrackMetadata?
}

/** A polite, bounded HTTP client. It does not log in, bypass challenges, or access playback endpoints. */
class PageClient(
    private val minInterval: Duration = Duration.ofMillis(1_200),
    private val maxBodyBytes: Long = 5L * 1024 * 1024,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(15))
        .callTimeout(Duration.ofSeconds(20))
        .followRedirects(true)
        .build(),
) {
    init {
        require(!minInterval.isNegative)
        require(maxBodyBytes in 1..(20L * 1024 * 1024))
    }

    private val hostLock = ConcurrentHashMap<String, Mutex>()
    private val lastRequest = ConcurrentHashMap<String, Long>()

    suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext null
        val host = uri.host ?: return@withContext null
        if (uri.scheme !in listOf("http", "https") || uri.userInfo != null) return@withContext null
        hostLock.computeIfAbsent(host) { Mutex() }.withLock {
            val last = lastRequest[host]
            if (last != null) {
                val remaining = minInterval.toNanos() - (System.nanoTime() - last)
                if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
            }
            lastRequest[host] = System.nanoTime()
        }
        val userAgent = if (host == "m.bugs.co.kr") {
            "Mozilla/5.0 (Linux; Android 13; SM-S911N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        attemptOrNull {
            val request = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7")
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .build()
            val call = client.newCall(request)
            suspendCancellableCoroutine<String?> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCancelled) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val text = response.use {
                                if (!continuation.isActive || !it.isSuccessful) return@use null
                                val body = it.body
                                if (body.contentLength() > maxBodyBytes) return@use null
                                val source = body.source()
                                source.request(maxBodyBytes + 1)
                                if (source.buffer.size > maxBodyBytes) return@use null
                                source.readByteArray().toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                            }
                            if (continuation.isActive) continuation.resume(text)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                })
            }
        }
    }
}

/** Never turn a cancelled UI operation into an empty result or start another provider request. */
internal suspend fun <T> attemptOrNull(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

private fun String?.clean(): String? = this?.replace("\\u002F", "/", ignoreCase = true)
    ?.replace("\\u0026", "&", ignoreCase = true)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }
private fun Element?.textClean(): String? = this?.text().clean()
private fun Document.meta(property: String): String? =
    selectFirst("meta[property='$property']")?.attr("content").clean()
private fun String.toAbsoluteImage(base: String): String? = clean()?.let { raw ->
    when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> runCatching { URI(base).resolve(raw).toString() }.getOrNull()
        raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
        else -> raw
    }
}
private fun <T> evidence(value: T?, provider: Provider, url: String, confidence: Double = 0.85): Evidence<T>? =
    value?.let { Evidence(it, provider, url, confidence) }
private fun splitNames(text: String?): List<String> = text.clean()
    ?.split(Regex("\\s*(?:,|ㆍ|·|\\|)\\s*"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
private fun splitGenres(text: String?): List<String> = text.clean()
    ?.split(Regex("\\s*(?:,|ㆍ|·|\\|)\\s*|\\s+/\\s+"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty()

private fun Document.labelValue(label: String): String? {
    val norm = label.replace(" ", "")
    select("dt").forEach { dt ->
        val key = dt.textClean()?.replace(" ", "")
        if (key == norm) {
            val value = dt.nextElementSibling()
            return value?.selectFirst(".gubun")?.textClean() ?: value.textClean()
        }
    }
    select("tr").forEach { row ->
        val key = row.selectFirst("th, .attr")?.textClean()?.replace(" ", "")
        if (key == norm) return row.selectFirst("td, .value")?.textClean()
    }
    select("li").forEach { row ->
        val key = (row.selectFirst(".attr img")?.attr("alt")?.clean()
            ?: row.selectFirst(".attr")?.textClean())?.replace(" ", "")
        if (key == norm) return row.selectFirst(".value")?.textClean()
    }
    return null
}

private fun parseBugsState(html: String): JsonObject? {
    val marker = Regex("window\\.__INITIAL_STATE__\\s*=\\s*").find(html) ?: return null
    val rest = html.substring(marker.range.last + 1)
    val end = rest.indexOf("</script>")
    if (end < 0) return null
    return runCatching { Json.parseToJsonElement(rest.substring(0, end).trim().removeSuffix(";")).jsonObject }.getOrNull()
}
private fun JsonObject.obj(key: String): JsonObject? = this[key]?.jsonObjectOrNull()
private fun JsonElement?.jsonObjectOrNull(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
private fun JsonElement?.jsonArrayOrEmpty(): JsonArray = runCatching { this?.jsonArray ?: JsonArray(emptyList()) }.getOrDefault(JsonArray(emptyList()))
private fun JsonElement?.str(): String? = runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull().clean()
private fun JsonObject.str(key: String): String? = this[key].str()
private fun JsonObject.dataResult(type: String): JsonObject? = obj("1")?.obj("data")?.obj(type)?.obj("result")
private fun JsonObject.imagePath(kind: String): String? {
    val image = obj("image") ?: return null
    val path = image.str("path") ?: return null
    if (path.startsWith("http")) return path
    return "https://image.bugsm.co.kr/$kind/images/200/${path.trimStart('/')}"
}

abstract class BugsProvider(
    final override val provider: Provider,
    private val base: String,
    protected val http: PageClient,
) : MusicProvider {
    private fun url(type: String, id: String) = "$base/$type/${URLEncoder.encode(id, Charsets.UTF_8.name())}"

    override suspend fun artist(id: String): ArtistMetadata? {
        val page = url("artist", id)
        val html = http.get(page) ?: return null
        val doc = Jsoup.parse(html, page)
        val state = parseBugsState(html)
        val result = state?.dataResult("artist")
        val info = doc.labelValue("소개") ?: doc.selectFirst(".artistIntroduction, .artist_intro, .artistIntroduce, .artist_info .desc, .artistProfile")?.textClean()
        val genres = result?.get("genres").jsonArrayOrEmpty().mapNotNull { it.jsonObjectOrNull()?.str("svc_nm") }
            .ifEmpty { doc.labelValue("장르")?.split(Regex("[,ㆍ·|]"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty() }
        val startDate = doc.labelValue("데뷔") ?: result?.str("act_start_ymd")
        val image = (doc.meta("og:image") ?: result?.imagePath("artist"))?.toAbsoluteImage(page)
        val name = result?.str("artist_nm") ?: doc.meta("og:title")?.substringBefore(" - ")
        if (name == null && image == null && info == null) return null
        return ArtistMetadata(
            id = result?.str("artist_id") ?: id,
            name = evidence(name, provider, page, 0.98),
            description = evidence(info, provider, page, 0.75),
            profileImageUrl = evidence(image, provider, page, 0.95),
            debutDate = evidence(startDate, provider, page, if (doc.labelValue("데뷔") != null) 0.98 else 0.9),
            genres = evidence(genres.takeIf { it.isNotEmpty() }, provider, page, 0.9),
            sourceUrl = page,
        )
    }

    override suspend fun album(id: String): AlbumMetadata? {
        val page = url("album", id)
        val html = http.get(page) ?: return null
        val doc = Jsoup.parse(html, page)
        val state = parseBugsState(html)
        val result = state?.dataResult("album")
        val title = result?.str("title") ?: doc.meta("og:title")?.substringBefore(" / ")?.substringBefore(" - ")
        val artists = result?.get("artists").jsonArrayOrEmpty().mapNotNull { it.jsonObjectOrNull()?.str("artist_nm") }
            .ifEmpty { splitNames(doc.meta("og:title")?.substringAfter(" / ", "")) }
        val release = result?.str("release_ymd") ?: doc.labelValue("발매일")
            ?: Regex("발매일\\s*([^·.]+(?:\\.[0-9]{1,2}){1,2})").find(doc.meta("og:description").orEmpty())?.groupValues?.getOrNull(1)
        val genres = result?.get("styles").jsonArrayOrEmpty().mapNotNull { style ->
            style.jsonObjectOrNull()?.str("style_nm") ?: style.jsonObjectOrNull()?.str("svc_nm")
        }
            .ifEmpty { doc.labelValue("장르")?.split(Regex("[,ㆍ·|]"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty() }
        val image = (doc.meta("og:image") ?: result?.imagePath("album"))?.toAbsoluteImage(page)
        if (title == null && image == null) return null
        return AlbumMetadata(
            id = result?.str("album_id") ?: id,
            title = evidence(title, provider, page, 0.98),
            artists = evidence(artists.takeIf { it.isNotEmpty() }, provider, page, 0.95),
            imageUrl = evidence(image, provider, page, 0.97),
            releaseDate = evidence(release, provider, page, 0.97),
            genres = evidence(genres.takeIf { it.isNotEmpty() }, provider, page, 0.92),
            sourceUrl = page,
        )
    }

    override suspend fun track(id: String): TrackMetadata? {
        val page = url("track", id)
        val html = http.get(page) ?: return null
        val doc = Jsoup.parse(html, page)
        val state = parseBugsState(html)
        val result = state?.dataResult("track")
        val title = result?.str("track_title") ?: doc.meta("og:title")?.substringBefore(" / ")?.substringBefore(" - ")
        val artists = result?.get("artists").jsonArrayOrEmpty().mapNotNull { it.jsonObjectOrNull()?.str("artist_nm") }
        val album = result?.obj("album")
        val lyrics = runCatching { result?.obj("lyrics")?.get("normal")?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?.replace("\\r\\n", "\n")?.replace("\\n", "\n")?.replace("\\r", "\n")?.trim()?.takeIf { it.isNotBlank() }
        // Bugs may only expose artist genres on a track page; this is marked as lower confidence.
        val genres = result?.get("artists").jsonArrayOrEmpty().flatMap { artist ->
            artist.jsonObjectOrNull()?.get("genres").jsonArrayOrEmpty().mapNotNull { it.jsonObjectOrNull()?.str("svc_nm") }
        }.distinct()
        val image = (doc.meta("og:image") ?: album?.imagePath("album"))?.toAbsoluteImage(page)
        if (title == null && lyrics == null) return null
        return TrackMetadata(
            id = result?.str("track_id") ?: id,
            title = evidence(title, provider, page, 0.98),
            artists = evidence(artists.takeIf { it.isNotEmpty() }, provider, page, 0.95),
            lyrics = evidence(lyrics, provider, page, 0.98),
            imageUrl = evidence(image, provider, page, 0.92),
            releaseDate = evidence(result?.str("release_ymd") ?: album?.str("release_ymd"), provider, page, 0.9),
            genres = evidence(genres.takeIf { it.isNotEmpty() }, provider, page, 0.65),
            albumTitle = evidence(album?.str("title"), provider, page, 0.96),
            sourceUrl = page,
        )
    }
}

class BugsMobileProvider(http: PageClient = PageClient()) : BugsProvider(Provider.BUGS_MOBILE, "https://m.bugs.co.kr", http)
class BugsPcProvider(http: PageClient = PageClient()) : BugsProvider(Provider.BUGS_PC, "https://music.bugs.co.kr", http)

/** Genie pages use the public detail routes; exact fields can vary with region/login/site markup. */
class GenieProvider(private val http: PageClient = PageClient()) : MusicProvider {
    override val provider = Provider.GENIE
    private suspend fun load(url: String): Pair<String, Document>? {
        val html = http.get(url) ?: return null
        return html to Jsoup.parse(html, url)
    }
    override suspend fun artist(id: String): ArtistMetadata? {
        val url = "https://www.genie.co.kr/detail/artistInfo?xxnm=${URLEncoder.encode(id, Charsets.UTF_8.name())}"
        val (_, doc) = load(url) ?: return null
        val name = doc.meta("og:title")?.substringBefore(" - genie")
            ?: doc.selectFirst(".artist-name, .artist_name, h2, h3")?.textClean()
        val image = (doc.meta("og:image") ?: doc.selectFirst(".artist-img img, .artistImage img")?.absUrl("src"))?.toAbsoluteImage(url)
        val debut = doc.labelValue("데뷔")
        val genres = splitGenres(doc.labelValue("장르/스타일") ?: doc.labelValue("장르"))
        val intro = doc.labelValue("소개") ?: doc.selectFirst(".artist-intro, .artist_desc, .desc_area")?.textClean()
        if (name == null && image == null) return null
        return ArtistMetadata(id, evidence(name, provider, url), evidence(intro, provider, url, 0.7), evidence(image, provider, url), evidence(debut, provider, url), evidence(genres.takeIf { it.isNotEmpty() }, provider, url), url)
    }
    override suspend fun album(id: String): AlbumMetadata? {
        val url = "https://www.genie.co.kr/detail/albumInfo?axnm=${URLEncoder.encode(id, Charsets.UTF_8.name())}"
        val (_, doc) = load(url) ?: return null
        val title = doc.meta("og:title")?.substringBefore(" / ")?.substringBefore(" - genie")
            ?: doc.selectFirst(".album-title, .album-name, h2")?.textClean()
        val artist = doc.labelValue("아티스트") ?: doc.selectFirst(".artist-name, .artist_name, .artist")?.textClean()
        val image = (doc.selectFirst(".album-img img, .albumImage img")?.absUrl("src") ?: doc.meta("og:image"))?.toAbsoluteImage(url)
        val release = doc.labelValue("발매일")
        val genre = splitGenres(doc.labelValue("장르/스타일") ?: doc.labelValue("장르"))
        if (title == null && image == null) return null
        return AlbumMetadata(id, evidence(title, provider, url), evidence(splitNames(artist).ifEmpty { emptyList() }.takeIf { it.isNotEmpty() }, provider, url), evidence(image, provider, url), evidence(release, provider, url), evidence(genre.takeIf { it.isNotEmpty() }, provider, url), url)
    }
    override suspend fun track(id: String): TrackMetadata? {
        val url = "https://www.genie.co.kr/detail/songInfo?xgnm=${URLEncoder.encode(id, Charsets.UTF_8.name())}"
        val (_, doc) = load(url) ?: return null
        val title = doc.selectFirst(".song-title, .song_name, .title")?.textClean() ?: doc.meta("og:title")?.substringBefore(" - ")
        val artist = doc.selectFirst(".artist-name, .artist_name, .artist")?.textClean()
        val release = doc.labelValue("발매일")
        val genre = splitGenres(doc.labelValue("장르/스타일") ?: doc.labelValue("장르"))
        val album = doc.labelValue("앨범")
        val lyrics = doc.selectFirst(".lyrics, .lyric, #lyrics, .song-lyrics")?.wholeText()?.trim()?.takeIf(String::isNotBlank)
        val image = (doc.meta("og:image") ?: doc.selectFirst(".album-img img")?.absUrl("src"))?.toAbsoluteImage(url)
        if (title == null && lyrics == null) return null
        return TrackMetadata(id, evidence(title, provider, url), evidence(splitNames(artist).takeIf { it.isNotEmpty() }, provider, url), evidence(lyrics, provider, url), evidence(image, provider, url), evidence(release, provider, url), evidence(genre.takeIf { it.isNotEmpty() }, provider, url), evidence(album, provider, url), url)
    }
}

/** Melon detail pages include useful artist debut, track release date/genre and lyrics when visible. */
class MelonProvider(private val http: PageClient = PageClient()) : MusicProvider {
    override val provider = Provider.MELON
    private suspend fun load(url: String): Document? = http.get(url)?.let { Jsoup.parse(it, url) }
    override suspend fun artist(id: String): ArtistMetadata? {
        val url = "https://www.melon.com/artist/detail.htm?artistId=${URLEncoder.encode(id, Charsets.UTF_8.name())}"
        val doc = load(url) ?: return null
        val name = doc.meta("og:title")
            ?: doc.selectFirst(".wrap_atist_info .artist_name, .wrap_atist_info .title, .artist_name")?.textClean()
        val image = (doc.meta("og:image") ?: doc.selectFirst(".wrap_atist_info img")?.absUrl("src"))?.toAbsoluteImage(url)
        val debut = doc.labelValue("데뷔")
        val genres = doc.labelValue("장르")?.split(Regex("[,ㆍ·|]"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
        val intro = doc.selectFirst(".section_atistinfo02 #d_artist_intro, .artist_introduction, .wrap_atist_info .info")?.textClean()
        if (name == null && image == null) return null
        return ArtistMetadata(id, evidence(name, provider, url, 0.96), evidence(intro, provider, url, 0.72), evidence(image, provider, url, 0.95), evidence(debut, provider, url, 0.96), evidence(genres.takeIf { it.isNotEmpty() }, provider, url, 0.85), url)
    }
    override suspend fun album(id: String): AlbumMetadata? {
        val url = "https://www.melon.com/album/detail.htm?albumId=${URLEncoder.encode(id, Charsets.UTF_8.name())}"
        val doc = load(url) ?: return null
        val title = doc.selectFirst(".album_name, .song_name, .wrap_info .title")?.textClean() ?: doc.meta("og:title")?.substringBefore(" - ")
        val artist = doc.selectFirst(".artist_name")?.textClean()
        val image = (doc.meta("og:image") ?: doc.selectFirst(".thumb img")?.absUrl("src"))?.toAbsoluteImage(url)
        val release = doc.labelValue("발매일")
        val genres = doc.labelValue("장르")?.split(Regex("[,ㆍ·|]"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
        if (title == null && image == null) return null
        return AlbumMetadata(id, evidence(title, provider, url, 0.9), evidence(splitNames(artist).takeIf { it.isNotEmpty() }, provider, url), evidence(image, provider, url), evidence(release, provider, url), evidence(genres.takeIf { it.isNotEmpty() }, provider, url), url)
    }
    override suspend fun track(id: String): TrackMetadata? {
        val url = "https://www.melon.com/song/detail.htm?songId=${URLEncoder.encode(id, Charsets.UTF_8.name())}"
        val doc = load(url) ?: return null
        val title = doc.selectFirst(".song_name")?.ownText()?.clean() ?: doc.selectFirst(".song_name")?.textClean() ?: doc.meta("og:title")?.substringBefore(" - ")
        val artist = doc.selectFirst(".artist a.artist_name, .entry .artist_name, .artist_name")?.textClean()
        val image = (doc.meta("og:image") ?: doc.selectFirst(".wrap_album_info img, .bg_album_frame img")?.absUrl("src"))?.toAbsoluteImage(url)
        val release = doc.labelValue("발매일")
        val genre = doc.labelValue("장르")?.split(Regex("[,ㆍ·|]"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
        val album = doc.selectFirst(".wrap_info .meta a, .section_info .meta a")?.textClean() ?: doc.labelValue("앨범")
        val lyrics = doc.selectFirst(".section_lyric .lyric, .section_lyric .lyric_area, .lyric")?.wholeText()?.trim()?.takeIf(String::isNotBlank)
        if (title == null && lyrics == null) return null
        return TrackMetadata(id, evidence(title, provider, url, 0.96), evidence(splitNames(artist).takeIf { it.isNotEmpty() }, provider, url), evidence(lyrics, provider, url, 0.9), evidence(image, provider, url, 0.95), evidence(release, provider, url, 0.95), evidence(genre.takeIf { it.isNotEmpty() }, provider, url, 0.92), evidence(album, provider, url), url)
    }
}

/** Map IDs independently: provider catalog IDs are not interchangeable across services. */
data class MusicLookup(val idsByProvider: Map<Provider, String>)

class MusicMetadataRepository(
    private val providers: List<MusicProvider> = listOf(BugsMobileProvider(), BugsPcProvider(), GenieProvider(), MelonProvider()),
) {
    private suspend fun <T> collect(idByProvider: Map<Provider, String>, load: suspend (MusicProvider, String) -> T?): List<T> =
        providers.mapNotNull { p -> idByProvider[p.provider]?.let { id -> attemptOrNull { load(p, id) } } }

    suspend fun artist(ids: MusicLookup): ArtistMetadata? =
        MetadataMerger.artists(collect(ids.idsByProvider) { p, id -> p.artist(id) }).takeIf { it.name != null || it.profileImageUrl != null }
    suspend fun album(ids: MusicLookup): AlbumMetadata? =
        MetadataMerger.albums(collect(ids.idsByProvider) { p, id -> p.album(id) }).takeIf { it.title != null || it.imageUrl != null }
    suspend fun track(ids: MusicLookup): TrackMetadata? =
        MetadataMerger.tracks(collect(ids.idsByProvider) { p, id -> p.track(id) }).takeIf { it.title != null || it.lyrics != null }
}
