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
import chromahub.rhythm.app.infrastructure.provider.RhythmAlbumArtProvider
import chromahub.rhythm.app.util.StoragePathUtils
import java.util.Locale

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
    private val appSettings: AppSettings,
    val dateAddedResolver: StableDateAddedResolver = StableDateAddedResolver(context)
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

        val allExistingSongs = database.songDao().getAllSongs()
        dateAddedResolver.prepareForScan(allExistingSongs)

        val existingDbSongs = if (!forceRefresh) {
            allExistingSongs.associateBy { it.id }
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
                val colRelPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                val colDispName = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)

            var processed = 0
            var lastProgressEmitTime = 0L

                while (cursor.moveToNext()) {
                    processed++
                    val id = cursor.getLong(colId).toString()
                    if (seenIds.contains(id) || blacklistedSongs.contains(id)) continue

                    var path = if (colData >= 0) cursor.getString(colData) else null
                    if (path.isNullOrBlank() && colRelPath >= 0 && colDispName >= 0) {
                        val rel = cursor.getString(colRelPath)
                        val disp = cursor.getString(colDispName)
                        if (!rel.isNullOrBlank() && !disp.isNullOrBlank()) {
                            val extStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
                            path = "$extStorage/$rel$disp"
                        }
                    }
                    val normPath = path?.let { StoragePathUtils.sanitizeAndNormalizePath(it) }
                    if (!normPath.isNullOrBlank()) {
                        if (seenPaths.contains(normPath)) continue

                        if (allowedFormats != null) {
                            val ext = normPath.substringAfterLast('.', "").lowercase(Locale.ROOT)
                            if (ext.isNotEmpty() && !allowedFormats.contains(ext)) continue
                        }

                        if (mediaScanMode == MediaScanMode.WHITELIST) {
                            val isFolderWhitelisted = whitelistedFolders.isNotEmpty() &&
                                StoragePathUtils.isPathInFolders(normPath, whitelistedFolders)
                            val isSongWhitelisted = whitelistedSongs.isNotEmpty() &&
                                whitelistedSongs.contains(id)
                            if (!isFolderWhitelisted && !isSongWhitelisted) continue
                        }

                        if (mediaScanMode == MediaScanMode.BLACKLIST && blacklistedFolders.isNotEmpty()) {
                            val isBlacklisted = StoragePathUtils.isPathInFolders(normPath, blacklistedFolders)
                            if (isBlacklisted) continue
                        }

                        seenPaths.add(normPath)
                    } else {
                        if (mediaScanMode == MediaScanMode.WHITELIST) {
                            val isSongWhitelisted = whitelistedSongs.isNotEmpty() &&
                                whitelistedSongs.contains(id)
                            if (!isSongWhitelisted) continue
                        }
                    }

                    val duration = cursor.getLong(colDuration)
                    if (minimumDuration > 0 && duration < minimumDuration) continue

                    val rawDateModified = cursor.getLong(colDateModified)
                    val dateModified = if (rawDateModified in 1..99_999_999_999L) rawDateModified * 1000L else rawDateModified

                    val preferSongArtwork = appSettings.preferSongArtwork.value
                    val losslessArtwork = appSettings.isLosslessArtworkActive.value

                    // Differential check: reuse existing DB record if unmodified and timestamps are in ms
                    val existing = existingDbSongs[id] ?: (if (!forceRefresh && !normPath.isNullOrBlank()) dateAddedResolver.findExistingByPath(normPath) else null)

                    if (existing != null && existing.dateModified == dateModified && existing.dateAdded >= 100_000_000_000L) {
                        val defaultArt = Uri.withAppendedPath(
                            ("content://media/external/audio/albumart").toUri(),
                            existing.albumId
                        ).toString()
                        val existingArt = if (preferSongArtwork) {
                            RhythmAlbumArtProvider.buildSongUri(
                                id = id,
                                path = existing.path,
                                albumId = existing.albumId,
                                lossless = losslessArtwork
                            ).toString()
                        } else {
                            defaultArt
                        }
                        dateAddedResolver.claimExistingSong(existing.id)
                        if (normPath != null) {
                            dateAddedResolver.recordResolvedDate(
                                normPath = normPath,
                                normTitle = StableDateAddedResolver.normalizeMetadata(existing.title),
                                normArtist = StableDateAddedResolver.normalizeMetadata(existing.artist),
                                normAlbum = StableDateAddedResolver.normalizeMetadata(existing.album),
                                durSec = existing.duration / 1000L,
                                dateAddedMs = existing.dateAdded
                            )
                        }
                        val contentUri = Uri.withAppendedPath(collection, id).toString()
                        scannedSongs.add(existing.copy(id = id, uri = contentUri, artworkUri = existingArt))
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
                        val observedDateAdded = StableDateAddedResolver.normalizeTimestamp(rawDateAdded)
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
                        if ((year == 0 || trackNumber == 0 || albumArtist.isNullOrBlank() || path?.lowercase()?.endsWith(".flac") == true) && !path.isNullOrBlank()) {
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
                                        val tagAlbumArtist = (propertyMap["ALBUMARTIST"] ?: propertyMap["ALBUM ARTIST"] ?: propertyMap["ALBUM_ARTIST"])?.firstOrNull()?.trim()
                                        if (!tagAlbumArtist.isNullOrBlank() && (albumArtist.isNullOrBlank() || chromahub.rhythm.app.util.MetadataHeuristics.isLikelyCorruptedMetadata(albumArtist))) {
                                            albumArtist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(tagAlbumArtist) ?: tagAlbumArtist
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
                            RhythmAlbumArtProvider.buildSongUri(
                                id = id,
                                path = path,
                                albumId = albumId,
                                lossless = losslessArtwork
                            ).toString()
                        } else {
                            defaultArtworkUri
                        }

                        val dateAdded = dateAddedResolver.resolveDateAdded(
                            songId = id,
                            filePath = path,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            observedDateAddedMs = observedDateAdded
                        )
                        val finalDateModified = dateModified.takeIf { it > 0L } ?: dateAdded

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

            if (mediaScanMode == MediaScanMode.WHITELIST && whitelistedFolders.isNotEmpty()) {
                val unindexedSongs = scanWhitelistedFoldersOnDisk(
                    whitelistedFolders = whitelistedFolders,
                    seenPaths = seenPaths,
                    seenIds = seenIds,
                    allowedFormats = allowedFormats,
                    minimumDuration = minimumDuration,
                    includeHiddenWhitelistedMedia = appSettings.includeHiddenWhitelistedMedia.value
                )
                scannedSongs.addAll(unindexedSongs)
            }

            dateAddedResolver.flush()

            // Sync with Room DB atomically
            _scanProgress.value = ScanProgress(scannedSongs.size, scannedSongs.size, ScanPhase.SavingDb, 0)
            database.withTransaction {
                if (forceRefresh) {
                    database.songDao().replaceAll(scannedSongs)
                    database.artistDao().deleteAll()
                    database.songArtistDao().deleteAll()
                } else {
                    val staleSongIds = allExistingSongs.map { it.id }.toSet() - seenIds
                    val newSongIds = seenIds - allExistingSongs.map { it.id }.toSet()
                    if (staleSongIds.isNotEmpty()) {
                        database.songDao().deleteByIds(staleSongIds.toList())
                        database.songArtistDao().deleteBySongIds(staleSongIds.toList())
                    }
                    database.songDao().upsertAll(scannedSongs)
                    if (staleSongIds.isNotEmpty() || newSongIds.isNotEmpty()) {
                        database.artistDao().deleteAll()
                        database.songArtistDao().deleteAll()
                    }
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

            if (scannedSongs.isNotEmpty()) {
                runCatching {
                    val lyricsDir = File(context.filesDir, "lyrics")
                    val activePairs = scannedSongs.map { it.artist to it.title }
                    val pruned = chromahub.rhythm.app.util.OrphanedLyricsCleaner
                        .pruneOrphanedAppLyricsCache(lyricsDir, activePairs)
                    if (pruned > 0) {
                        Log.d(TAG, "Pruned $pruned orphaned lyric cache files")
                    }
                }
            }

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

    private fun scanWhitelistedFoldersOnDisk(
        whitelistedFolders: List<String>,
        seenPaths: MutableSet<String>,
        seenIds: MutableSet<String>,
        allowedFormats: Set<String>?,
        minimumDuration: Long,
        includeHiddenWhitelistedMedia: Boolean
    ): List<SongEntity> {
        val discovered = mutableListOf<SongEntity>()
        val defaultAudioExtensions = setOf(
            "mp3", "m4a", "flac", "ogg", "opus", "opa", "wav", "aac", "alac", "aiff", "aif", "wma", "mka",
            "ac3", "ac4", "oga", "mid", "midi", "adts", "m4b"
        )
        val preferSongArtwork = appSettings.preferSongArtwork.value
        val losslessArtwork = appSettings.isLosslessArtworkActive.value

        for (folderPath in whitelistedFolders) {
            val root = File(folderPath)
            if (!root.exists() || !root.isDirectory) {
                continue
            }

            try {
                root.walkTopDown()
                    .onEnter { dir ->
                        if (!includeHiddenWhitelistedMedia && dir.name.startsWith(".")) {
                            false
                        } else {
                            true
                        }
                    }
                    .forEach { file ->
                        if (!file.isFile) return@forEach
                        if (!includeHiddenWhitelistedMedia && file.name.startsWith(".")) return@forEach

                        val ext = file.extension.lowercase(Locale.ROOT)
                        val isSupported = if (allowedFormats != null) {
                            allowedFormats.contains(ext)
                        } else {
                            defaultAudioExtensions.contains(ext)
                        }
                        if (!isSupported) return@forEach

                        val absolutePath = file.absolutePath
                        val normPath = StoragePathUtils.sanitizeAndNormalizePath(absolutePath)
                        if (seenPaths.contains(normPath)) return@forEach

                        val existing = dateAddedResolver.findExistingByPath(normPath)
                        val fileId = "file_${normPath.hashCode()}"

                        if (existing != null && file.lastModified() == existing.dateModified && existing.duration >= minimumDuration) {
                            val resolvedId = existing.id.ifEmpty { fileId }
                            discovered.add(existing.copy(id = resolvedId))
                            seenPaths.add(normPath)
                            seenIds.add(resolvedId)
                            return@forEach
                        }

                        val entity = createSongEntityFromFile(file, fileId, preferSongArtwork, losslessArtwork, minimumDuration)
                        if (entity != null) {
                            discovered.add(entity)
                            seenPaths.add(normPath)
                            seenIds.add(fileId)

                            try {
                                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                            } catch (_: Exception) {}
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning whitelisted folder on disk: $folderPath", e)
            }
        }

        if (discovered.isNotEmpty()) {
            Log.d(TAG, "Added ${discovered.size} songs from direct whitelisted folders scan")
        }
        return discovered
    }

    private fun createSongEntityFromFile(
        file: File,
        id: String,
        preferSongArtwork: Boolean,
        losslessArtwork: Boolean,
        minimumDuration: Long
    ): SongEntity? {
        var title = file.nameWithoutExtension
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var albumArtist: String? = null
        var genre: String? = null
        var duration = 0L
        var year = 0
        var trackNumber = 0
        var discNumber = 1

        try {
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val metadata = com.kyant.taglib.TagLib.getMetadata(fd.detachFd())
                val propertyMap = metadata?.propertyMap ?: emptyMap()

                propertyMap["TITLE"]?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    title = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                }
                propertyMap["ARTIST"]?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    artist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                }
                propertyMap["ALBUM"]?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    album = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                }
                (propertyMap["ALBUMARTIST"] ?: propertyMap["ALBUM ARTIST"] ?: propertyMap["ALBUM_ARTIST"])?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    albumArtist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                }
                propertyMap["GENRE"]?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    genre = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                }
                val dateValues = propertyMap["DATE"] ?: propertyMap["YEAR"]
                if (!dateValues.isNullOrEmpty() && dateValues[0].isNotBlank()) {
                    year = chromahub.rhythm.app.util.MetadataHeuristics.parseYear(dateValues[0])
                }
                val trackValues = propertyMap["TRACKNUMBER"]
                if (!trackValues.isNullOrEmpty() && trackValues[0].isNotBlank()) {
                    val parsed = trackValues[0].substringBefore('/').toIntOrNull() ?: 0
                    if (parsed >= 1000) {
                        discNumber = parsed / 1000
                        trackNumber = parsed % 1000
                    } else if (parsed > 0) {
                        trackNumber = parsed
                    }
                }
                val discValues = propertyMap["DISCNUMBER"]
                if (!discValues.isNullOrEmpty() && discValues[0].isNotBlank()) {
                    val parsedDisc = discValues[0].substringBefore('/').toIntOrNull() ?: 1
                    if (parsedDisc > 0) discNumber = parsedDisc
                }
            }
        } catch (_: Throwable) {}

        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            try {
                if (duration <= 0L) {
                    duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
                if (title == file.nameWithoutExtension) {
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.let {
                        if (it.isNotBlank()) title = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                    }
                }
                if (artist == "Unknown Artist") {
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.let {
                        if (it.isNotBlank()) artist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                    }
                }
                if (album == "Unknown Album") {
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.let {
                        if (it.isNotBlank()) album = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                    }
                }
                if (albumArtist.isNullOrBlank()) {
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.let {
                        if (it.isNotBlank()) albumArtist = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                    }
                }
                if (genre.isNullOrBlank()) {
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.let {
                        if (it.isNotBlank()) genre = chromahub.rhythm.app.util.MetadataHeuristics.normalizeMetadataText(it) ?: it
                    }
                }
                if (year == 0) {
                    year = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
                }
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        if (minimumDuration > 0 && duration < minimumDuration) return null
        if (duration <= 0L) {
            duration = 180_000L
        }

        val fileUri = Uri.fromFile(file).toString()
        val artworkUri = if (preferSongArtwork) {
            RhythmAlbumArtProvider.buildSongUri(
                id = id,
                path = file.absolutePath,
                albumId = "album_$id",
                lossless = losslessArtwork
            ).toString()
        } else {
            null
        }

        val now = System.currentTimeMillis()
        val dateAdded = dateAddedResolver.resolveDateAdded(
            songId = id,
            filePath = file.absolutePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            observedDateAddedMs = file.lastModified().takeIf { it > 0 } ?: now
        )
        val dateModified = file.lastModified().takeIf { it > 0 } ?: dateAdded

        return SongEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = "album_$id",
            duration = duration,
            uri = fileUri,
            artworkUri = artworkUri,
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            dateAdded = dateAdded,
            dateModified = dateModified,
            albumArtist = albumArtist,
            bitrate = null,
            sampleRate = null,
            channels = null,
            codec = file.extension.uppercase(Locale.ROOT),
            discNumber = discNumber,
            path = file.absolutePath
        )
    }
}
