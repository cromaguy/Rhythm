/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.util.PendingLyricsWriteRequest
import chromahub.rhythm.app.util.PendingWriteRequest
import chromahub.rhythm.app.util.PendingBatchWriteRequest
import chromahub.rhythm.app.util.RecoverableSecurityExceptionWrapper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.content.edit

class LibraryMetadataManager(
    private val context: Application,
    private val scope: CoroutineScope,
    private val getCurrentSong: () -> Song?,
    private val updateCurrentSongMetadata: (Song) -> Unit,
    private val bulkUpdateSongs: (Map<String, Song>) -> Unit,
    private val pausePlaybackForWrite: ((String) -> Pair<Long, Boolean>?)? = null,
    private val resumePlaybackAfterWrite: ((String, Pair<Long, Boolean>?) -> Unit)? = null
) {

    companion object {
        private const val TAG = "LibraryMetadataManager"
        private const val MAX_EDITABLE_LYRICS_CHARS = 200_000
    }

    private val _pendingWriteRequest = MutableStateFlow<PendingWriteRequest?>(null)
    val pendingWriteRequest: StateFlow<PendingWriteRequest?> = _pendingWriteRequest.asStateFlow()

    private val _pendingBatchWriteRequest = MutableStateFlow<PendingBatchWriteRequest?>(null)
    val pendingBatchWriteRequest: StateFlow<PendingBatchWriteRequest?> = _pendingBatchWriteRequest.asStateFlow()

    private val _pendingLyricsWriteRequest = MutableStateFlow<PendingLyricsWriteRequest?>(null)
    val pendingLyricsWriteRequest: StateFlow<PendingLyricsWriteRequest?> = _pendingLyricsWriteRequest.asStateFlow()

    /**
     * Saves metadata changes to the audio file and updates the UI.
     * On Android 11+, if permission is needed, it will trigger a permission request flow.
     */
    fun saveMetadataChanges(
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        albumArtist: String? = null,
        composer: String? = null,
        discNumber: Int = 1,
        onSuccess: (fileWriteSucceeded: Boolean) -> Unit,
        onError: (String) -> Unit,
        onPermissionRequired: ((PendingWriteRequest) -> Unit)? = null
    ) {
        scope.launch {
            var playbackState: Pair<Long, Boolean>? = null
            try {
                val appContext = context.applicationContext

                val tempArtworkUri = if (artworkUri != null && artworkUri.scheme == "content") {
                    try {
                        withContext(Dispatchers.IO) {
                            val tempFile = File(appContext.cacheDir, "temp_single_edit_art_${System.currentTimeMillis()}.jpg")
                            appContext.contentResolver.openInputStream(artworkUri)?.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Uri.fromFile(tempFile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to pre-cache single edit artwork", e)
                        artworkUri
                    }
                } else {
                    artworkUri
                }

                // Early format check — unsupported formats cannot be tag-edited
                val fileExtension = run {
                    val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
                    appContext.contentResolver.query(song.uri, projection, null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                        ?: song.uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                }
                if (fileExtension.isNotEmpty() && !MediaUtils.isSupportedByJaudiotagger(fileExtension)) {
                    withContext(Dispatchers.Main) {
                        val msg = ".$fileExtension files are not supported for metadata editing. Supported: MP3, FLAC, OGG, WAV, M4A, WMA"
                        Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                        onError(msg)
                    }
                    return@launch
                }

                val finalAlbumArtist = if (!albumArtist.isNullOrBlank()) {
                    albumArtist
                } else if (song.albumArtist.isNullOrBlank() || song.albumArtist == song.artist) {
                    artist
                } else {
                    song.albumArtist
                }

                playbackState = withContext(Dispatchers.Main) {
                    pausePlaybackForWrite?.invoke(song.id)
                }

                val success = withContext(Dispatchers.IO) {
                    MediaUtils.updateSongMetadata(
                        context = appContext,
                        song = song,
                        newTitle = title,
                        newArtist = artist,
                        newAlbum = album,
                        newGenre = genre,
                        newYear = year,
                        newTrackNumber = trackNumber,
                        artworkUri = tempArtworkUri,
                        removeArtwork = removeArtwork,
                        newAlbumArtist = finalAlbumArtist,
                        newComposer = composer,
                        newDiscNumber = discNumber
                    )
                }

                val updatedArtworkUri = when {
                    removeArtwork -> {
                        clearCachedArtwork(appContext, song.id)
                        persistArtworkOverrideRemoved(appContext, song.id)
                        MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, song.uri)
                        null
                    }
                    tempArtworkUri != null -> {
                        try {
                            val cachedUri = saveArtworkToCache(appContext, song, tempArtworkUri) ?: tempArtworkUri
                            persistArtworkOverrideUri(appContext, song.id, cachedUri)
                            MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, song.uri)
                            cachedUri
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache updated artwork for ${song.title}", e)
                            tempArtworkUri
                        }
                    }
                    else -> song.artworkUri
                }
                
                // Always update in-memory data
                val updatedSong = song.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber,
                    artworkUri = updatedArtworkUri,
                    albumArtist = finalAlbumArtist,
                    discNumber = discNumber
                )
                
                updateCurrentSongMetadata(updatedSong)
                
                // Update genre cache so it persists across library rescans
                if (genre.isNotBlank()) {
                    try {
                        val genrePrefs = appContext.getSharedPreferences("genre_cache", Context.MODE_PRIVATE)
                        genrePrefs.edit { putString("genre_${song.id}", genre) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update genre cache for song ${song.id}", e)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(song.id, playbackState)
                    if (success) {
                        Log.d(TAG, "Successfully updated file metadata for: $title by $artist")
                        onSuccess(true)
                    } else {
                        // File update failed - on Android 11+, try the permission request approach
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Log.d(TAG, "File write failed on Android 11+, attempting createWriteRequest approach")
                            val pendingRequest = withContext(Dispatchers.IO) {
                                MediaUtils.createWriteRequestForSong(
                                    context = appContext,
                                    song = song,
                                    newTitle = title,
                                    newArtist = artist,
                                    newAlbum = album,
                                    newGenre = genre,
                                    newYear = year,
                                    newTrackNumber = trackNumber,
                                    artworkUri = tempArtworkUri,
                                    removeArtwork = removeArtwork,
                                    newAlbumArtist = finalAlbumArtist,
                                    newComposer = composer,
                                    newDiscNumber = discNumber
                                )
                            }
                            
                            if (pendingRequest != null) {
                                Log.d(TAG, "Created pending write request, triggering permission dialog")
                                _pendingWriteRequest.value = pendingRequest
                                onPermissionRequired?.invoke(pendingRequest)
                                    ?: onError("Permission required to modify this file. Please grant access when prompted.")
                            } else {
                                Log.w(TAG, "Failed to create write request")
                                onSuccess(false)
                            }
                        } else {
                            Log.w(TAG, "File metadata write failed")
                            onSuccess(false)
                        }
                    }
                }
                
            } catch (e: RecoverableSecurityExceptionWrapper) {
                // Android 11+ scoped storage restriction - file not owned by app
                Log.w(TAG, "RecoverableSecurityException - attempting createWriteRequest approach")
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(song.id, playbackState)
                }
                
                val appContext = context.applicationContext
                val finalAlbumArtist = if (!albumArtist.isNullOrBlank()) {
                    albumArtist
                } else if (song.albumArtist.isNullOrBlank() || song.albumArtist == song.artist) {
                    artist
                } else {
                    song.albumArtist
                }
                
                // Update in-memory data first
                val updatedSong = song.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber,
                    artworkUri = when {
                        removeArtwork -> {
                            clearCachedArtwork(appContext, song.id)
                            persistArtworkOverrideRemoved(appContext, song.id)
                            null
                        }
                        artworkUri != null -> {
                            try {
                                val cachedUri = saveArtworkToCache(appContext, song, artworkUri) ?: artworkUri
                                persistArtworkOverrideUri(appContext, song.id, cachedUri)
                                cachedUri
                            } catch (_: Exception) {
                                artworkUri
                            }
                        }
                        else -> song.artworkUri
                    },
                    albumArtist = finalAlbumArtist,
                    discNumber = discNumber
                )
                updateCurrentSongMetadata(updatedSong)
                
                // Try to create a write request for Android 11+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingRequest = withContext(Dispatchers.IO) {
                        MediaUtils.createWriteRequestForSong(
                            context = appContext,
                            song = song,
                            newTitle = title,
                            newArtist = artist,
                            newAlbum = album,
                            newGenre = genre,
                            newYear = year,
                            newTrackNumber = trackNumber,
                            artworkUri = artworkUri,
                            removeArtwork = removeArtwork,
                            newAlbumArtist = finalAlbumArtist,
                            newComposer = composer,
                            newDiscNumber = discNumber
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (pendingRequest != null) {
                            Log.d(TAG, "Created pending write request after RecoverableSecurityException")
                            _pendingWriteRequest.value = pendingRequest
                            onPermissionRequired?.invoke(pendingRequest)
                                ?: onError("Permission required to modify this file. Please grant access when prompted.")
                        } else {
                            onError("Cannot modify this file. Changes saved to library only.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Cannot modify this file: permission denied. Changes saved to library only.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving metadata", e)
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(song.id, playbackState)
                    onError("Failed to save metadata: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    /**
     * Called after user grants permission via the system dialog triggered by createWriteRequest.
     * Completes the pending metadata write operation.
     */
    fun completeMetadataWriteAfterPermission(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val pendingRequest = _pendingWriteRequest.value
        if (pendingRequest == null) {
            Log.w(TAG, "No pending write request to complete")
            onError("No pending write request")
            return
        }
        
        val songId = pendingRequest.song.id
        scope.launch {
            val playbackState = withContext(Dispatchers.Main) {
                pausePlaybackForWrite?.invoke(songId)
            }
            try {
                val appContext = context.applicationContext
                val success = withContext(Dispatchers.IO) {
                    MediaUtils.completeWriteAfterPermissionGranted(
                        context = appContext,
                        pendingRequest = pendingRequest
                    )
                }
                
                // Clear the pending request
                _pendingWriteRequest.value = null
                
                if (success) {
                    val updatedArtworkUri = when {
                        pendingRequest.removeArtwork -> {
                            clearCachedArtwork(appContext, pendingRequest.song.id)
                            persistArtworkOverrideRemoved(appContext, pendingRequest.song.id)
                            MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, pendingRequest.song.uri)
                            null
                        }
                        !pendingRequest.artworkUriString.isNullOrBlank() -> {
                            val pendingArtworkUri = pendingRequest.artworkUriString.toUri()
                            try {
                                val cachedUri = saveArtworkToCache(
                                    appContext,
                                    pendingRequest.song,
                                    pendingArtworkUri
                                ) ?: pendingArtworkUri
                                persistArtworkOverrideUri(appContext, pendingRequest.song.id, cachedUri)
                                MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, pendingRequest.song.uri)
                                cachedUri
                            } catch (_: Exception) {
                                pendingArtworkUri
                            }
                        }
                        else -> pendingRequest.song.artworkUri
                    }

                    // Update in-memory data
                    val updatedSong = pendingRequest.song.copy(
                        title = pendingRequest.newTitle,
                        artist = pendingRequest.newArtist,
                        album = pendingRequest.newAlbum,
                        genre = pendingRequest.newGenre,
                        year = pendingRequest.newYear,
                        trackNumber = pendingRequest.newTrackNumber,
                        artworkUri = updatedArtworkUri,
                        albumArtist = pendingRequest.newAlbumArtist
                    )
                    updateCurrentSongMetadata(updatedSong)
                    
                    withContext(Dispatchers.Main) {
                        resumePlaybackAfterWrite?.invoke(songId, playbackState)
                        Log.d(TAG, "Successfully completed metadata write after permission granted")
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        resumePlaybackAfterWrite?.invoke(songId, playbackState)
                        onError("Failed to write metadata even after permission was granted")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error completing metadata write after permission", e)
                _pendingWriteRequest.value = null
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(songId, playbackState)
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Called when user denies the permission request.
     * Cleans up the pending request.
     */
    fun cancelPendingMetadataWrite() {
        val pendingRequest = _pendingWriteRequest.value
        if (pendingRequest != null) {
            scope.launch(Dispatchers.IO) {
                MediaUtils.cleanupPendingWriteRequest(pendingRequest)
            }
            _pendingWriteRequest.value = null
            Log.d(TAG, "Cancelled pending metadata write request")
        }
    }

    /**
     * Batch-edits metadata for multiple songs at once.
     * Only enabled fields are applied; disabled fields are left untouched.
     */
    fun batchEditMetadata(
        songs: List<Song>,
        artist: String?,
        album: String?,
        genre: String?,
        year: Int?,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        onProgress: (Int, Int) -> Unit,
        onComplete: (successCount: Int, failCount: Int) -> Unit,
        onPermissionRequired: ((PendingBatchWriteRequest) -> Unit)? = null
    ) {
        scope.launch {
            val appContext = context.applicationContext

            val tempArtworkUri = if (artworkUri != null && artworkUri.scheme == "content") {
                try {
                    withContext(Dispatchers.IO) {
                        val tempFile = File(appContext.cacheDir, "temp_batch_edit_art_${System.currentTimeMillis()}.jpg")
                        appContext.contentResolver.openInputStream(artworkUri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Uri.fromFile(tempFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to pre-cache batch edit artwork", e)
                    artworkUri
                }
            } else {
                artworkUri
            }

            var successCount = 0
            var failCount = 0
            // Collect updated songs for bulk in-memory update at the end
            val updatedSongs = mutableMapOf<String, Song>()
            val songsNeedingPermission = mutableListOf<Song>()

            songs.forEachIndexed { index, song ->
                try {
                    val newArtist = artist ?: song.artist
                    val newAlbum = album ?: song.album
                    val newGenre = genre ?: (song.genre ?: "")
                    val newYear = year ?: song.year
                    val newAlbumArtist = artist ?: song.albumArtist
                    val hasArtworkEdit = tempArtworkUri != null || removeArtwork

                    // Check if file format is supported before attempting write
                    val fileExtension = try {
                        val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
                        appContext.contentResolver.query(song.uri, projection, null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                            ?.substringAfterLast('.', "")
                            ?.lowercase()
                            ?: song.uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                    } catch (_: Exception) { "" }

                    val formatSupported = fileExtension.isEmpty() ||
                        MediaUtils.isSupportedByJaudiotagger(fileExtension)

                    val success = if (formatSupported) {
                        try {
                            val fileWriteSuccess = withContext(Dispatchers.IO) {
                                MediaUtils.updateSongMetadata(
                                    context = appContext,
                                    song = song,
                                    newTitle = song.title,
                                    newArtist = newArtist,
                                    newAlbum = newAlbum,
                                    newGenre = newGenre,
                                    newYear = newYear,
                                    newTrackNumber = song.trackNumber,
                                    artworkUri = tempArtworkUri,
                                    removeArtwork = removeArtwork,
                                    newAlbumArtist = newAlbumArtist
                                )
                            }

                            if (!fileWriteSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    songsNeedingPermission.add(song)
                                    false
                                } else {
                                    true
                                }
                            } else {
                                fileWriteSuccess
                            }
                        } catch (e: RecoverableSecurityExceptionWrapper) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Log.d(TAG, "Batch edit: song ${song.title} needs write permission")
                                songsNeedingPermission.add(song)
                                false
                            } else {
                                Log.w(TAG, "Batch edit: scoped storage restriction for ${song.title}, MediaStore updated only")
                                true
                            }
                        }
                    } else {
                        // Unsupported format — update MediaStore only via ContentValues
                        val mediaStoreSuccess = try {
                            withContext(Dispatchers.IO) {
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Audio.Media.ARTIST, newArtist)
                                    put(android.provider.MediaStore.Audio.Media.ALBUM, newAlbum)
                                    if (newGenre.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        put(android.provider.MediaStore.Audio.Media.GENRE, newGenre)
                                    }
                                    if (newYear > 0) {
                                        put(android.provider.MediaStore.Audio.Media.YEAR, newYear)
                                    }
                                    if (newAlbumArtist != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        put(android.provider.MediaStore.Audio.Media.ALBUM_ARTIST, newAlbumArtist)
                                    }
                                }
                                appContext.contentResolver.update(song.uri, values, null, null) > 0
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Batch edit: MediaStore-only update failed for ${song.title}", e)
                            false
                        }

                        if (hasArtworkEdit) {
                            Log.w(
                                TAG,
                                "Batch artwork update skipped for unsupported format .$fileExtension (${song.title})"
                            )
                            false
                        } else {
                            mediaStoreSuccess
                        }
                    }

                    if (success) {
                        val updatedArtworkUri = when {
                            removeArtwork -> {
                                clearCachedArtwork(appContext, song.id)
                                persistArtworkOverrideRemoved(appContext, song.id)
                                MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, song.uri)
                                null
                            }
                            tempArtworkUri != null -> {
                                try {
                                    val cachedUri = saveArtworkToCache(appContext, song, tempArtworkUri) ?: tempArtworkUri
                                    persistArtworkOverrideUri(appContext, song.id, cachedUri)
                                    MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, song.uri)
                                    cachedUri
                                } catch (_: Exception) {
                                    tempArtworkUri
                                }
                            }
                            else -> song.artworkUri
                        }

                        val updatedSong = song.copy(
                            artist = newArtist,
                            album = newAlbum,
                            genre = newGenre,
                            year = newYear,
                            artworkUri = updatedArtworkUri,
                            albumArtist = newAlbumArtist
                        )
                        
                        // Track for bulk update
                        updatedSongs[song.id] = updatedSong
                        
                        // Also update currently playing song if it matches
                        updateCurrentSongMetadata(updatedSong)

                        if (newGenre.isNotBlank()) {
                            try {
                                val genrePrefs = appContext.getSharedPreferences("genre_cache", Context.MODE_PRIVATE)
                                genrePrefs.edit { putString("genre_${song.id}", newGenre) }
                            } catch (_: Exception) {}
                        }

                        successCount++
                    } else {
                        if (!songsNeedingPermission.contains(song)) {
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Batch edit failed for ${song.title}", e)
                    failCount++
                }
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, songs.size)
                }
            }

            // Bulk update the in-memory song list so UI reflects changes immediately
            if (updatedSongs.isNotEmpty()) {
                bulkUpdateSongs(updatedSongs)
            }

            if (songsNeedingPermission.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val contentResolver = appContext.contentResolver
                    val resolvedUris = songsNeedingPermission.map { 
                        MediaUtils.getSpecificVolumeUri(appContext, it.id.toLongOrNull() ?: 0L) ?: it.uri 
                    }
                    val pendingIntent = MediaStore.createWriteRequest(contentResolver, resolvedUris)
                    val pendingRequest = PendingBatchWriteRequest(
                        intentSender = pendingIntent.intentSender,
                        songs = songsNeedingPermission,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = year,
                        artworkUriString = tempArtworkUri?.toString(),
                        removeArtwork = removeArtwork,
                        onProgress = onProgress,
                        onComplete = onComplete
                    )
                    _pendingBatchWriteRequest.value = pendingRequest
                    withContext(Dispatchers.Main) {
                        onPermissionRequired?.invoke(pendingRequest)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create batch write request", e)
                    withContext(Dispatchers.Main) {
                        onComplete(successCount, failCount + songsNeedingPermission.size)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete(successCount, failCount)
                }
            }
        }
    }

    /**
     * Completes pending batch metadata write after permission is granted.
     */
    fun completeBatchMetadataWriteAfterPermission(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val pendingRequest = _pendingBatchWriteRequest.value
        if (pendingRequest == null) {
            Log.w(TAG, "No pending batch write request to complete")
            onError("No pending batch write request")
            return
        }

        scope.launch {
            val appContext = context.applicationContext
            var successCount = 0
            var failCount = 0
            val updatedSongs = mutableMapOf<String, Song>()

            pendingRequest.songs.forEachIndexed { index, song ->
                try {
                    val newArtist = pendingRequest.artist ?: song.artist
                    val newAlbum = pendingRequest.album ?: song.album
                    val newGenre = pendingRequest.genre ?: (song.genre ?: "")
                    val newYear = pendingRequest.year ?: song.year
                    val newAlbumArtist = pendingRequest.artist ?: song.albumArtist
                    val artworkUri = pendingRequest.artworkUriString?.toUri()
                    val removeArtwork = pendingRequest.removeArtwork

                    val success = withContext(Dispatchers.IO) {
                        MediaUtils.updateSongMetadata(
                            context = appContext,
                            song = song,
                            newTitle = song.title,
                            newArtist = newArtist,
                            newAlbum = newAlbum,
                            newGenre = newGenre,
                            newYear = newYear,
                            newTrackNumber = song.trackNumber,
                            artworkUri = artworkUri,
                            removeArtwork = removeArtwork,
                            newAlbumArtist = newAlbumArtist
                        )
                    }

                    if (success) {
                        val updatedArtworkUri = when {
                            removeArtwork -> {
                                clearCachedArtwork(appContext, song.id)
                                persistArtworkOverrideRemoved(appContext, song.id)
                                MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, song.uri)
                                null
                            }
                            artworkUri != null -> {
                                try {
                                    val cachedUri = saveArtworkToCache(appContext, song, artworkUri) ?: artworkUri
                                    persistArtworkOverrideUri(appContext, song.id, cachedUri)
                                    MediaUtils.deleteCachedEmbeddedArtwork(appContext.cacheDir, song.uri)
                                    cachedUri
                                } catch (_: Exception) {
                                    artworkUri
                                }
                            }
                            else -> song.artworkUri
                        }

                        val updatedSong = song.copy(
                            artist = newArtist,
                            album = newAlbum,
                            genre = newGenre,
                            year = newYear,
                            artworkUri = updatedArtworkUri,
                            albumArtist = newAlbumArtist
                        )
                        updatedSongs[song.id] = updatedSong
                        updateCurrentSongMetadata(updatedSong)

                        if (newGenre.isNotBlank()) {
                            try {
                                val genrePrefs = appContext.getSharedPreferences("genre_cache", Context.MODE_PRIVATE)
                                genrePrefs.edit { putString("genre_${song.id}", newGenre) }
                            } catch (_: Exception) {}
                        }

                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Batch write completion failed for ${song.title}", e)
                    failCount++
                }
                withContext(Dispatchers.Main) {
                    pendingRequest.onProgress(index + 1, pendingRequest.songs.size)
                }
            }

            if (updatedSongs.isNotEmpty()) {
                bulkUpdateSongs(updatedSongs)
            }

            _pendingBatchWriteRequest.value = null

            withContext(Dispatchers.Main) {
                pendingRequest.onComplete(successCount, failCount)
                if (failCount == 0) {
                    onSuccess()
                } else {
                    onError("Updated $successCount songs, but $failCount failed")
                }
            }
        }
    }

    /**
     * Cancels pending batch metadata write request.
     */
    fun cancelPendingBatchMetadataWrite() {
        val pendingRequest = _pendingBatchWriteRequest.value
        _pendingBatchWriteRequest.value = null
        if (pendingRequest != null) {
            pendingRequest.onComplete(0, pendingRequest.songs.size)
            Log.d(TAG, "Cancelled pending batch metadata write request and notified caller")
        }
    }

    private suspend fun saveArtworkToCache(context: Context, song: Song, artworkUri: Uri): Uri? {
        try {
            context.contentResolver.openInputStream(artworkUri)?.use { inputStream ->
                val artworkFile = File(context.cacheDir, "artwork_${song.id}.jpg")
                artworkFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                Log.d(TAG, "Artwork saved to cache: ${artworkFile.absolutePath}")
                return artworkFile.toUri()
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save artwork to cache", e)
            throw e
        }
    }

    private fun persistArtworkOverrideRemoved(context: Context, songId: String) {
        try {
            context.getSharedPreferences("artwork_overrides", Context.MODE_PRIVATE)
                .edit {
                    putBoolean("removed_$songId", true)
                    remove("uri_$songId")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist removed artwork override for song $songId", e)
        }
    }

    private fun persistArtworkOverrideUri(context: Context, songId: String, artworkUri: Uri?) {
        if (artworkUri == null) return
        try {
            context.getSharedPreferences("artwork_overrides", Context.MODE_PRIVATE)
                .edit {
                    putBoolean("removed_$songId", false)
                    putString("uri_$songId", artworkUri.toString())
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist artwork override URI for song $songId", e)
        }
    }

    fun clearCachedArtwork(context: Context, songId: String) {
        try {
            val cachedArtwork = File(context.cacheDir, "artwork_${songId}.jpg")
            if (cachedArtwork.exists()) {
                cachedArtwork.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cached artwork for song $songId", e)
        }
    }

    /**
     * Embed lyrics into the current song's audio file metadata.
     */
    fun embedLyricsInFile(
        lyrics: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onPermissionRequired: ((PendingLyricsWriteRequest) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val song = getCurrentSong()
            if (song == null) {
                Log.w(TAG, "Cannot embed lyrics - no current song")
                return@launch
            }
            val songId = song.id
            val playbackState = withContext(Dispatchers.Main) {
                pausePlaybackForWrite?.invoke(songId)
            }
            try {
                val appContext = context

                // Early format check — OGG Opus and other unsupported codecs cannot be tag-edited
                val fileExtension = run {
                    val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
                    appContext.contentResolver.query(song.uri, projection, null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                        ?: song.uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                }
                if (fileExtension.isNotEmpty() && !MediaUtils.isSupportedByJaudiotagger(fileExtension)) {
                    withContext(Dispatchers.Main) {
                        resumePlaybackAfterWrite?.invoke(songId, playbackState)
                        val msg = appContext.getString(R.string.lyrics_embed_failed) +
                            " — .$fileExtension files are not supported. Try MP3, FLAC, OGG, WAV, or M4A."
                        Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                        onError?.invoke(msg)
                    }
                    return@launch
                }

                val success = MediaUtils.embedLyricsInFile(appContext, song, lyrics)
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(songId, playbackState)
                    if (success) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.lyrics_embed_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        onSuccess?.invoke()
                    } else {
                        // File update failed - on Android 11+, try createWriteRequest
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Log.d(TAG, "Lyrics embed failed, attempting createWriteRequest approach")
                            val pendingRequest = withContext(Dispatchers.IO) {
                                MediaUtils.createWriteRequestForLyrics(appContext, song, lyrics)
                            }
                            if (pendingRequest != null) {
                                _pendingLyricsWriteRequest.value = pendingRequest
                                onPermissionRequired?.invoke(pendingRequest)
                                    ?: onError?.invoke("Permission required to embed lyrics.")
                            } else {
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.lyrics_embed_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onError?.invoke("Failed to embed lyrics")
                            }
                        } else {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.lyrics_embed_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                            onError?.invoke("Failed to embed lyrics")
                        }
                    }
                }
                Log.d(TAG, "Embed lyrics result: $success for ${song.title}")
            } catch (e: RecoverableSecurityExceptionWrapper) {
                Log.w(TAG, "RecoverableSecurityException for lyrics - attempting createWriteRequest")
                val appContext = context
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(songId, playbackState)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingRequest = MediaUtils.createWriteRequestForLyrics(appContext, song, lyrics)
                    withContext(Dispatchers.Main) {
                        if (pendingRequest != null) {
                            _pendingLyricsWriteRequest.value = pendingRequest
                            onPermissionRequired?.invoke(pendingRequest)
                                ?: onError?.invoke("Permission required to embed lyrics.")
                        } else {
                            onError?.invoke("Cannot embed lyrics: permission denied.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Cannot embed lyrics: permission denied.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error embedding lyrics in file", e)
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(songId, playbackState)
                    Toast.makeText(
                        context,
                        context.getString(R.string.lyrics_embed_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    onError?.invoke("Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Complete lyrics embedding after user grants permission via createWriteRequest.
     */
    fun completeLyricsWriteAfterPermission(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val pendingRequest = _pendingLyricsWriteRequest.value
        if (pendingRequest == null) {
            onError("No pending lyrics write request")
            return
        }
        val songId = pendingRequest.song.id
        scope.launch {
            val playbackState = withContext(Dispatchers.Main) {
                pausePlaybackForWrite?.invoke(songId)
            }
            try {
                val appContext = context.applicationContext
                val success = withContext(Dispatchers.IO) {
                    MediaUtils.completeLyricsWriteAfterPermission(appContext, pendingRequest)
                }
                _pendingLyricsWriteRequest.value = null
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(songId, playbackState)
                    if (success) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.lyrics_embed_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        onSuccess()
                    } else {
                        onError("Failed to embed lyrics even after permission was granted")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error completing lyrics write after permission", e)
                _pendingLyricsWriteRequest.value = null
                withContext(Dispatchers.Main) {
                    resumePlaybackAfterWrite?.invoke(songId, playbackState)
                    onError("Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Cancel pending lyrics write request.
     */
    fun cancelPendingLyricsWrite() {
        val pendingRequest = _pendingLyricsWriteRequest.value
        if (pendingRequest != null) {
            scope.launch(Dispatchers.IO) {
                MediaUtils.cleanupPendingLyricsWriteRequest(pendingRequest)
            }
            _pendingLyricsWriteRequest.value = null
        }
    }
}
