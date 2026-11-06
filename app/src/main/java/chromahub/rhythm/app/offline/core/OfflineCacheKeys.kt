// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Stable cache keys for Subsonic streams.
 *
 * Rhythm signs every `stream.view` URL with a fresh random salt, so the player's default cache key
 * (the URL) never repeated and the 150 MB audio cache never hit. The bytes of a stream depend on
 * the song, the server, the `format` and the `maxBitRate`, nothing else, so the key is built from
 * exactly those. The same key is derivable from an *unsigned* URL, which is how an offline play
 * finds the bytes it cached earlier without any credentials.
 */
object OfflineCacheKeys {

    private const val PREFIX = "rhythm-subsonic"
    private const val SEP = "|"
    private val STREAM_SUFFIXES = listOf("/rest/stream.view", "/rest/stream")

    /** The pieces a key carries; [restBase] is `scheme://authority/<prefix>/rest`. */
    data class Parts(val restBase: String, val songId: String, val format: String?, val maxBitRate: String?)

    /** The cache key for a Subsonic stream URL (signed or not), or null for any other URI. */
    fun keyFor(uri: String): String? = parse(uri)?.let(::keyOf)

    fun keyOf(p: Parts): String =
        listOf(PREFIX, p.songId, p.format ?: "-", p.maxBitRate ?: "0", p.restBase).joinToString(SEP)

    /** Prefix shared by every key of [songId], whatever its format or bit rate. */
    fun songPrefix(songId: String): String = PREFIX + SEP + songId + SEP

    /** Inverse of [keyOf]; null for a key this class did not make. */
    fun partsOfKey(key: String): Parts? {
        val f = key.split(SEP, limit = 5)
        if (f.size != 5 || f[0] != PREFIX) return null
        return Parts(restBase = f[4], songId = f[1], format = f[2].takeIf { it != "-" }, maxBitRate = f[3].takeIf { it != "0" })
    }

    /** An unsigned URL that [keyFor] maps back to the same key: enough to ask the cache, never the server. */
    fun unsignedUrl(p: Parts): String {
        val query = buildList {
            add("id=" + enc(p.songId))
            p.format?.let { add("format=" + enc(it)) }
            p.maxBitRate?.let { add("maxBitRate=" + enc(it)) }
        }.joinToString("&")
        return p.restBase + "/stream.view?" + query
    }

    /** The raw (untranscoded) stream of [songId] on [restBase]. */
    fun rawParts(restBase: String, songId: String) = Parts(restBase, songId, format = "raw", maxBitRate = null)

    fun parse(uri: String): Parts? {
        val parsed = try {
            URI(uri)
        } catch (_: Exception) {
            return null
        }
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val path = parsed.rawPath ?: return null
        val suffix = STREAM_SUFFIXES.firstOrNull { path.endsWith(it) } ?: return null
        val query = parsed.rawQuery ?: return null
        val params = query.split('&').filter { it.isNotEmpty() }.map {
            val eq = it.indexOf('=')
            if (eq < 0) dec(it) to "" else dec(it.substring(0, eq)) to dec(it.substring(eq + 1))
        }
        val id = params.lastOrNull { it.first == "id" }?.second?.takeIf { it.isNotBlank() } ?: return null
        val authority = parsed.rawAuthority ?: return null
        // Credentials in the authority (user:pass@host) are not part of what identifies the bytes.
        val host = authority.substringAfterLast('@')
        return Parts(
            restBase = "$scheme://$host${path.removeSuffix(suffix)}/rest",
            songId = id,
            format = params.lastOrNull { it.first == "format" }?.second?.takeIf { it.isNotBlank() },
            maxBitRate = params.lastOrNull { it.first == "maxBitRate" }?.second?.takeIf { it.isNotBlank() && it != "0" },
        )
    }

    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
