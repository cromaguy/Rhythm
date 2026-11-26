/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.core.domain.backup.BackupSongResolver
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Stateless multi-format playlist serializer & parser supporting M3U, M3U8, PLS,
 * XSPF (XML Shareable Playlist Format), and WPL (Windows Media Playlist).
 *
 * All XML parsing enforces strict XXE protection (external DTDs and entities disabled),
 * and song resolution delegates uniformly to [BackupSongResolver].
 */
object PlaylistSerializer {

    data class ParsedTrackEntry(
        val location: String,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L
    )

    data class ParsedPlaylist(
        val name: String?,
        val entries: List<ParsedTrackEntry>
    )

    fun serializeM3u(playlist: Playlist, extended: Boolean): String {
        val sb = StringBuilder()
        if (extended) sb.append("#EXTM3U\n")
        playlist.songs.forEach { song ->
            if (extended) {
                sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
            }
            sb.append("${song.path?.takeIf { it.isNotBlank() } ?: song.uri}\n")
        }
        return sb.toString()
    }

    fun serializePls(playlist: Playlist): String {
        val sb = StringBuilder()
        sb.append("[playlist]\n")
        playlist.songs.forEachIndexed { index, song ->
            val num = index + 1
            sb.append("File$num=${song.path?.takeIf { it.isNotBlank() } ?: song.uri}\n")
            sb.append("Title$num=${song.artist} - ${song.title}\n")
            sb.append("Length$num=${song.duration / 1000}\n")
        }
        sb.append("NumberOfEntries=${playlist.songs.size}\n")
        sb.append("Version=2\n")
        return sb.toString()
    }

    fun serializeXspf(playlist: Playlist): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n")
        sb.append("  <title>${escapeXml(playlist.name)}</title>\n")
        sb.append("  <trackList>\n")
        playlist.songs.forEach { song ->
            val location = song.path?.takeIf { it.isNotBlank() } ?: song.uri.toString()
            sb.append("    <track>\n")
            sb.append("      <location>${escapeXml(location)}</location>\n")
            sb.append("      <title>${escapeXml(song.title)}</title>\n")
            sb.append("      <creator>${escapeXml(song.artist)}</creator>\n")
            if (song.album.isNotBlank()) {
                sb.append("      <album>${escapeXml(song.album)}</album>\n")
            }
            if (song.duration > 0L) {
                sb.append("      <duration>${song.duration}</duration>\n")
            }
            sb.append("    </track>\n")
        }
        sb.append("  </trackList>\n")
        sb.append("</playlist>\n")
        return sb.toString()
    }

    fun serializeWpl(playlist: Playlist): String {
        val sb = StringBuilder()
        sb.append("<?wpl version=\"1.0\"?>\n")
        sb.append("<smil>\n")
        sb.append("  <head>\n")
        sb.append("    <title>${escapeXml(playlist.name)}</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        sb.append("    <seq>\n")
        playlist.songs.forEach { song ->
            val location = song.path?.takeIf { it.isNotBlank() } ?: song.uri.toString()
            sb.append("      <media src=\"${escapeXml(location)}\" />\n")
        }
        sb.append("    </seq>\n")
        sb.append("  </body>\n")
        sb.append("</smil>\n")
        return sb.toString()
    }

    fun parseM3u(content: String): ParsedPlaylist {
        val entries = mutableListOf<ParsedTrackEntry>()
        var currentTitle = ""
        var currentDurationMs = 0L
        var playlistName: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    playlistName = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val afterColon = line.substringAfter(':')
                    val secPart = afterColon.substringBefore(',').trim().toLongOrNull() ?: 0L
                    currentDurationMs = if (secPart > 0L) secPart * 1000L else 0L
                    currentTitle = if (afterColon.contains(',')) afterColon.substringAfter(',').trim() else ""
                }
                line.startsWith("#") -> Unit
                else -> {
                    entries.add(
                        ParsedTrackEntry(
                            location = line,
                            title = currentTitle,
                            durationMs = currentDurationMs
                        )
                    )
                    currentTitle = ""
                    currentDurationMs = 0L
                }
            }
        }
        return ParsedPlaylist(name = playlistName, entries = entries)
    }

    fun parsePls(content: String): ParsedPlaylist {
        val files = HashMap<Int, String>()
        val titles = HashMap<Int, String>()
        val lengths = HashMap<Int, Long>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()
            when {
                key.startsWith("File", ignoreCase = true) ->
                    key.drop(4).toIntOrNull()?.let { files[it] = value }
                key.startsWith("Title", ignoreCase = true) ->
                    key.drop(5).toIntOrNull()?.let { titles[it] = value }
                key.startsWith("Length", ignoreCase = true) ->
                    key.drop(6).toIntOrNull()?.let { num ->
                        val sec = value.toLongOrNull() ?: -1L
                        if (sec > 0L) lengths[num] = sec * 1000L
                    }
            }
        }

        val entries = files.toSortedMap().map { (num, location) ->
            ParsedTrackEntry(
                location = location,
                title = titles[num].orEmpty(),
                durationMs = lengths[num] ?: 0L
            )
        }
        return ParsedPlaylist(name = null, entries = entries)
    }

    fun parseXspf(content: String): ParsedPlaylist {
        val doc = parseSecureXml(content)
        val root = doc.documentElement
        val playlistTitle = root.directChildText("title")
        val entries = mutableListOf<ParsedTrackEntry>()

        val tracks = doc.getElementsByTagName("track")
        for (i in 0 until tracks.length) {
            val el = tracks.item(i) as? Element ?: continue
            val location = el.directChildText("location").orEmpty()
            val title = el.directChildText("title").orEmpty()
            val creator = el.directChildText("creator").orEmpty()
            val album = el.directChildText("album").orEmpty()
            val durationMs = el.directChildText("duration")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            if (location.isNotBlank() || title.isNotBlank()) {
                entries.add(
                    ParsedTrackEntry(
                        location = location,
                        title = title,
                        artist = creator,
                        album = album,
                        durationMs = durationMs
                    )
                )
            }
        }
        return ParsedPlaylist(name = playlistTitle, entries = entries)
    }

    fun parseWpl(content: String): ParsedPlaylist {
        val doc = parseSecureXml(content)
        val titleNodes = doc.getElementsByTagName("title")
        val playlistName = if (titleNodes.length > 0) {
            titleNodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        } else null

        val entries = mutableListOf<ParsedTrackEntry>()
        val mediaNodes = doc.getElementsByTagName("media")
        for (i in 0 until mediaNodes.length) {
            val el = mediaNodes.item(i) as? Element ?: continue
            val src = el.getAttribute("src").trim()
            if (src.isNotEmpty()) {
                entries.add(ParsedTrackEntry(location = src))
            }
        }
        return ParsedPlaylist(name = playlistName, entries = entries)
    }

    fun resolveEntries(
        entries: List<ParsedTrackEntry>,
        availableSongs: List<Song>,
        extraFilePaths: Map<String, Song> = emptyMap(),
        baseDirectory: String? = null
    ): List<Song> {
        if (entries.isEmpty() || availableSongs.isEmpty()) return emptyList()
        val index = BackupSongResolver.SongIndex(availableSongs, extraFilePaths)
        val matched = mutableListOf<Song>()
        val seenIds = HashSet<String>()

        entries.forEach { entry ->
            val hit = index.resolve(
                BackupSongResolver.ResolutionQuery(
                    uri = entry.location,
                    filePath = entry.location,
                    title = entry.title,
                    artist = entry.artist,
                    album = entry.album,
                    durationMs = entry.durationMs,
                    baseDirectory = baseDirectory
                )
            )
            if (hit != null && seenIds.add(hit.id)) {
                matched.add(hit)
            }
        }
        return matched
    }

    private fun parseSecureXml(xmlContent: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xmlContent.trimStart('\uFEFF'))))
    }

    private fun Element.directChildText(tagName: String): String? {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i) as? Element ?: continue
            if (child.tagName.equals(tagName, ignoreCase = true)) {
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun escapeXml(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
