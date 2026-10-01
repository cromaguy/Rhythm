/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.Reader
import java.io.Writer

/**
 * Turns signed provider URLs (stream, cover art) into short, credential-free references for the
 * catalog cache, and back into URLs signed with the current credentials.
 */
interface CatalogUrlRefs {
    /** A reference to store instead of [url], or null to store the URL itself. */
    fun toRef(url: String): String?

    /** Resolves references for one load; the returned function gives null for unknown ones. */
    fun resolver(): (String) -> String?

    object None : CatalogUrlRefs {
        override fun toRef(url: String): String? = null
        override fun resolver(): (String) -> String? = { null }
    }
}

/**
 * Reads and writes the catalog cache in a compact form. Songs, which are almost all of it, use
 * short keys, skip default values and store URL references instead of signed URLs (those
 * embed auth tokens and made up most of the file); cover URLs of albums and artists are stored
 * as references too. Caches written in the old, verbose format still load; they are rewritten
 * compactly on the next save.
 */
internal class CatalogCacheCodec(private val urlRefs: CatalogUrlRefs = CatalogUrlRefs.None) {

    fun write(out: Writer, cache: StreamingCatalogCache) {
        val compactCache = cache.copy(
            albums = cache.albums.map { it.copy(artworkUri = compactUrl(it.artworkUri)) },
            artists = cache.artists.map { it.copy(artworkUri = compactUrl(it.artworkUri)) }
        )
        gson(expand = null).toJson(compactCache, out)
    }

    fun read(reader: Reader): StreamingCatalogCache? {
        val resolve = urlRefs.resolver()
        val expand: (String?) -> String? = { value ->
            if (value != null && value.startsWith(REF_PREFIX)) resolve(value.substring(REF_PREFIX.length)) else value
        }
        val cache = gson(expand).fromJson(reader, StreamingCatalogCache::class.java) ?: return null
        // Gson leaves fields missing from the file null despite their Kotlin types.
        return cache.copy(
            songs = orEmpty(cache.songs),
            albums = orEmpty(cache.albums).map { it.copy(artworkUri = expand(it.artworkUri)) },
            artists = orEmpty(cache.artists).map { it.copy(artworkUri = expand(it.artworkUri)) },
            playlists = orEmpty(cache.playlists),
            likedSongIds = orEmpty(cache.likedSongIds)
        )
    }

    fun writeFile(file: File, cache: StreamingCatalogCache) {
        file.bufferedWriter().use { write(it, cache) }
    }

    fun readFile(file: File): StreamingCatalogCache? = file.bufferedReader().use { read(it) }

    private fun <T> orEmpty(list: List<T>?): List<T> = list ?: emptyList()

    private fun compactUrl(url: String?): String? = url?.let { urlRefs.toRef(it)?.let { ref -> REF_PREFIX + ref } ?: it }

    private fun gson(expand: ((String?) -> String?)?): Gson = GsonBuilder()
        // No HTML-safe escaping: it turns every '=' and '&' in stored URLs into a 6-char escape.
        .disableHtmlEscaping()
        .registerTypeAdapter(StreamingSong::class.java, SongAdapter(expand).nullSafe())
        .create()

    /**
     * [StreamingSong] with short keys. Reads both the short keys and the property names the
     * reflective format used, so old caches load. [expand] is null when writing.
     */
    private inner class SongAdapter(private val expand: ((String?) -> String?)?) : TypeAdapter<StreamingSong>() {

        override fun write(out: JsonWriter, song: StreamingSong) {
            out.beginObject()
            out.name("i").value(song.id)
            out.name("t").value(song.title)
            out.name("a").value(song.artist)
            out.name("l").value(song.album)
            out.name("d").value(song.duration)
            out.name("s").value(song.sourceType.name)
            compactUrl(song.artworkUri)?.let { out.name("w").value(it) }
            compactUrl(song.streamingUrl)?.let { out.name("u").value(it) }
            song.previewUrl?.let { out.name("pv").value(it) }
            if (song.isExplicit) out.name("e").value(true)
            song.popularity?.let { out.name("po").value(it) }
            song.releaseDate?.let { out.name("rd").value(it) }
            if (!song.isPlayable) out.name("pl").value(false)
            // The provider id is usually the part of the id after "service::"; store it only if
            // not ("" for none).
            val derivedExternalId = song.id.substringAfter("::", "").ifEmpty { null }
            if (song.externalId != derivedExternalId) out.name("x").value(song.externalId.orEmpty())
            song.albumId?.let { out.name("b").value(it) }
            song.albumArtist?.let { out.name("aa").value(it) }
            song.isrc?.let { out.name("is").value(it) }
            if (song.isFavorite) out.name("f").value(true)
            song.trackNumber?.let { out.name("n").value(it) }
            song.year?.let { out.name("y").value(it) }
            song.genre?.let { out.name("g").value(it) }
            song.bitrate?.let { out.name("br").value(it) }
            song.sampleRate?.let { out.name("sr").value(it) }
            song.channels?.let { out.name("ch").value(it) }
            song.codec?.let { out.name("c").value(it) }
            out.endObject()
        }

        override fun read(reader: JsonReader): StreamingSong {
            var id = ""; var title = ""; var artist = ""; var album = ""; var duration = 0L
            var artworkUri: String? = null; var sourceType: SourceType? = null
            var streamingUrl: String? = null; var previewUrl: String? = null
            var isExplicit = false; var popularity: Int? = null; var releaseDate: String? = null
            var isPlayable = true; var externalId: String? = null; var hasExternalId = false
            var albumId: String? = null; var albumArtist: String? = null; var isrc: String? = null
            var isFavorite = false; var trackNumber: Int? = null; var year: Int? = null
            var genre: String? = null; var bitrate: Int? = null; var sampleRate: Int? = null
            var channels: Int? = null; var codec: String? = null; var compact = false

            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }
                when (name) {
                    "i" -> { id = reader.nextString(); compact = true }
                    "id" -> id = reader.nextString()
                    "t", "title" -> title = reader.nextString()
                    "a", "artist" -> artist = reader.nextString()
                    "l", "album" -> album = reader.nextString()
                    "d", "duration" -> duration = reader.nextLong()
                    "s", "sourceType" -> sourceType = runCatching { SourceType.valueOf(reader.nextString()) }.getOrNull()
                    "w", "artworkUri" -> artworkUri = reader.nextString()
                    "u", "streamingUrl" -> streamingUrl = reader.nextString()
                    "pv", "previewUrl" -> previewUrl = reader.nextString()
                    "e", "isExplicit" -> isExplicit = reader.nextBoolean()
                    "po", "popularity" -> popularity = reader.nextInt()
                    "rd", "releaseDate" -> releaseDate = reader.nextString()
                    "pl", "isPlayable" -> isPlayable = reader.nextBoolean()
                    "x", "externalId" -> { externalId = reader.nextString().ifEmpty { null }; hasExternalId = true }
                    "b", "albumId" -> albumId = reader.nextString()
                    "aa", "albumArtist" -> albumArtist = reader.nextString()
                    "is", "isrc" -> isrc = reader.nextString()
                    "f", "isFavorite" -> isFavorite = reader.nextBoolean()
                    "n", "trackNumber" -> trackNumber = reader.nextInt()
                    "y", "year" -> year = reader.nextInt()
                    "g", "genre" -> genre = reader.nextString()
                    "br", "bitrate" -> bitrate = reader.nextInt()
                    "sr", "sampleRate" -> sampleRate = reader.nextInt()
                    "ch", "channels" -> channels = reader.nextInt()
                    "c", "codec" -> codec = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            if (compact && !hasExternalId) {
                externalId = id.substringAfter("::", "").ifEmpty { null }
            }
            return StreamingSong(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                artworkUri = if (expand != null) expand(artworkUri) else artworkUri,
                sourceType = sourceType ?: SourceType.UNKNOWN,
                streamingUrl = if (expand != null) expand(streamingUrl) else streamingUrl,
                previewUrl = previewUrl,
                isExplicit = isExplicit,
                popularity = popularity,
                releaseDate = releaseDate,
                isPlayable = isPlayable,
                externalId = externalId,
                albumId = albumId,
                albumArtist = albumArtist,
                isrc = isrc,
                isFavorite = isFavorite,
                trackNumber = trackNumber,
                year = year,
                genre = genre,
                bitrate = bitrate,
                sampleRate = sampleRate,
                channels = channels,
                codec = codec
            )
        }
    }

    companion object {
        /** Marks a stored value as a [CatalogUrlRefs] reference rather than a URL. */
        const val REF_PREFIX = "ref:"
    }
}
