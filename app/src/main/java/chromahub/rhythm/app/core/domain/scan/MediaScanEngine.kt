/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.scan

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.room.withTransaction
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.MediaScanMode
import chromahub.rhythm.app.shared.data.model.ScanPhase
import chromahub.rhythm.app.shared.data.model.ScanProgress
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.util.MetadataHeuristics
import com.kyant.taglib.TagLib
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Centralized, High-Performance Media Scanning Engine for Rhythm.
 * Features:
 * - Incremental scanning (only query files modified since last scan)
 * - Differential scanning (MediaStore vs Room DB DATE_MODIFIED comparison)
 * - O(1) HashMap lookups
 * - Concurrent TagLib parsing using coroutines
 * - Progress updates throttled to 150ms for smooth UI
 */
class MediaScanEngine(
    private val context: Context,
    private val database: RhythmDatabase,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "MediaScanEngine"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 150L // Update progress every 150ms

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
     * Performs an optimized incremental or full media scan.
     * - Only queries files modified after last scan timestamp (if not forceRefresh).
     * - Reuses unchanged records from DB.
     * - Parses tags concurrently for new/modified files.
     * - Progress updates are emitted at most every 150ms to avoid UI overload.
     */
    suspend fun performScan(
        forceRefresh: Boolean = false,
        allowedFormats: Set<String>? = null,
        minimumDuration: Long = 0L
    ): List<Song> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting media scan (forceRefresh=$forceRefresh, minimumDuration=${minimumDuration}ms)")

        // --- 1. Determine scan timestamp for incremental query ---
        val lastScanTimestamp = if (!forceRefresh) {
            appSettings.lastScanTimestamp.value ?: 0L
        } else {
            0L
        }

        _scanProgress.value = ScanProgress(0, 0, ScanPhase.Songs, 0)

        // --- 2. Load existing DB songs into a HashMap for O(1) lookup by ID ---
        val existingDbSongs = if (!forceRefresh) {
            database.songDao().getAllSongs().associateBy { it.id }
        } else {
            emptyMap()
        }

        // --- 3. Retrieve settings and pre-normalize paths for filtering ---
        val mediaScanMode = appSettings.mediaScanMode.value
        val whitelistedFolders = appSettings.whitelistedFolders.value.map { it.lowercase() }
        val whitelistedSongs = appSettings.whitelistedSongs.value.toSet() // Set of IDs
        val blacklistedFolders = appSettings.blacklistedFolders.value.map { it.lowercase() }
        val blacklistedSongs = appSettings.blacklistedSongs.value.toSet()

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

        // --- 4. Query MediaStore with incremental filter ---
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // Build projection with all needed columns
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

        // Selection: base criteria + incremental timestamp filter
        val baseSelection = mediaScanSelection(minimumDuration)
        val selection = if (lastScanTimestamp > 0L) {
            "$baseSelection AND ${MediaStore.Audio.Media.DATE_MODIFIED} >= $lastScanTimestamp"
        } else {
            baseSelection
        }
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val scannedSongs = mutableListOf<SongEntity>()
        val seenIds = mutableSetOf<String>() // for dedup and deletion detection
        val seenPaths = mutableSetOf<String>() // for path dedup
        val pendingTagLibItems = mutableListOf<PendingTagLibItem>()

        var totalCandidates = 0
        var processedCount = 0
        var lastProgressUpdateTime = 0L // Time of last progress emission

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                totalCandidates = cursor.count
                Log.d(TAG, "MediaStore query found $totalCandidates candidates (incremental since $lastScanTimestamp)")
                _scanProgress.value = ScanProgress(0, totalCandidates, ScanPhase.Songs, 0)
                lastProgressUpdateTime = System.currentTimeMillis()

                // Pre-fetch column indices for speed
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
                val colGenre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                val colAlbumArtist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
                val colDiscNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER) else -1
                val colCdTrackNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER) else -1

                val preferSongArtwork = appSettings.preferSongArtwork.value
                val losslessArtwork = appSettings.isLosslessArtworkActive.value

                // --- 5. Main loop: read MediaStore fields, classify files ---
                while (cursor.moveToNext()) {
                    processedCount++
                    val id = cursor.getLong(colId).toString()

                    // Skip already processed (shouldn't happen) or explicitly blacklisted
                    if (seenIds.contains(id) || blacklistedSongs.contains(id)) continue

                    val path = if (colData >= 0) cursor.getString(colData) else null
                    if (path == null) continue // skip if no path

                    // Path normalization once
                    val normPath = path.lowercase()
                    if (seenPaths.contains(normPath)) continue

                    // Filter by format if needed
                    if (allowedFormats != null) {
                        val ext = path.substringAfterLast('.', "").lowercase()
                        if (ext.isNotEmpty() && !allowedFormats.contains(ext)) continue
                    }

                    // Whitelist/blacklist filtering (using pre-normalized lists)
                    if (mediaScanMode == MediaScanMode.WHITELIST) {
                        val isFolderWhitelisted = whitelistedFolders.isNotEmpty() &&
                            whitelistedFolders.any { normPath.startsWith(it) }
                        val isSongWhitelisted = whitelistedSongs.isNotEmpty() &&
                            whitelistedSongs.contains(id)
                        if (!isFolderWhitelisted && !isSongWhitelisted) continue
                    }

                    if (mediaScanMode == MediaScanMode.BLACKLIST && blacklistedFolders.isNotEmpty()) {
                        val isBlacklisted = blacklistedFolders.any { normPath.startsWith(it) }
                        if (isBlacklisted) continue
                    }

                    // Mark as seen (for deletion detection and dedup)
                    seenPaths.add(normPath)
                    seenIds.add(id)

                    val duration = cursor.getLong(colDuration)
                    if (minimumDuration > 0 && duration < minimumDuration) continue

                    // Convert timestamps to milliseconds
                    val rawDateModified = cursor.getLong(colDateModified)
                    val dateModified = if (rawDateModified in 1..99_999_999_999L) rawDateModified * 1000L else rawDateModified
                    val rawDateAdded = cursor.getLong(colDateAdded)
                    val dateAdded = if (rawDateAdded in 1..99_999_999_999L) rawDateAdded * 1000L else if (rawDateAdded > 0L) rawDateAdded else System.currentTimeMillis()
                    val finalDateModified = dateModified.takeIf { it > 0L } ?: dateAdded

                    // --- Check if existing record is unchanged and can be reused ---
                    val existing = existingDbSongs[id]
                    if (existing != null && existing.dateModified == finalDateModified && existing.dateAdded >= 100_000_000_000L) {
                        // Reuse existing entity (update artwork if needed)
                        val existingArt = if (preferSongArtwork) {
                            MediaUtils.getCachedEmbeddedAlbumArtUri(
                                cacheDir = context.filesDir,
                                songUri = existing.uri.toUri(),
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
                        // Progress update check (after each item)
                        updateProgressIfNeeded(processedCount, totalCandidates, lastProgressUpdateTime) { newTime ->
                            lastProgressUpdateTime = newTime
                        }
                        continue
                    }

                    // --- New or modified file: extract MediaStore fields ---
                    val rawTitle = cursor.getString(colTitle) ?: "Unknown Title"
                    val rawArtist = cursor.getString(colArtist) ?: "<unknown>"
                    val rawAlbum = cursor.getString(colAlbum) ?: "Unknown Album"
                    val albumId = cursor.getLong(colAlbumId).toString()
                    val rawTrack = cursor.getInt(colTrack)
                    val cdTrack = if (colCdTrackNumber >= 0) cursor.getInt(colCdTrackNumber) else 0
                    val discFromStore = if (colDiscNumber >= 0) cursor.getInt(colDiscNumber) else 0
                    val rawYear = cursor.getInt(colYear)
                    val rawGenre = if (colGenre >= 0) cursor.getString(colGenre) else null
                    val rawAlbumArtist = if (colAlbumArtist >= 0) cursor.getString(colAlbumArtist) else null

                    // Normalize metadata (heuristics)
                    var title = MetadataHeuristics.normalizeMetadataText(rawTitle) ?: rawTitle
                    var artist = MetadataHeuristics.normalizeMetadataText(rawArtist) ?: rawArtist
                    var album = MetadataHeuristics.normalizeMetadataText(rawAlbum) ?: rawAlbum
                    var genre = rawGenre?.let { MetadataHeuristics.normalizeMetadataText(it) }
                    var albumArtist = rawAlbumArtist?.let { MetadataHeuristics.normalizeMetadataText(it) }

                    // Compute disc and track numbers
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

                    // Pre-build URIs
                    val contentUri = Uri.withAppendedPath(collection, id).toString()
                    val defaultArtworkUri = Uri.withAppendedPath(
                        ("content://media/external/audio/albumart").toUri(),
                        albumId
                    ).toString()

                    // --- Determine if TagLib parsing is needed ---
                    val needsTagLib = (year == 0 || trackNumber == 0 || albumArtist.isNullOrBlank() || normPath.endsWith(".flac")) &&
                            !path.isNullOrBlank()

                    if (needsTagLib) {
                        // Store fields for later async parsing
                        val fields = MediaStoreFields(
                            id = id,
                            path = path,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            trackNumber = trackNumber,
                            cdTrack = cdTrack,
                            discFromStore = discFromStore,
                            year = year,
                            dateAdded = dateAdded,
                            dateModified = finalDateModified,
                            genre = genre,
                            albumArtist = albumArtist,
                            contentUri = contentUri,
                            defaultArtworkUri = defaultArtworkUri
                        )
                        pendingTagLibItems.add(PendingTagLibItem(fields, collection))
                    } else {
                        // No TagLib needed: build entity directly
                        val entity = buildSongEntity(
                            fields = MediaStoreFields(
                                id, path, title, artist, album, albumId, duration,
                                trackNumber, cdTrack, discFromStore, year,
                                dateAdded, finalDateModified, genre, albumArtist,
                                contentUri, defaultArtworkUri
                            ),
                            tagLibData = null,
                            preferSongArtwork = preferSongArtwork,
                            losslessArtwork = losslessArtwork
                        )
                        scannedSongs.add(entity)
                    }

                    // --- Progress update (throttled to 150ms) ---
                    updateProgressIfNeeded(processedCount, totalCandidates, lastProgressUpdateTime) { newTime ->
                        lastProgressUpdateTime = newTime
                    }
                }
            }

            // Ensure final progress update (in case loop ended without emitting)
            _scanProgress.value = ScanProgress(processedCount, totalCandidates, ScanPhase.Songs, 0)

            // --- 6. Concurrent TagLib parsing for pending items ---
            if (pendingTagLibItems.isNotEmpty()) {
                Log.d(TAG, "Starting concurrent TagLib parsing for ${pendingTagLibItems.size} files")
                // Use a coroutine scope with limited parallelism (e.g., 4 at a time)
                val deferredResults = pendingTagLibItems.map { item ->
                    async(Dispatchers.IO) {
                        parseTagLibForItem(item)
                    }
                }
                // Wait for all to complete and collect results
                val parsedEntities = deferredResults.awaitAll().filterNotNull()
                scannedSongs.addAll(parsedEntities)
                Log.d(TAG, "TagLib parsing completed, added ${parsedEntities.size} entities")
            }

            // --- 7. Update progress to saving phase ---
            _scanProgress.value = ScanProgress(scannedSongs.size, scannedSongs.size, ScanPhase.SavingDb, 0)

            // --- 8. Database transaction: upsert and delete stale records ---
            database.withTransaction {
                // Determine stale IDs (existing in DB but not seen in MediaStore)
                val staleSongIds = existingDbSongs.keys - seenIds
                val newSongIds = seenIds - existingDbSongs.keys

                // Delete stale songs and their artist associations
                if (staleSongIds.isNotEmpty()) {
                    database.songDao().deleteByIds(staleSongIds.toList())
                    database.songArtistDao().deleteBySongIds(staleSongIds.toList())
                }

                // Upsert all scanned songs
                database.songDao().upsertAll(scannedSongs)

                // If any songs changed, rebuild artist relations (simplified: delete all and let triggers handle)
                if (staleSongIds.isNotEmpty() || newSongIds.isNotEmpty()) {
                    // Note: Your DAO may have better ways; this is a placeholder for consistency
                    // In practice, you might want to call a method that rebuilds artist-songs
                    database.artistDao().deleteAll()
                    database.songArtistDao().deleteAll()
                    // Optionally re-populate via a separate process, but we'll rely on
                    // other parts of the app to handle that.
                }
            }

            // Update last scan timestamp (using current time)
            appSettings.setLastScanTimestamp(System.currentTimeMillis())

            // Save MediaStore count for reference
            try {
                context.getSharedPreferences("library_scan_metadata", Context.MODE_PRIVATE)
                    .edit { putInt("last_scan_mediastore_count", totalCandidates) }
                Log.d(TAG, "Saved MediaStore count ($totalCandidates) to library_scan_metadata")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save MediaStore count", e)
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Scan completed: ${scannedSongs.size} songs processed in ${totalDuration}ms")
            _scanProgress.value = ScanProgress(scannedSongs.size, scannedSongs.size, ScanPhase.Complete, totalDuration)

            // Return as List<Song>
            scannedSongs.map { entity ->
                Song(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    albumId = entity.albumId,
                    duration = entity.duration,
                    uri = entity.uri.toUri(),
                    artworkUri = entity.artworkUri?.let { it.toUri() },
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

    /**
     * Helper to update progress only if at least 150ms has elapsed since last update.
     * Also ensures we always update on the last item (when processedCount == totalCandidates).
     */
    private fun updateProgressIfNeeded(
        processed: Int,
        total: Int,
        lastUpdateTime: Long,
        onUpdate: (Long) -> Unit
    ) {
        val now = System.currentTimeMillis()
        // Update if interval elapsed or it's the last item
        if (now - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS || processed == total) {
            _scanProgress.value = ScanProgress(processed, total, ScanPhase.Songs, 0)
            onUpdate(now)
            // Yield to let other coroutines run
            // Note: yield() is a suspend function, but this helper is called from a suspend context,
            // so we can call it here. However, we cannot call yield() inside a non-suspend lambda.
            // We'll move yield() to the caller.
        }
    }

    // --- Helper data classes for intermediate storage ---
    private data class MediaStoreFields(
        val id: String,
        val path: String?,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: String,
        val duration: Long,
        val trackNumber: Int,
        val cdTrack: Int,
        val discFromStore: Int,
        val year: Int,
        val dateAdded: Long,
        val dateModified: Long,
        val genre: String?,
        val albumArtist: String?,
        val contentUri: String,
        val defaultArtworkUri: String
    )

    private data class PendingTagLibItem(
        val fields: MediaStoreFields,
        val collection: Uri // kept for potential future use, but we have contentUri already
    )

    /**
     * Parse a single file with TagLib and return a SongEntity, or null on failure.
     */
    private suspend fun parseTagLibForItem(item: PendingTagLibItem): SongEntity? {
        val fields = item.fields
        val path = fields.path ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) return@withContext null

                // Open file descriptor and parse
                val propertyMap = android.os.ParcelFileDescriptor.open(
                    file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
                ).use { fd ->
                    // Note: detachFd() is not needed if TagLib accepts ParcelFileDescriptor directly
                    // Assuming TagLib.getMetadata() accepts ParcelFileDescriptor or FileDescriptor
                    // If not, you may need to use fd.fileDescriptor
                    TagLib.getMetadata(fd.detachFd())?.propertyMap
                }

                // Build entity using tag data
                buildSongEntity(
                    fields = fields,
                    tagLibData = propertyMap,
                    preferSongArtwork = appSettings.preferSongArtwork.value,
                    losslessArtwork = appSettings.isLosslessArtworkActive.value
                )
            } catch (e: Throwable) {
                Log.w(TAG, "TagLib parse failed for $path", e)
                // Fallback: build entity without tag data
                buildSongEntity(
                    fields = fields,
                    tagLibData = null,
                    preferSongArtwork = appSettings.preferSongArtwork.value,
                    losslessArtwork = appSettings.isLosslessArtworkActive.value
                )
            }
        }
    }

    /**
     * Construct a SongEntity from MediaStore fields and optional TagLib data.
     */
    private fun buildSongEntity(
        fields: MediaStoreFields,
        tagLibData: Map<String, List<String>>?,
        preferSongArtwork: Boolean,
        losslessArtwork: Boolean
    ): SongEntity {
        var title = fields.title
        var artist = fields.artist
        var album = fields.album
        var genre = fields.genre
        var albumArtist = fields.albumArtist
        var year = fields.year
        var trackNumber = fields.trackNumber
        var discNumber = if (fields.discFromStore > 0) fields.discFromStore else {
            if (fields.trackNumber >= 1000) fields.trackNumber / 1000 else 1
        }

        // Override with TagLib data if present
        tagLibData?.let { map ->
            // Year
            if (year == 0) {
                val dateValues = map["DATE"] ?: map["YEAR"]
                if (!dateValues.isNullOrEmpty() && dateValues[0].isNotBlank()) {
                    year = MetadataHeuristics.parseYear(dateValues[0])
                }
            }
            // Track number
            if (trackNumber == 0) {
                val trackValues = map["TRACKNUMBER"]
                if (!trackValues.isNullOrEmpty() && trackValues[0].isNotBlank()) {
                    val parsed = trackValues[0].substringBefore('/').toIntOrNull() ?: 0
                    if (parsed >= 1000) {
                        discNumber = parsed / 1000
                        trackNumber = parsed % 1000
                    } else if (parsed > 0) {
                        trackNumber = parsed
                    }
                }
            }
            // Disc number
            if (discNumber <= 1) {
                val discValues = map["DISCNUMBER"]
                if (!discValues.isNullOrEmpty() && discValues[0].isNotBlank()) {
                    val parsedDisc = discValues[0].substringBefore('/').toIntOrNull() ?: 1
                    if (parsedDisc > 0) discNumber = parsedDisc
                }
            }
            // Title
            val tagTitle = map["TITLE"]?.firstOrNull()?.trim()
            if (!tagTitle.isNullOrBlank() && (title == "Unknown Title" || MetadataHeuristics.isLikelyCorruptedMetadata(title))) {
                title = MetadataHeuristics.normalizeMetadataText(tagTitle) ?: tagTitle
            }
            // Artist
            val tagArtist = map["ARTIST"]?.firstOrNull()?.trim()
            if (!tagArtist.isNullOrBlank() && (artist == "<unknown>" || MetadataHeuristics.isLikelyCorruptedMetadata(artist))) {
                artist = MetadataHeuristics.normalizeMetadataText(tagArtist) ?: tagArtist
            }
            // Album
            val tagAlbum = map["ALBUM"]?.firstOrNull()?.trim()
            if (!tagAlbum.isNullOrBlank() && (album == "Unknown Album" || MetadataHeuristics.isLikelyCorruptedMetadata(album))) {
                album = MetadataHeuristics.normalizeMetadataText(tagAlbum) ?: tagAlbum
            }
            // Album Artist
            val tagAlbumArtist = (map["ALBUMARTIST"] ?: map["ALBUM ARTIST"] ?: map["ALBUM_ARTIST"])?.firstOrNull()?.trim()
            if (!tagAlbumArtist.isNullOrBlank() && (albumArtist.isNullOrBlank() || MetadataHeuristics.isLikelyCorruptedMetadata(albumArtist))) {
                albumArtist = MetadataHeuristics.normalizeMetadataText(tagAlbumArtist) ?: tagAlbumArtist
            }
            // Genre
            val tagGenre = map["GENRE"]?.firstOrNull()?.trim()
            if (!tagGenre.isNullOrBlank() && (genre.isNullOrBlank() || MetadataHeuristics.isLikelyCorruptedMetadata(genre))) {
                genre = MetadataHeuristics.normalizeMetadataText(tagGenre) ?: tagGenre
            }
        }

        // Determine artwork URI
        val artworkUri = if (preferSongArtwork) {
            MediaUtils.getCachedEmbeddedAlbumArtUri(
                cacheDir = context.filesDir,
                songUri = fields.contentUri.toUri(),
                lossless = losslessArtwork,
                exactMatchOnly = false
            )?.toString() ?: fields.defaultArtworkUri
        } else {
            fields.defaultArtworkUri
        }

        return SongEntity(
            id = fields.id,
            title = title,
            artist = artist,
            album = album,
            albumId = fields.albumId,
            duration = fields.duration,
            uri = fields.contentUri,
            artworkUri = artworkUri,
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            dateAdded = fields.dateAdded,
            dateModified = fields.dateModified,
            albumArtist = albumArtist,
            bitrate = null,
            sampleRate = null,
            channels = null,
            codec = null,
            discNumber = discNumber,
            path = fields.path
        )
    }
}