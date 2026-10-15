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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import chromahub.rhythm.app.features.local.data.metadata.readBytesBounded

import android.content.ContentUris
import androidx.core.net.toUri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import chromahub.rhythm.app.infrastructure.provider.RhythmAlbumArtProvider

/**
 * On-demand Coil Fetcher that decodes embedded album art and folder covers directly
 * from audio file URIs or virtual [RhythmAlbumArtProvider] URIs on background IO threads
 * without requiring ahead-of-time batch extraction or loose file storage.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) { extraction.withPermit {
        val directory = File(context.cacheDir, "embedded-artwork-v2").apply { mkdirs() }
        val filePath = uri.getQueryParameter("songFile")?.let(::File) ?: uri.path?.takeIf { uri.scheme == "file" }?.let(::File)
        val signature = if (filePath != null) "${filePath.lastModified()}:${filePath.length()}" else runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) "${c.getLong(0)}:${c.getLong(1)}" else "unknown"
            }
        }.getOrNull().orEmpty()
        val key = kr.co.metadata.crawler.CatalogFileStore.hash("$uri:$signature:${System.currentTimeMillis() / (2 * 3600_000L)}")
        val encoded = File(directory, "$key.bin")
        if (encoded.isFile && encoded.length() > 0) {
            encoded.setLastModified(System.currentTimeMillis())
            return@withPermit fileResult(encoded)
        }
        fetchUncached(encoded).also {
            // An encoded cache avoids reopening audio metadata on every thumbnail-size change.
            val files = directory.listFiles { f -> f.extension == "bin" }.orEmpty()
            var bytes = files.sumOf { f -> f.length() }
            if (bytes > 32L * 1024 * 1024) for (old in files.sortedBy { f -> f.lastModified() }) {
                if (old == encoded) continue
                val length = old.length(); if (old.delete()) bytes -= length
                if (bytes <= 32L * 1024 * 1024) break
            }
        }
    } }

    private fun fileResult(file: File) = SourceResult(ImageSource(android.util.AtomicFile(file).openRead().source().buffer(), context), null, DataSource.DISK)
    private fun result(bytes: ByteArray, target: File): SourceResult {
        require(bytes.size <= 32 * 1024 * 1024)
        if (bytes.size <= 8 * 1024 * 1024) {
            val atomic = android.util.AtomicFile(target); val output = atomic.startWrite()
            try { output.write(bytes); atomic.finishWrite(output) } catch (error: Exception) { atomic.failWrite(output); throw error }
            return fileResult(target)
        }
        return SourceResult(ImageSource(ByteArrayInputStream(bytes).source().buffer(), context), null, DataSource.DISK)
    }

    private fun fetchUncached(encoded: File): FetchResult? {
        if (uri.authority == RhythmAlbumArtProvider.PROVIDER_AUTHORITY) {
            val pathSegments = uri.pathSegments
            val type = pathSegments.firstOrNull() ?: ""
            val targetId = pathSegments.getOrNull(1) ?: ""
            val songFilePath = uri.getQueryParameter("songFile")
            val albumId = uri.getQueryParameter("albumId")

            val songUri = if (type == "song") {
                targetId.toLongOrNull()?.let { idLong ->
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idLong)
                } ?: Uri.EMPTY
            } else {
                Uri.EMPTY
            }

            val bytes = MediaUtils.extractRawEmbeddedArtworkBytes(context, songUri, songFilePath)
            if (bytes != null && bytes.isNotEmpty()) {
                return result(bytes, encoded)
            }

            // Fallback to MediaStore album art if no embedded art is found
            if (!albumId.isNullOrBlank()) {
                val albumIdLong = albumId.toLongOrNull()
                if (albumIdLong != null) {
                    val mediaStoreAlbumArtUri = ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".toUri(),
                        albumIdLong
                    )
                    try {
                        context.contentResolver.openInputStream(mediaStoreAlbumArtUri)?.use { input ->
                            val albumBytes = input.readBytesBounded(20 * 1024 * 1024)
                            if (albumBytes.isNotEmpty()) {
                                return result(albumBytes, encoded)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            return null
        }

        val bytes = MediaUtils.extractRawEmbeddedArtworkBytes(context, uri) ?: return null
        return result(bytes, encoded)
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
        private val extraction = Semaphore(1)
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "dsf", "dff", "ape", "wv", "aiff", "wma"
        )

        fun isSupportedAudioUri(uri: Uri): Boolean {
            val scheme = uri.scheme
            if (scheme == "content") {
                val auth = uri.authority
                if (auth == RhythmAlbumArtProvider.PROVIDER_AUTHORITY) {
                    return true
                }
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
        return keyFromUrlString(data.toString())
    }

    companion object {
        fun keyFromUrlString(url: String): String? {
            val httpUrl = url.toHttpUrlOrNull() ?: return null
            val scheme = httpUrl.scheme
            if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
                return null
            }
            val host = httpUrl.host
            val path = httpUrl.encodedPath

            // Subsonic cover art: /rest/getCoverArt or /rest/getCoverArt.view
            if (path.contains("getCoverArt", ignoreCase = true)) {
                val id = httpUrl.queryParameter("id") ?: return null
                val size = httpUrl.queryParameter("size") ?: "500"
                return "streaming_subsonic_${host}_${id}_${size}"
            }

            // Jellyfin Item Primary image: /Items/{id}/Images/...
            if (path.contains("/Images/", ignoreCase = true)) {
                val maxWidth = httpUrl.queryParameter("maxWidth") ?: "500"
                return "streaming_jellyfin_${host}_${path}_${maxWidth}"
            }

            return null
        }
    }
}

/**
 * Canonical keyer for streaming service artwork String URLs (Subsonic & Jellyfin).
 */
class StreamingArtworkStringKeyer : Keyer<String> {
    override fun key(data: String, options: Options): String? {
        return StreamingArtworkKeyer.keyFromUrlString(data)
    }
}
