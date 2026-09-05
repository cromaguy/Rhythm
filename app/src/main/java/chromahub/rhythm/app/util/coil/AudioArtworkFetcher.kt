/*
 * Copyright (C) 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * Copyright (C) 2026 The Gramophone authors
 *
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 The Gramophone authors <https://github.com/FoedusProgramme/Gramophone>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util.coil

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import chromahub.rhythm.app.util.MediaUtils
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream

/**
 * On-demand Coil Fetcher that decodes embedded album art and folder covers directly
 * from audio file URIs on background IO threads without requiring ahead-of-time batch extraction.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = MediaUtils.extractRawEmbeddedArtworkBytes(context, uri) ?: return null
        val bufferedSource = ByteArrayInputStream(bytes).source().buffer()
        val imageSource = ImageSource(source = bufferedSource, context = context)
        return SourceResult(
            source = imageSource,
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (isSupportedAudioUri(data)) {
                return AudioArtworkFetcher(context, data, options)
            }
            return null
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "dsf", "dff", "ape", "wv", "aiff", "wma"
        )

        fun isSupportedAudioUri(uri: Uri): Boolean {
            val scheme = uri.scheme
            if (scheme == "content") {
                val auth = uri.authority
                if (auth == MediaStore.AUTHORITY) {
                    val path = uri.path.orEmpty()
                    return path.contains("/audio/media")
                }
                return false
            } else if (scheme == "file") {
                val path = uri.path ?: return false
                val ext = path.substringAfterLast('.', "").lowercase()
                return ext in AUDIO_EXTENSIONS
            }
            return false
        }
    }
}

/**
 * Keyer for AudioArtwork URIs to ensure fast memory & disk cache lookups in Coil.
 */
class AudioArtworkKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        if (AudioArtworkFetcher.isSupportedAudioUri(data)) {
            return "audio_artwork_${data}"
        }
        return null
    }
}

/**
 * Canonical keyer for streaming service artwork URIs (Subsonic & Jellyfin).
 * Strips dynamic/session auth tokens and query parameters so that Coil's memory
 * and disk caches hit reliably across app launches and network state changes.
 */
class StreamingArtworkKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        val scheme = data.scheme ?: return null
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return null
        }
        val host = data.host ?: return null
        val path = data.path ?: return null

        // Subsonic cover art: /rest/getCoverArt or /rest/getCoverArt.view
        if (path.contains("getCoverArt", ignoreCase = true)) {
            val id = data.getQueryParameter("id") ?: return null
            val size = data.getQueryParameter("size") ?: "500"
            return "streaming_subsonic_${host}_${id}_${size}"
        }

        // Jellyfin Item Primary image: /Items/{id}/Images/...
        if (path.contains("/Images/", ignoreCase = true)) {
            val maxWidth = data.getQueryParameter("maxWidth") ?: "500"
            return "streaming_jellyfin_${host}_${path}_${maxWidth}"
        }

        return null
    }
}
