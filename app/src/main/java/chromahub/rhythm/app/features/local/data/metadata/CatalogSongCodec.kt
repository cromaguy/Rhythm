// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.net.Uri
import chromahub.rhythm.app.shared.data.model.Song
import org.json.JSONObject

object CatalogSongCodec {
    fun encode(song: Song): String = JSONObject().apply {
        put("id", song.id); put("uri", song.uri.toString()); put("title", song.title); put("artist", song.artist)
        put("album", song.album); put("albumId", song.albumId); put("duration", song.duration)
        put("year", song.year); put("genre", song.genre); put("path", song.path)
        put("track", song.trackNumber); put("disc", song.discNumber); put("albumArtist", song.albumArtist)
        put("artwork", song.artworkUri?.toString())
    }.toString()
    fun decode(source: String): Song = JSONObject(source).let {
        Song(id = it.getString("id"), title = it.getString("title"), artist = it.getString("artist"),
            album = it.getString("album"), albumId = it.optString("albumId"), duration = it.optLong("duration"),
            uri = Uri.parse(it.getString("uri")), year = it.optInt("year"), genre = it.optString("genre").takeUnless(String::isBlank),
            path = it.optString("path").takeUnless(String::isBlank), trackNumber = it.optInt("track"), discNumber = it.optInt("disc", 1),
            albumArtist = it.optString("albumArtist").takeUnless(String::isBlank),
            artworkUri = it.optString("artwork").takeUnless(String::isBlank)?.let(Uri::parse))
    }
}
