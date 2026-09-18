/*
 * Copyright (C) 2026 The Rhythm authors
 * Copyright (C) 2026 The Gramophone authors
 *
 * SPDX-FileCopyrightText: 2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 The Gramophone authors <https://github.com/FoedusProgramme/Gramophone>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.util.MediaUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.IOException

/**
 * On-demand ContentProvider that serves embedded album/song artwork to external processes
 * (such as SystemUI media notifications, Android Auto, lockscreen widgets, and Media3 MediaSession)
 * without writing duplicate files to device storage.
 *
 * URI format:
 *   content://${BuildConfig.APPLICATION_ID}.albumart/song/{id}?songFile={path}&albumId={albumId}&lossless={bool}
 *   content://${BuildConfig.APPLICATION_ID}.albumart/album/{id}?songFile={path}&lossless={bool}
 */
class RhythmAlbumArtProvider : ContentProvider() {

    companion object {
        private const val TAG = "RhythmAlbumArtProvider"

        /** Authority for the ContentProvider that serves artwork to external processes. */
        const val PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.albumart"

        /**
         * Builds a virtual `content://` URI pointing to [RhythmAlbumArtProvider] for a song.
         */
        fun buildSongUri(
            id: String,
            path: String? = null,
            albumId: String? = null,
            lossless: Boolean = false
        ): Uri {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PROVIDER_AUTHORITY)
                .appendPath("song")
                .appendPath(id)
                .apply {
                    if (!path.isNullOrBlank()) appendQueryParameter("songFile", path)
                    if (!albumId.isNullOrBlank()) appendQueryParameter("albumId", albumId)
                    appendQueryParameter("lossless", lossless.toString())
                }
                .build()
        }

        /**
         * Builds a virtual `content://` URI pointing to [RhythmAlbumArtProvider] for an album.
         */
        fun buildAlbumUri(
            albumId: String,
            songFile: String? = null,
            lossless: Boolean = false
        ): Uri {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PROVIDER_AUTHORITY)
                .appendPath("album")
                .appendPath(albumId)
                .apply {
                    if (!songFile.isNullOrBlank()) appendQueryParameter("songFile", songFile)
                    appendQueryParameter("lossless", lossless.toString())
                }
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        if (mode != "r") {
            throw IllegalArgumentException("Unsupported mode '$mode': this provider is read-only")
        }
        val afd = openAssetFile(uri, mode, signal) ?: return null
        return afd.parcelFileDescriptor
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        if (mode != "r") {
            throw IllegalArgumentException("Unsupported mode '$mode': this provider is read-only")
        }
        signal?.throwIfCanceled()
        return resolveArtworkAssetFileDescriptor(uri, signal)
    }

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        signal?.throwIfCanceled()
        return resolveArtworkAssetFileDescriptor(uri, signal)
    }

    private fun resolveArtworkAssetFileDescriptor(
        uri: Uri,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        val appContext = context?.applicationContext ?: return null
        signal?.throwIfCanceled()

        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) {
            throw FileNotFoundException("Invalid artwork URI with empty path: $uri")
        }

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

        val resolvedFilePath = if (!songFilePath.isNullOrBlank()) {
            songFilePath
        } else {
            MediaUtils.resolveFilePathFromUri(appContext, songUri)
        }

        if (!resolvedFilePath.isNullOrBlank()) {
            MediaUtils.openArtworkDiskCacheDescriptor(appContext.cacheDir, resolvedFilePath)?.let { afd ->
                return afd
            }
        }

        signal?.throwIfCanceled()

        val bytes = MediaUtils.extractRawEmbeddedArtworkBytes(appContext, songUri, resolvedFilePath)

        if (bytes != null && bytes.isNotEmpty()) {
            signal?.throwIfCanceled()
            if (!resolvedFilePath.isNullOrBlank()) {
                MediaUtils.openArtworkDiskCacheDescriptor(appContext.cacheDir, resolvedFilePath)?.let { afd ->
                    return afd
                }
            }
            return createAssetFileDescriptorFromBytes(bytes)
        }

        // Fallback to MediaStore album art if embedded art is absent and albumId is present
        if (!albumId.isNullOrBlank()) {
            val albumIdLong = albumId.toLongOrNull()
            if (albumIdLong != null) {
                val mediaStoreAlbumArtUri = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(),
                    albumIdLong
                )
                try {
                    val fallbackAfd = appContext.contentResolver.openAssetFileDescriptor(mediaStoreAlbumArtUri, "r", signal)
                    if (fallbackAfd != null) {
                        return fallbackAfd
                    }
                } catch (_: Exception) {
                    // Fallback to not found
                }
            }
        }

        throw FileNotFoundException("No artwork found for $uri")
    }

    private fun createAssetFileDescriptorFromBytes(bytes: ByteArray): AssetFileDescriptor {
        val appContext = context?.applicationContext

        try {
            val memoryFile = MemoryFile("${appContext?.packageName ?: "rhythm"}.albumart", bytes.size)
            val pfd = try {
                val fdMethod = memoryFile.javaClass.getMethod("getFileDescriptor")
                val fd = fdMethod.invoke(memoryFile) as FileDescriptor
                memoryFile.writeBytes(bytes, 0, 0, bytes.size)
                ParcelFileDescriptor.dup(fd)
            } finally {
                memoryFile.close()
            }
            return AssetFileDescriptor(pfd, 0, bytes.size.toLong())
        } catch (e: Exception) {
            Log.w(TAG, "Ashmem MemoryFile creation failed, falling back to pipe", e)
        }

        val pipe = ParcelFileDescriptor.createPipe()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { os ->
                    os.write(bytes)
                    os.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pipe streaming failed", e)
            }
        }
        return AssetFileDescriptor(pipe[0], 0, bytes.size.toLong())
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String {
        return "image/*"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("RhythmAlbumArtProvider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("RhythmAlbumArtProvider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("RhythmAlbumArtProvider is read-only")
}
