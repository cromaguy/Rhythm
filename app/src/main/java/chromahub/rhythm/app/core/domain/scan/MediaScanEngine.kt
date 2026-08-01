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
        Log.d(TAG, "Starting media scan (forceRefresh=$forceRefresh)")
        _scanProgress.value = ScanProgress(0, 0, ScanPhase.Songs, 0)

        // Query existing DB entries into an O(1) Map by ID
        val existingDbSongs = if (!forceRefresh) {
            database.songDao().getAllSongs().associateBy { it.id }
        } else {
            emptyMap()
        }

        val mediaScanMode = appSettings.mediaScanMode.value
        val whitelistedFolders = appSettings.whitelistedFolders.value
        val blacklistedFolders = appSettings.blacklistedFolders.value
        val blacklistedSongs = appSettings.blacklistedSongs.value

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
            }
        }.toTypedArray()

        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} > 10000"
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
                val colGenre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val colAlbumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)

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

                        if (mediaScanMode == MediaScanMode.WHITELIST && whitelistedFolders.isNotEmpty()) {
                            val isWhitelisted = whitelistedFolders.any { normPath.startsWith(it.lowercase()) }
                            if (!isWhitelisted) continue
                        }

                        if (mediaScanMode == MediaScanMode.BLACKLIST && blacklistedFolders.isNotEmpty()) {
                            val isBlacklisted = blacklistedFolders.any { normPath.startsWith(it.lowercase()) }
                            if (isBlacklisted) continue
                        }

                        seenPaths.add(normPath)
                    }

                    val duration = cursor.getLong(colDuration)
                    if (minimumDuration > 0 && duration < minimumDuration) continue

                    val dateModified = cursor.getLong(colDateModified)

                    // Differential check: reuse existing DB record if unmodified
                    val existing = existingDbSongs[id]
                    val preferSongArtwork = appSettings.preferSongArtwork.value
                    val losslessArtwork = appSettings.isLosslessArtworkActive.value

                    if (existing != null && existing.dateModified == dateModified) {
                        val existingArt = existing.artworkUri ?: ""
                        val isLosslessArt = existingArt.contains("embedded_art_lossless_")
                        val isFileExist = if (existingArt.startsWith("file:") || existingArt.startsWith("/")) {
                            try {
                                val artPath = if (existingArt.startsWith("file:")) Uri.parse(existingArt).path else existingArt
                                artPath?.let { File(it).exists() && File(it).length() > 0L } == true
                            } catch (e: Exception) {
                                false
                            }
                        } else true

                        val needsArtUpgrade = preferSongArtwork && (existingArt.isEmpty() || !isFileExist || (losslessArtwork && !isLosslessArt))

                        if (needsArtUpgrade) {
                            val parsedUri = Uri.parse(existing.uri)
                            val embeddedUri = try {
                                chromahub.rhythm.app.util.MediaUtils.extractEmbeddedAlbumArt(
                                    context, parsedUri, context.filesDir, losslessArtwork
                                )?.toString()
                            } catch (e: Exception) {
                                null
                            }
                            if (embeddedUri != null && embeddedUri != existing.artworkUri) {
                                scannedSongs.add(existing.copy(artworkUri = embeddedUri))
                            } else {
                                scannedSongs.add(existing)
                            }
                        } else {
                            scannedSongs.add(existing)
                        }
                        seenIds.add(id)
                    } else {
                        val title = cursor.getString(colTitle) ?: "Unknown Title"
                        val artist = cursor.getString(colArtist) ?: "<unknown>"
                        val album = cursor.getString(colAlbum) ?: "Unknown Album"
                        val albumId = cursor.getLong(colAlbumId).toString()
                        val trackNumber = cursor.getInt(colTrack)
                        val year = cursor.getInt(colYear)
                        val dateAdded = cursor.getLong(colDateAdded)
                        val genre = if (colGenre >= 0) cursor.getString(colGenre) else null
                        val albumArtist = if (colAlbumArtist >= 0) cursor.getString(colAlbumArtist) else null

                        val contentUri = Uri.withAppendedPath(collection, id).toString()
                        val defaultArtworkUri = Uri.withAppendedPath(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()

                        val finalArtworkUri = if (preferSongArtwork) {
                            try {
                                val parsedUri = Uri.parse(contentUri)
                                val embeddedUri = chromahub.rhythm.app.util.MediaUtils.extractEmbeddedAlbumArt(
                                    context, parsedUri, context.filesDir, losslessArtwork
                                )
                                embeddedUri?.toString() ?: defaultArtworkUri
                            } catch (e: Exception) {
                                defaultArtworkUri
                            }
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
                            artworkUri = finalArtworkUri,
                            trackNumber = trackNumber,
                            year = year,
                            genre = genre,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            albumArtist = albumArtist,
                            bitrate = null,
                            sampleRate = null,
                            channels = null,
                            codec = null,
                            discNumber = 1,
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
            appSettings.setEmbeddedArtworkExtractionCompleted(true)
            appSettings.setEmbeddedArtworkExtractionLosslessStatus(appSettings.isLosslessArtworkActive.value)
            try {
                context.getSharedPreferences("library_scan_metadata", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("last_scan_mediastore_count", rawMediaStoreCount)
                    .apply()
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
                    uri = Uri.parse(entity.uri),
                    artworkUri = entity.artworkUri?.let { Uri.parse(it) },
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
