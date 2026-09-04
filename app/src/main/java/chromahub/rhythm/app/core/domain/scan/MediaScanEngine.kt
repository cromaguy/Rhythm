/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.scan

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.MediaScanMode
import chromahub.rhythm.app.shared.data.model.ScanPhase
import chromahub.rhythm.app.shared.data.model.ScanProgress
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.AudioFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri
import androidx.core.content.edit

/**
 * Centralized, High-Performance Media Scanning Engine for Rhythm.
 * Features:
 * - Differential scanning (MediaStore vs Room DB DATE_MODIFIED comparison)
 * - O(1) HashMap lookups (eliminates memory leaks, CPU heating, battery drain)
 * - Asynchronous batching & yielding on Dispatchers.IO
 */
class MediaScanEngine(
    private val context: Context,
    private val database: RhythmDatabase,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "MediaScanEngine"
        private const val BATCH_SIZE = 100

        fun mediaScanSelection(minimumDuration: Long = 0L): String {
            val baseSelection = "(${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR ${MediaStore.Audio.Media.MIME_TYPE} = 'video/mp4' OR ${MediaStore.Audio.Media.MIME_TYPE} = 'video/x-matroska' OR ${MediaStore.Audio.Media.MIME_TYPE} = 'application/x-matroska')"
            return if (minimumDuration > 0L) {
                "$baseSelection AND ${MediaStore.Audio.Media.DURATION} >= $minimumDuration"
            } else {
                baseSelection
            }
        }
    }

    private val _scanProgress = MutableStateFlow(ScanProgress(0, 0, ScanPhase.Idle))
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    /**
     * Performs a high-performance differential or full media scan.
     */
    suspend fun performScan(
        forceRefresh: Boolean = false,
        allowedFormats: Set<String>? = null,
        minimumDuration: Long = 0L
    ): List<Song> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting media scan (forceRefresh=$forceRefresh, minimumDuration=${minimumDuration}ms)")
        _scanProgress.value = ScanProgress(0, 0, ScanPhase.Songs, 0)

        // Query existing DB entries into an O(1) Map by ID
        val existingDbSongs = if (!forceRefresh) {
            database.songDao().getAllSongs().associateBy { it.id }
        } else {
            emptyMap()
        }

        val mediaScanMode = appSettings.mediaScanMode.value
        val whitelistedFolders = appSettings.whitelistedFolders.value
        val whitelistedSongs = appSettings.whitelistedSongs.value
        val blacklistedFolders = appSettings.blacklistedFolders.value
        val blacklistedSongs = appSettings.blacklistedSongs.value

        if (mediaScanMode == MediaScanMode.WHITELIST && whitelistedFolders.isEmpty() && whitelistedSongs.isEmpty()) {
            Log.d(TAG, "Whitelist mode active with no whitelisted folders or songs; skipping MediaStore scan")
            database.withTransaction {
                if (forceRefresh) {
                    database.songDao().replaceAll(emptyList())
                }
            }
            _scanProgress.value = ScanProgress(0, 0, ScanPhase.Complete, 0)
            return@withContext emptyList()
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.DISC_NUMBER)
                add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
            }
        }.toTypedArray()

        val selection = mediaScanSelection(minimumDuration)
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val scannedSongs = mutableListOf<SongEntity>()
        val seenIds = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()

        var rawMediaStoreCount = 0

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val totalCount = cursor.count
                rawMediaStoreCount = totalCount
                Log.d(TAG, "MediaStore query found $totalCount candidates")
                _scanProgress.value = ScanProgress(0, totalCount, ScanPhase.Songs, 0)

                val colId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val colTitle = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val colArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val colAlbum = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val colAlbumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val colDuration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val colTrack = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val colYear = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val colDateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val colDateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val colData = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val colGenre = cursor.getColumnIndex("genre") // MediaStore.Audio.AudioColumns.GENRE (API 30+)
                val colAlbumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val colDiscNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER) else -1
                val colCdTrackNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER) else -1

            var processed = 0
            var lastProgressEmitTime = 0L

                while (cursor.moveToNext()) {
                    processed++
                    val id = cursor.getLong(colId).toString()
                    if (seenIds.contains(id) || blacklistedSongs.contains(id)) continue

                    val path = if (colData >= 0) cursor.getString(colData) else null
                    if (path != null) {
                        val normPath = path.lowercase()
                        if (seenPaths.contains(normPath)) continue

                        if (allowedFormats != null) {
                            val ext = path.substringAfterLast('.', "").lowercase()
                            if (ext.isNotEmpty() && !allowedFormats.contains(ext)) continue
                        }

                        if (mediaScanMode == MediaScanMode.WHITELIST) {
                            val isFolderWhitelisted = whitelistedFolders.isNotEmpty() &&
                                whitelistedFolders.any { normPath.startsWith(it.lowercase()) }
                            val isSongWhitelisted = whitelistedSongs.isNotEmpty() &&
                                whitelistedSongs.contains(id)
                            if (!isFolderWhitelisted && !isSongWhitelisted) continue
                        }

                        if (mediaScanMode == MediaScanMode.BLACKLIST && blacklistedFolders.isNotEmpty()) {
                            val isBlacklisted = blacklistedFolders.any { normPath.startsWith(it.lowercase()) }
                            if (isBlacklisted) continue
                        }

                        seenPaths.add(normPath)
                    }

                    val duration = cursor.getLong(colDuration)
                    if (minimumDuration > 0 && duration < minimumDuration) continue

                    val rawDateModified = cursor.getLong(colDateModified)
                    val dateModified = if (rawDateModified in 1..99_999_999_999L) rawDateModified * 1000L else rawDateModified

                    val preferSongArtwork = appSettings.preferSongArtwork.value
                    val losslessArtwork = appSettings.isLosslessArtworkActive.value

                    // Differential check: reuse existing DB record if unmodified and timestamps are in ms
                    val existing = existingDbSongs[id]

                    if (existing != null && existing.dateModified == dateModified && existing.dateAdded >= 100_000_000_000L) {
                        val existingArt = if (preferSongArtwork) {
                            chromahub.rhythm.app.util.MediaUtils.getCachedEmbeddedAlbumArtUri(
                                cacheDir = context.filesDir,
                                songUri = (existing.uri).toUri(),
                                lossless = losslessArtwork,
                                exactMatchOnly = false
                            )?.toString() ?: (existing.artworkUri ?: Uri.withAppendedPath(
                                ("content://media/external/audio/albumart").toUri(),
                                existing.albumId
                            ).toString())
                        } else {
                            existing.artworkUri ?: Uri.withAppendedPath(
                                ("content://media/external/audio/albumart").toUri(),
                                existing.albumId
                            ).toString()
                        }
                        scannedSongs.add(existing.copy(artworkUri = existingArt))
                        seenIds.add(id)
                    } else {
                        val rawTitle = cursor.getString(colTitle) ?: "Unknown Title"
                        val rawArtist = cursor.getString(colArtist) ?: "<unknown>"
                        val rawAlbum = cursor.getString(colAlbum) ?: "Unknown Album"
                        val albumId = cursor.getLong(colAlbumId).toString()
                        val rawTrack = cursor.getInt(colTrack)
                        val cdTrack = if (colCdTrackNumber >= 0) cursor.getInt(colCdTrackNumber) else 0
                        val discFromStore = if (colDiscNumber >= 0) cursor.getInt(colDiscNumber) else 0
                        val rawYear = cursor.getInt(colYear)
                        val rawDateAdded = cursor.getLong(colDateAdded)
                        val dateAdded = if (rawDateAdded in 1..99_999_999_999L) {
                            rawDateAdded * 1000L
                        } else if (rawDateAdded > 0L) {
                            rawDateAdded
                        } else {
                            System.currentTimeMillis()
                        }
                        val finalDateModified = dateModified.takeIf { it > 0L } ?: dateAdded
                        val rawGenre = if (colGenre >= 0) cursor.getString(colGenre) else null
                        val rawAlbumArtist = if (colAlbumArtist >= 0) cursor.getString(colAlbumArtist) else null

                        var title = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(rawTitle) ?: rawTitle
                        var artist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(rawArtist) ?: rawArtist
                        var album = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(rawAlbum) ?: rawAlbum
                        var genre = rawGenre?.let { chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) }
                        var albumArtist = rawAlbumArtist?.let { chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) }

                        var discNumber = when {
                            discFromStore > 0 -> discFromStore
                            rawTrack >= 1000 -> rawTrack / 1000
                            else -> 1
                        }

                        var trackNumber = when {
                            rawTrack >= 1000 -> rawTrack % 1000
                            rawTrack > 0 -> rawTrack
                            cdTrack > 0 -> cdTrack
                            else -> 0
                        }

                        var year = rawYear

                        // Fallback tag extraction for missing year or FLAC/audio files where MediaStore failed
                        if ((year == 0 || trackNumber == 0 || path?.lowercase()?.endsWith(".flac") == true) && !path.isNullOrBlank()) {
                            try {
                                val file = File(path)
                                if (file.exists() && file.canRead()) {
                                    android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                        val metadata = com.kyant.taglib.TagLib.getMetadata(fd.detachFd())
                                        val propertyMap = metadata?.propertyMap ?: emptyMap()
                                        if (year == 0) {
                                            val dateValues = propertyMap["DATE"] ?: propertyMap["YEAR"]
                                            if (!dateValues.isNullOrEmpty() && dateValues[0].isNotBlank()) {
                                                year = chromahub.rhythm.app.util.MetadataHeuristics.parseYear(dateValues[0])
                                            }
                                        }
                                        if (trackNumber == 0) {
                                            val trackValues = propertyMap["TRACKNUMBER"]
                                            if (!trackValues.isNullOrEmpty() && trackValues[0].isNotBlank()) {
                                                val parsedTrackStr = trackValues[0].substringBefore('/')
                                                val parsedInt = parsedTrackStr.toIntOrNull() ?: 0
                                                if (parsedInt >= 1000) {
                                                    discNumber = parsedInt / 1000
                                                    trackNumber = parsedInt % 1000
                                                } else if (parsedInt > 0) {
                                                    trackNumber = parsedInt
                                                }
                                            }
                                        }
                                        if (discNumber <= 1) {
                                            val discValues = propertyMap["DISCNUMBER"]
                                            if (!discValues.isNullOrEmpty() && discValues[0].isNotBlank()) {
                                                val parsedDisc = discValues[0].substringBefore('/').toIntOrNull() ?: 1
                                                if (parsedDisc > 0) discNumber = parsedDisc
                                            }
                                        }
                                        val tagTitle = propertyMap["TITLE"]?.firstOrNull()?.trim()
                                        if (!tagTitle.isNullOrBlank() && (title == "Unknown Title" || chromahub.rhythm.app.util.MetadataHeuristics.isLikelyCorruptedMetadata(title))) {
                                            title = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(tagTitle) ?: tagTitle
                                        }
                                        val tagArtist = propertyMap["ARTIST"]?.firstOrNull()?.trim()
                                        if (!tagArtist.isNullOrBlank() && (artist == "<unknown>" || chromahub.rhythm.app.util.MetadataHeuristics.isLikelyCorruptedMetadata(artist))) {
                                            artist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(tagArtist) ?: tagArtist
                                        }
                                        val tagAlbum = propertyMap["ALBUM"]?.firstOrNull()?.trim()
                                        if (!tagAlbum.isNullOrBlank() && (album == "Unknown Album" || chromahub.rhythm.app.util.MetadataHeuristics.isLikelyCorruptedMetadata(album))) {
                                            album = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(tagAlbum) ?: tagAlbum
                                        }
                                        val tagGenre = propertyMap["GENRE"]?.firstOrNull()?.trim()
                                        if (!tagGenre.isNullOrBlank() && (genre.isNullOrBlank() || chromahub.rhythm.app.util.MetadataHeuristics.isLikelyCorruptedMetadata(genre))) {
                                            genre = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(tagGenre) ?: tagGenre
                                        }
                                    }
                                }
                            } catch (_: Throwable) {
                                // TagLib fallback non-fatal
                            }
                        }

                        val contentUri = Uri.withAppendedPath(collection, id).toString()
                        val defaultArtworkUri = Uri.withAppendedPath(
                            ("content://media/external/audio/albumart").toUri(),
                            albumId
                        ).toString()

                        val initialArtworkUri = if (preferSongArtwork) {
                            chromahub.rhythm.app.util.MediaUtils.getCachedEmbeddedAlbumArtUri(
                                cacheDir = context.filesDir,
                                songUri = (contentUri).toUri(),
                                lossless = losslessArtwork,
                                exactMatchOnly = false
                            )?.toString() ?: defaultArtworkUri
                        } else {
                            defaultArtworkUri
                        }

                        val entity = SongEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            uri = contentUri,
                            artworkUri = initialArtworkUri,
                            trackNumber = trackNumber,
                            year = year,
                            genre = genre,
                            dateAdded = dateAdded,
                            dateModified = finalDateModified,
                            albumArtist = albumArtist,
                            bitrate = null,
                            sampleRate = null,
                            channels = null,
                            codec = null,
                            discNumber = discNumber,
                            path = path
                        )
                        scannedSongs.add(entity)
                        seenIds.add(id)
                    }

                    val nowTime = System.currentTimeMillis()
                    if (nowTime - lastProgressEmitTime >= 150 || processed == totalCount) {
                        _scanProgress.value = ScanProgress(processed, totalCount, ScanPhase.Songs, 0)
                        lastProgressEmitTime = nowTime
                        yield()
                    }
                }
            }

            // Sync with Room DB atomically
            _scanProgress.value = ScanProgress(scannedSongs.size, scannedSongs.size, ScanPhase.SavingDb, 0)
            database.withTransaction {
                if (forceRefresh) {
                    database.songDao().replaceAll(scannedSongs)
                } else {
                    val staleSongIds = existingDbSongs.keys - seenIds
                    if (staleSongIds.isNotEmpty()) {
                        database.songDao().deleteByIds(staleSongIds.toList())
                    }
                    database.songDao().upsertAll(scannedSongs)
                }
            }

            appSettings.setLastScanTimestamp(System.currentTimeMillis())
            try {
                context.getSharedPreferences("library_scan_metadata", Context.MODE_PRIVATE)
                    .edit { putInt("last_scan_mediastore_count", rawMediaStoreCount) }
                Log.d(TAG, "Saved MediaStore count ($rawMediaStoreCount) to library_scan_metadata")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save MediaStore count", e)
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Scan completed: ${scannedSongs.size} songs processed in ${totalDuration}ms")
            _scanProgress.value = ScanProgress(scannedSongs.size, scannedSongs.size, ScanPhase.Complete, totalDuration)

            scannedSongs.map { entity ->
                Song(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    albumId = entity.albumId,
                    duration = entity.duration,
                    uri = (entity.uri).toUri(),
                    artworkUri = entity.artworkUri?.let { (it).toUri() },
                    trackNumber = entity.trackNumber,
                    year = entity.year,
                    genre = entity.genre,
                    dateAdded = entity.dateAdded,
                    dateModified = entity.dateModified,
                    albumArtist = entity.albumArtist,
                    bitrate = entity.bitrate,
                    sampleRate = entity.sampleRate,
                    channels = entity.channels,
                    codec = entity.codec,
                    discNumber = entity.discNumber,
                    path = entity.path
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during media scan", e)
            _scanProgress.value = ScanProgress(0, 0, ScanPhase.Error, 0)
            emptyList()
        }
    }
}
