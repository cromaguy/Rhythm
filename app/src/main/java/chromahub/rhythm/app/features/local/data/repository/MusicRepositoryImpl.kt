package chromahub.rhythm.app.features.local.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.network.DeezerApiService
import chromahub.rhythm.app.network.DeezerArtist
import chromahub.rhythm.app.network.DeezerAlbum
import chromahub.rhythm.app.network.YTMusicApiService
import chromahub.rhythm.app.network.YTMusicSearchRequest
import chromahub.rhythm.app.network.YTMusicContext
import chromahub.rhythm.app.network.YTMusicClient
import chromahub.rhythm.app.network.YTMusicBrowseRequest
import chromahub.rhythm.app.network.extractArtistImageUrl
import chromahub.rhythm.app.network.extractAlbumImageUrl
import chromahub.rhythm.app.network.extractArtistBrowseId
import chromahub.rhythm.app.network.extractAlbumBrowseId
import chromahub.rhythm.app.network.extractArtistThumbnail
import chromahub.rhythm.app.network.extractAlbumCover
import okhttp3.Request
import com.google.gson.JsonParser
import com.google.gson.Gson
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URL
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.LyricsSourcePreference
import chromahub.rhythm.app.shared.data.model.UserAudioDevice
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.core.domain.model.PlayableItem
import java.lang.ref.WeakReference
import chromahub.rhythm.app.util.AudioFormatDetector
import chromahub.rhythm.app.util.LyricsParser
import chromahub.rhythm.app.util.EnhancedLyricLine
import chromahub.rhythm.app.util.EnhancedWord
// AppleMusicLyricsParser import removed - kept for future re-implementation
import android.content.SharedPreferences

/**
 * Scan progress data class for real-time updates
 */
data class ScanProgress(
    val current: Int,
    val total: Int,
    val stage: String, // "Songs", "Albums", "Artists", "Metadata"
    val estimatedTimeMs: Long = 0
)

/**
 * Scan statistics for tracking library state
 */
data class ScanStatistics(
    val lastScanTime: Long,
    val scanDuration: Long,
    val totalSongs: Int,
    val filteredSongs: Int,
    val totalAlbums: Int,
    val totalArtists: Int,
    val storageUsedBytes: Long,
    val averageBitrate: Int,
    val duplicatesFound: Int
)

/**
 * Scan history entry
 */
data class ScanHistoryEntry(
    val timestamp: Long,
    val songsAdded: Int,
    val songsRemoved: Int,
    val duration: Long,
    val errorCount: Int
)

/**
 * Folder suggestion for smart blacklisting
 */
data class FolderSuggestion(
    val path: String,
    val reason: String,
    val songCount: Int,
    val confidence: Double
)

class MusicRepository(context: Context) {
    private val TAG = "MusicRepository"
    // Use WeakReference to prevent context leaks, but store applicationContext which is safe
    private val contextRef = WeakReference(context.applicationContext)
    private val context: Context
        get() = contextRef.get() ?: throw IllegalStateException("Context has been garbage collected")
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Genre cache using SharedPreferences
    private val genrePrefs: SharedPreferences by lazy { context.getSharedPreferences("genre_cache", Context.MODE_PRIVATE) }
    
    // Scan progress tracking
    private val _scanProgress = MutableStateFlow(ScanProgress(0, 0, "Idle"))
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()
    
    // Scan history
    private val _scanHistory = MutableStateFlow<List<ScanHistoryEntry>>(emptyList())
    val scanHistory: StateFlow<List<ScanHistoryEntry>> = _scanHistory.asStateFlow()
    
    // Song cache to avoid duplicate MediaStore queries
    private var cachedSongs: List<Song>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_VALIDITY_MS = 60_000 // 1 minute
    
    // ContentObserver for automatic updates
    private var mediaStoreObserver: ContentObserver? = null
    private var onMediaStoreChangeCallback: (() -> Unit)? = null

    /**
     * API Fallback Strategy:
     * 
     * ARTIST IMAGES:
     * 1. Check local cache/storage
     * 2. Deezer API (primary)
     * 3. YouTube Music (fallback)
     * 4. Placeholder generation
     * 
     * ALBUM ARTWORK:
     * 1. Check existing local album art
     * 2. Check cache
     * 3. Deezer API (primary)
     * 4. YouTube Music (only when local album art is absent)
     * 5. Placeholder generation
     * 
     * TRACK IMAGES:
     * 1. Check if track already has artwork
     * 2. Check if album has artwork (inherit from album)
     * 3. YouTube Music (fallback when no local artwork available)
     */
    
    private val deezerApiService = NetworkClient.deezerApiService
    private val lrclibApiService = NetworkClient.lrclibApiService
    private val ytmusicApiService = NetworkClient.ytmusicApiService
    // Apple Music API service removed - can be re-added in future
    private val genericHttpClient = NetworkClient.genericHttpClient
    
    // Note: API services can be null if disabled via BuildConfig

    /**
     * Register ContentObserver to monitor MediaStore changes
     */
    fun registerMediaStoreObserver(onChange: () -> Unit) {
        if (mediaStoreObserver != null) {
            Log.d(TAG, "ContentObserver already registered")
            return
        }
        
        onMediaStoreChangeCallback = onChange
        mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "MediaStore changed, triggering callback")
                onChange()
            }
        }
        
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver!!
        )
        Log.d(TAG, "ContentObserver registered for MediaStore changes")
    }
    
    /**
     * Unregister ContentObserver
     */
    fun unregisterMediaStoreObserver() {
        mediaStoreObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            mediaStoreObserver = null
            onMediaStoreChangeCallback = null
            Log.d(TAG, "ContentObserver unregistered")
        }
    }

    // LRU caches for artist images, album artwork, and lyrics to avoid memory leaks
    private val artistImageCache = object : LinkedHashMap<String, Uri?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Uri?>?): Boolean {
            return size > MAX_ARTIST_CACHE_SIZE
        }
    }
    private val albumImageCache = object : LinkedHashMap<String, Uri?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Uri?>?): Boolean {
            return size > MAX_ALBUM_CACHE_SIZE
        }
    }
    private val lyricsCache = object : LinkedHashMap<String, LyricsData>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, LyricsData>?): Boolean {
            return size > MAX_LYRICS_CACHE_SIZE
        }
    }
    
    // Rate limiting for API calls
    private val lastApiCalls = mutableMapOf<String, Long>()
    private val apiCallCounts = mutableMapOf<String, Int>()
    
    companion object {
        private const val MAX_ARTIST_CACHE_SIZE = 100
        private const val MAX_ALBUM_CACHE_SIZE = 200
        private const val MAX_LYRICS_CACHE_SIZE = 150
        
        // API rate limiting constants
        private const val DEEZER_MIN_DELAY = 200L
        private const val YTMUSIC_MIN_DELAY = 300L
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
    }
    
    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "deezer" -> DEEZER_MIN_DELAY
            "ytmusic" -> YTMUSIC_MIN_DELAY
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }
        
        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }
        
        // Check if we're making too many calls per minute
        val callsInLastMinute = apiCallCounts[apiName] ?: 0
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }
        
        return 0L
    }
    
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp
        
        // Update call count for rate limiting
        val currentCount = apiCallCounts[apiName] ?: 0
        apiCallCounts[apiName] = currentCount + 1
        
        // Reset counter every minute
        if (currentCount == 0) {
            repositoryScope.launch {
                delay(60000)
                apiCallCounts[apiName] = 0
            }
        }
    }

    suspend fun loadSongs(
        forceRefresh: Boolean = false,
        allowedFormats: Set<String>? = null,
        minimumBitrate: Int = 0,
        minimumDuration: Long = 0L
    ): List<Song> = withContext(Dispatchers.IO) {
        // Check cache first
        if (!forceRefresh && 
            cachedSongs != null && 
            System.currentTimeMillis() - cacheTimestamp < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Returning cached songs (${cachedSongs!!.size})")
            return@withContext cachedSongs!!
        }
        
        val startTime = System.currentTimeMillis()
        val songs = mutableListOf<Song>()
        val errors = mutableListOf<Pair<Int, Exception>>()
        val seenIds = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        var duplicatesFound = 0
        var filteredByFormat = 0
        var filteredByQuality = 0
        
        Log.d(TAG, "Starting media scan on Android ${Build.VERSION.SDK_INT} (API ${Build.VERSION.SDK_INT})")
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        Log.d(TAG, "Using MediaStore URI: $collection")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATA // For path-based duplicate detection
        )

        // Improved selection to filter out very short files and invalid entries
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            _scanProgress.value = ScanProgress(0, 0, "Songs", 0)
            
            Log.d(TAG, "Querying MediaStore with selection: $selection")
            
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val count = cursor.count
                Log.d(TAG, "MediaStore query successful: Found $count audio files to process")
                _scanProgress.value = ScanProgress(0, count, "Songs", 0)
                
                if (count == 0) {
                    Log.w(TAG, "No audio files found in MediaStore - this may indicate a permission or storage access issue on Android ${Build.VERSION.SDK_INT}")
                    Log.w(TAG, "Please verify:")
                    Log.w(TAG, "1. Storage permission is granted")
                    Log.w(TAG, "2. Files are visible in MediaStore (try MediaScanner)")
                    Log.w(TAG, "3. Files meet criteria: IS_MUSIC=1 AND DURATION>10000ms")
                    return@withContext emptyList()
                }

                // Pre-allocate list with known size for better performance
                if (songs is ArrayList) {
                    songs.ensureCapacity(count)
                }
                
                // Cache all column indices once
                val columnIndices = try {
                    ColumnIndices(
                        id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                        title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                        artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                        album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                        albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                        duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                        track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK),
                        year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR),
                        dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED),
                        size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
                        genre = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE),
                        albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) // May be -1 on older devices
                    )
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Required column not found in MediaStore on Android ${Build.VERSION.SDK_INT}", e)
                    _scanProgress.value = ScanProgress(0, 0, "Error", 0)
                    return@withContext emptyList()
                }

                var processedCount = 0
                val batchSize = 100
                val pathColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    try {
                        val song = createSongFromCursor(cursor, columnIndices)
                        if (song != null) {
                            // Duplicate detection by ID
                            if (seenIds.contains(song.id)) {
                                Log.d(TAG, "Skipping duplicate ID: ${song.id} - ${song.title}")
                                duplicatesFound++
                                processedCount++
                                continue
                            }
                            
                            // Duplicate detection by path
                            if (pathColumnIndex >= 0) {
                                val path = cursor.getString(pathColumnIndex)
                                if (path != null && seenPaths.contains(path)) {
                                    Log.d(TAG, "Skipping duplicate path: $path - ${song.title}")
                                    duplicatesFound++
                                    processedCount++
                                    continue
                                }
                                
                                // Format filtering
                                if (allowedFormats != null && path.isNotEmpty()) {
                                    val extension = path.substringAfterLast('.', "").lowercase()
                                    if (extension.isNotEmpty() && !allowedFormats.contains(extension)) {
                                        filteredByFormat++
                                        processedCount++
                                        continue
                                    }
                                }
                                
                                seenPaths.add(path)
                            }
                            
                            // Duration filtering (quality check)
                            if (minimumDuration > 0 && song.duration < minimumDuration) {
                                filteredByQuality++
                                processedCount++
                                continue
                            }
                            
                            // Note: Bitrate filtering is done later since we need to extract metadata
                            // For now, we add the song and can filter in post-processing if needed
                            
                            seenIds.add(song.id)
                            songs.add(song)
                        }
                        
                        processedCount++
                        
                        // Update progress periodically
                        if (processedCount % 10 == 0) {
                            _scanProgress.value = ScanProgress(processedCount, count, "Songs", 0)
                        }
                        
                        // Yield control periodically to avoid blocking
                        if (processedCount % batchSize == 0) {
                            yield()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing song at position ${cursor.position}", e)
                        errors.add(cursor.position to e)
                        processedCount++
                        continue
                    }
                }
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                Log.d(TAG, "Loaded ${songs.size} songs in ${duration}ms")
                Log.d(TAG, "Filtering stats - Duplicates: $duplicatesFound, Format: $filteredByFormat, Quality: $filteredByQuality, Errors: ${errors.size}")
                
                // Update cache
                cachedSongs = songs
                cacheTimestamp = System.currentTimeMillis()
                
                // Update scan progress to complete
                _scanProgress.value = ScanProgress(songs.size, count, "Complete", duration)
                
                // Log errors if any
                if (errors.isNotEmpty()) {
                    Log.w(TAG, "Scan completed with ${errors.size} errors. First error: ${errors.first().second.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during song scan", e)
            _scanProgress.value = ScanProgress(0, 0, "Error", 0)
            // Return partial results if available
            return@withContext songs
        }

        return@withContext songs
    }
    
    /**
     * Perform incremental scan for newly added songs only
     */
    suspend fun performIncrementalScan(
        lastScanTimestamp: Long,
        allowedFormats: Set<String>? = null,
        minimumBitrate: Int = 0,
        minimumDuration: Long = 0L
    ): List<Song> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting incremental scan since timestamp: $lastScanTimestamp")
        val startTime = System.currentTimeMillis()
        val newSongs = mutableListOf<Song>()
        var filteredByFormat = 0
        var filteredByQuality = 0
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.GENRE
        )

        // Only scan songs added after last scan
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > 10000 AND ${MediaStore.Audio.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((lastScanTimestamp / 1000).toString())
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val count = cursor.count
                Log.d(TAG, "Found $count new audio files")
                _scanProgress.value = ScanProgress(0, count, "Incremental", 0)
                
                if (count == 0) {
                    return@withContext emptyList()
                }

                val columnIndices = try {
                    ColumnIndices(
                        id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                        title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                        artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                        album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                        albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                        duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                        track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK),
                        year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR),
                        dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED),
                        size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
                        genre = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE),
                        albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    )
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Required column not found in MediaStore", e)
                    return@withContext emptyList()
                }

                var processedCount = 0
                val pathColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    try {
                        val song = createSongFromCursor(cursor, columnIndices)
                        if (song != null) {
                            // Format filtering
                            if (allowedFormats != null && pathColumnIndex >= 0) {
                                val path = cursor.getString(pathColumnIndex)
                                if (path != null && path.isNotEmpty()) {
                                    val extension = path.substringAfterLast('.', "").lowercase()
                                    if (extension.isNotEmpty() && !allowedFormats.contains(extension)) {
                                        filteredByFormat++
                                        processedCount++
                                        continue
                                    }
                                }
                            }
                            
                            // Duration filtering
                            if (minimumDuration > 0 && song.duration < minimumDuration) {
                                filteredByQuality++
                                processedCount++
                                continue
                            }
                            
                            newSongs.add(song)
                        }
                        processedCount++
                        if (processedCount % 10 == 0) {
                            _scanProgress.value = ScanProgress(processedCount, count, "Incremental", 0)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing new song at position ${cursor.position}", e)
                    }
                }
                
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Incremental scan complete: ${newSongs.size} new songs in ${duration}ms")
                Log.d(TAG, "Filtering stats - Format: $filteredByFormat, Quality: $filteredByQuality")
                _scanProgress.value = ScanProgress(newSongs.size, count, "Complete", duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during incremental scan", e)
        }

        return@withContext newSongs
    }
    
    private data class ColumnIndices(
        val id: Int,
        val title: Int,
        val artist: Int,
        val album: Int,
        val albumId: Int,
        val duration: Int,
        val track: Int,
        val year: Int,
        val dateAdded: Int,
        val size: Int,
        val genre: Int,
        val albumArtist: Int // May be -1 if not available on older devices
    )
    
    private fun createSongFromCursor(cursor: android.database.Cursor, indices: ColumnIndices): Song? {
        return try {
            val id = cursor.getLong(indices.id)
            val title = cursor.getString(indices.title)?.trim() ?: return null
            val rawArtist = cursor.getString(indices.artist)?.trim() ?: "Unknown Artist"
            
            // Parse multiple artists from the artist string using configured delimiters
            val appSettings = AppSettings.getInstance(context)
            val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
            val delimiters = appSettings.artistSeparatorDelimiters.value
            
            // Get primary artist for the Song object (first artist in the list)
            val artist = chromahub.rhythm.app.util.ArtistSeparator.getPrimaryArtist(
                rawArtist,
                delimiters,
                artistSeparatorEnabled
            )
            
            val album = cursor.getString(indices.album)?.trim() ?: "Unknown Album"
            val albumId = cursor.getLong(indices.albumId)
            val duration = cursor.getLong(indices.duration)
            val track = cursor.getInt(indices.track)
            val year = cursor.getInt(indices.year)
            val dateAdded = cursor.getLong(indices.dateAdded) * 1000L
            val size = cursor.getLong(indices.size)
            val genreId = cursor.getString(indices.genre)?.trim()
            val albumArtist = if (indices.albumArtist >= 0) {
                cursor.getString(indices.albumArtist)?.trim()?.takeIf { it.isNotBlank() }
            } else null

            // Skip files that are too small (likely invalid)
            if (size < 1024) { // Less than 1KB
                Log.d(TAG, "Skipping file too small: $title ($size bytes)")
                return null
            }

            // Skip files with empty titles
            if (title.isBlank() || title.equals("<unknown>", ignoreCase = true)) {
                Log.d(TAG, "Skipping file with invalid title: $title")
                return null
            }

            val contentUri: Uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )

            // Determine album art URI based on user preference
            val ignoreMediaStoreCovers = appSettings.ignoreMediaStoreCovers.value
            
            val albumArtUri = if (ignoreMediaStoreCovers) {
                // Extract embedded album art directly from file
                chromahub.rhythm.app.util.MediaUtils.extractEmbeddedAlbumArt(context, contentUri, context.cacheDir)
                    ?: ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ) // Fallback to MediaStore if no embedded art
            } else {
                // Use MediaStore album art (default behavior)
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
            }

            // Load cached genre if available
            val cachedGenre = try {
                genrePrefs.getString("genre_$id", null)?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cached genre for song ID $id", e)
                null
            }
            if (cachedGenre != null) {
                Log.d(TAG, "Loaded cached genre '$cachedGenre' for song ID $id: $title")
            }

            // Note: Audio metadata extraction moved to background task to avoid blocking initial scan
            // Use extractAudioMetadata() separately when needed for detailed info

            Song(
                id = id.toString(),
                title = title,
                artist = artist,
                album = album,
                albumId = albumId.toString(),
                duration = duration,
                uri = contentUri,
                artworkUri = albumArtUri,
                trackNumber = track,
                year = year,
                dateAdded = dateAdded,
                genre = cachedGenre, // Use cached genre if available
                albumArtist = albumArtist,
                bitrate = null, // Will be extracted lazily when needed
                sampleRate = null,
                channels = null,
                codec = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error creating song from cursor", e)
            null
        }
    }
    
    /**
     * Data class to hold audio metadata
     */
    private data class AudioMetadata(
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val codec: String? = null
    )
    
    /**
     * Extract audio quality metadata using MediaMetadataRetriever
     */
    private fun extractAudioMetadata(uri: Uri): AudioMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        var extractor: android.media.MediaExtractor? = null
        
        try {
            retriever.setDataSource(context, uri)
            
            val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
            
            val sampleRateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            val sampleRate = sampleRateStr?.toIntOrNull()
            
            // Extract number of channels using MediaExtractor (correct way)
            var channels: Int? = null
            try {
                extractor = android.media.MediaExtractor()
                extractor.setDataSource(context, uri, null)
                
                // Find the audio track
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                    
                    if (mime?.startsWith("audio/") == true) {
                        // Get channel count from MediaFormat
                        if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error extracting channel count with MediaExtractor: ${e.message}")
            }
            
            // Default to stereo if channel count couldn't be determined
            if (channels == null || channels <= 0) {
                channels = 2
            }
            
            // Get codec/mime type
            val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            var codec = mimeType?.let { 
                when {
                    it.contains("mp3", ignoreCase = true) -> "MP3"
                    it.contains("aac", ignoreCase = true) -> "AAC"
                    it.contains("flac", ignoreCase = true) -> "FLAC"
                    it.contains("alac", ignoreCase = true) -> "ALAC"
                    it.contains("opus", ignoreCase = true) -> "Opus"
                    it.contains("vorbis", ignoreCase = true) -> "Vorbis"
                    it.contains("wav", ignoreCase = true) -> "WAV"
                    it.contains("m4a", ignoreCase = true) -> "M4A"
                    else -> it.substringAfter("/").uppercase()
                }
            }
            
            // For M4A containers, try to detect if it's ALAC (lossless) or AAC (lossy)
            // ALAC typically has bitrate > 700 kbps, AAC is usually < 320 kbps
            if (codec == "M4A" && bitrate != null) {
                codec = if (bitrate >= 700000) "ALAC" else "AAC"
                Log.d(TAG, "M4A container detected: bitrate=${bitrate/1000}kbps, classified as $codec")
            }
            
            return AudioMetadata(bitrate, sampleRate, channels, codec)
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting audio metadata for $uri: ${e.message}")
            return AudioMetadata()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever: ${e.message}")
            }
            try {
                extractor?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaExtractor: ${e.message}")
            }
        }
    }

    /**
     * Enhanced genre detection with multiple fallback methods
     * @param context The application context
     * @param songUri The URI of the song file
     * @param songId The song ID for MediaStore queries
     * @return The detected genre name, or null if not found
     */
    private fun getGenreForSong(context: Context, songUri: Uri, songId: Int): String? {
        // Method 1: Try MediaStore.Audio.Media.GENRE column (may contain genre ID or name)
        try {
            val genreFromMediaStoreColumn = getGenreFromMediaStoreColumn(songId)
            if (!genreFromMediaStoreColumn.isNullOrBlank()) {
                Log.d(TAG, "Found genre from MediaStore column: $genreFromMediaStoreColumn for song ID: $songId")
                return genreFromMediaStoreColumn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from MediaStore column", e)
        }

        // Method 2: Try MediaStore.Audio.Genres table lookup
        try {
            val genreFromGenresTable = getGenreNameFromMediaStore(context.contentResolver, songId)
            if (!genreFromGenresTable.isNullOrBlank()) {
                Log.d(TAG, "Found genre from Genres table: $genreFromGenresTable for song ID: $songId")
                return genreFromGenresTable
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from Genres table", e)
        }

        // Method 3: Try MediaMetadataRetriever
        try {
            val genreFromRetriever = getGenreFromMediaMetadataRetriever(songUri)
            if (!genreFromRetriever.isNullOrBlank()) {
                Log.d(TAG, "Found genre from MediaMetadataRetriever: $genreFromRetriever for song URI: $songUri")
                return genreFromRetriever
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from MediaMetadataRetriever", e)
        }

        // Method 4: Try to infer genre from file path or filename patterns
        try {
            val genreFromPath = inferGenreFromPath(songUri)
            if (!genreFromPath.isNullOrBlank()) {
                Log.d(TAG, "Inferred genre from path: $genreFromPath for song URI: $songUri")
                return genreFromPath
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to infer genre from path", e)
        }

        Log.d(TAG, "No genre found for song ID: $songId, URI: $songUri")
        return null
    }

    /**
     * Gets genre directly from MediaStore.Audio.Media.GENRE column
     * This column may contain either a genre ID or genre name depending on Android version
     */
    private fun getGenreFromMediaStoreColumn(songId: Int): String? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media.GENRE)
            val selection = "${MediaStore.Audio.Media._ID} = ?"
            val selectionArgs = arrayOf(songId.toString())

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val genreIndex = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    if (genreIndex != -1) {
                        val genreValue = cursor.getString(genreIndex)?.trim()
                        if (!genreValue.isNullOrBlank()) {
                            // Check if it's a numeric genre ID or a genre name
                            val genreId = genreValue.toLongOrNull()
                            if (genreId != null && genreId > 0) {
                                // It's a genre ID, try to convert it to name
                                return getGenreNameFromId(context.contentResolver, genreId)
                            } else {
                                // It's already a genre name
                                return genreValue
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting genre from MediaStore column", e)
            null
        }
    }

    /**
     * Converts a genre ID to genre name using the Genres table
     */
    private fun getGenreNameFromId(contentResolver: android.content.ContentResolver, genreId: Long): String? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Genres.NAME)
            val selection = "${MediaStore.Audio.Genres._ID} = ?"
            val selectionArgs = arrayOf(genreId.toString())

            contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)?.trim()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting genre ID to name", e)
            null
        }
    }

    /**
     * Gets genre from MediaMetadataRetriever
     */
    private fun getGenreFromMediaMetadataRetriever(songUri: Uri): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, songUri)
            val genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
            genre?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting genre from MediaMetadataRetriever", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Attempts to infer genre from file path or filename patterns
     */
    private fun inferGenreFromPath(songUri: Uri): String? {
        return try {
            val path = songUri.toString().lowercase()

            // Common genre patterns in file paths
            when {
                path.contains("rock") -> "Rock"
                path.contains("pop") -> "Pop"
                path.contains("hip.hop") || path.contains("hiphop") || path.contains("rap") -> "Hip Hop"
                path.contains("jazz") -> "Jazz"
                path.contains("classical") || path.contains("classic") -> "Classical"
                path.contains("electronic") || path.contains("electro") || path.contains("edm") -> "Electronic"
                path.contains("country") -> "Country"
                path.contains("blues") -> "Blues"
                path.contains("reggae") -> "Reggae"
                path.contains("folk") -> "Folk"
                path.contains("metal") -> "Metal"
                path.contains("punk") -> "Punk"
                path.contains("indie") -> "Indie"
                path.contains("alternative") -> "Alternative"
                path.contains("r&b") || path.contains("rnb") -> "R&B"
                path.contains("soul") -> "Soul"
                path.contains("funk") -> "Funk"
                path.contains("disco") -> "Disco"
                path.contains("dance") -> "Dance"
                path.contains("house") -> "House"
                path.contains("techno") -> "Techno"
                path.contains("trance") -> "Trance"
                path.contains("ambient") -> "Ambient"
                path.contains("soundtrack") || path.contains("ost") -> "Soundtrack"
                path.contains("instrumental") -> "Instrumental"
                path.contains("vocal") -> "Vocal"
                path.contains("christmas") || path.contains("holiday") -> "Holiday"
                path.contains("world") -> "World"
                path.contains("latin") -> "Latin"
                path.contains("african") -> "African"
                path.contains("asian") -> "Asian"
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inferring genre from path", e)
            null
        }
    }

    /**
     * Gets the genre name from MediaStore.Audio.Genres table using the song ID
     * @param contentResolver The ContentResolver to use for queries
     * @param songId The song ID to look up genre for
     * @return The genre name, or null if not found
     */
    private fun getGenreNameFromMediaStore(contentResolver: android.content.ContentResolver, songId: Int): String? {
        return try {
            // Try to get genre directly from the URI - works on newer Android versions
            val genreUri = android.provider.MediaStore.Audio.Genres.getContentUriForAudioId("external", songId)
            val projection = arrayOf(android.provider.MediaStore.Audio.Genres.NAME)
            
            contentResolver.query(
                genreUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Genres.NAME)
                    val genreName = cursor.getString(nameIndex)
                    if (!genreName.isNullOrBlank()) {
                        Log.d(TAG, "Found genre: $genreName for song ID: $songId")
                        return genreName
                    }
                }
            }

            null
        } catch (e: IllegalArgumentException) {
            // Column doesn't exist on this Android version - silently ignore
            Log.d(TAG, "Genre column not available on this device (Android API limitation)")
            null
        } catch (e: Exception) {
            // Other errors - log but don't spam
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Could not get genre for song ID: $songId: ${e.message}")
            }
            null
        }
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<Album>()
        val collection = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.FIRST_YEAR
        )

        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        // Load all songs once
        val allSongs = loadSongs()
        val songsByAlbumTitle = allSongs.groupBy { it.album }

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            // Cache column indices
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songsCountColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.FIRST_YEAR)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(albumColumn) ?: continue // Skip if null
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val songsCount = cursor.getInt(songsCountColumn)
                val year = cursor.getInt(yearColumn)

                // Get songs for this album from the pre-loaded map
                val albumSongs = songsByAlbumTitle[title] ?: emptyList()
                
                // Use album art from the first song in the album if available
                // This ensures consistency with the song's album art (whether embedded or from MediaStore)
                val albumArtUri = albumSongs.firstOrNull()?.artworkUri 
                    ?: ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        id
                    )

                val album = Album(
                    id = id.toString(),
                    title = title,
                    artist = artist,
                    artworkUri = albumArtUri,
                    year = year,
                    songs = albumSongs,
                    numberOfSongs = albumSongs.size
                )
                albums.add(album)
            }
        }

        Log.d(TAG, "Loaded ${albums.size} albums")
        albums
    }

    /**
     * Loads artists from device storage with enhanced metadata extraction.
     * Supports grouping by album artist or track artist based on user preference.
     */
    suspend fun loadArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val appSettings = AppSettings.getInstance(context)
        val groupByAlbumArtist = appSettings.groupByAlbumArtist.value
        
        Log.d(TAG, "Loading artists (groupByAlbumArtist=$groupByAlbumArtist)")
        
        if (groupByAlbumArtist) {
            // New method: Group by album artist from songs
            loadArtistsGroupedByAlbumArtist()
        } else {
            // Original method: Use MediaStore.Audio.Artists (grouped by track artist)
            loadArtistsFromMediaStore()
        }
    }
    
    /**
     * Original method: Loads artists using MediaStore.Audio.Artists
     * This groups by track artist, showing collaborations as separate entries
     */
    private suspend fun loadArtistsFromMediaStore(): List<Artist> = withContext(Dispatchers.IO) {
        // Load all songs to properly count tracks/albums for split artists
        val allSongs = loadSongs()
        val artistMap = mutableMapOf<String, MutableList<Song>>()
        val albumsByArtist = mutableMapOf<String, MutableSet<String>>()
        
        // Group songs by individual track artists (splitting collaborations)
        for (song in allSongs) {
            // Split artist names on common separators
            val artistNames = splitArtistNames(song.artist)
            
            for (artistName in artistNames) {
                val cleanName = artistName.trim()
                
                // Skip invalid artist names
                if (cleanName.isBlank() || cleanName.equals("<unknown>", ignoreCase = true)) {
                    continue
                }
                
                // Add song to artist's collection
                artistMap.getOrPut(cleanName) { mutableListOf() }.add(song)
                
                // Track unique albums for this artist
                if (song.album.isNotBlank()) {
                    albumsByArtist.getOrPut(cleanName) { mutableSetOf() }.add(song.album)
                }
            }
        }
        
        // Create Artist objects from grouped data
        val artists = artistMap.map { (artistName, songs) ->
            val albums = albumsByArtist[artistName] ?: emptySet()
            Artist(
                id = "track_artist_${artistName.hashCode()}", // Generate unique ID based on name
                name = artistName,
                numberOfAlbums = albums.size,
                numberOfTracks = songs.size
            )
        }.sortedBy { it.name.lowercase() }

        Log.d(TAG, "Loaded ${artists.size} artists from track artists (split collaborations)")
        artists
    }
    
    /**
     * Splits artist names on common collaboration separators.
     * Returns a list of individual artist names.
     */
    fun splitArtistNames(artistName: String): List<String> {
        // Common separators for collaborations
        val separators = listOf(
            " & ",
            " and ",
            ", ",
            " feat. ",
            " feat ",
            " ft. ",
            " ft ",
            " featuring ",
            " x ",
            " X ",
            " vs ",
            " vs. ",
            " with "
        )
        
        var names = listOf(artistName)
        
        // Split on each separator
        for (separator in separators) {
            names = names.flatMap { it.split(separator, ignoreCase = true) }
        }
        
        return names.map { it.trim() }.filter { it.isNotBlank() }
    }
    
    /**
     * New method: Groups artists by album artist from all songs.
     * This provides proper album-based grouping, showing single artist for collaboration albums.
     */
    private suspend fun loadArtistsGroupedByAlbumArtist(): List<Artist> = withContext(Dispatchers.IO) {
        val allSongs = loadSongs()
        val artistMap = mutableMapOf<String, MutableList<Song>>()
        val albumsByArtist = mutableMapOf<String, MutableSet<String>>()
        
        // Group songs by album artist (or track artist if album artist is not available)
        for (song in allSongs) {
            val artistName = (song.albumArtist?.takeIf { it.isNotBlank() } ?: song.artist).trim()
            
            // Skip invalid artist names
            if (artistName.isBlank() || artistName.equals("<unknown>", ignoreCase = true)) {
                continue
            }
            
            // Add song to artist's collection
            artistMap.getOrPut(artistName) { mutableListOf() }.add(song)
            
            // Track unique albums for this artist
            if (song.album.isNotBlank()) {
                albumsByArtist.getOrPut(artistName) { mutableSetOf() }.add(song.album)
            }
        }
        
        // Create Artist objects from grouped data
        val artists = artistMap.map { (artistName, songs) ->
            val albums = albumsByArtist[artistName] ?: emptySet()
            Artist(
                id = "album_artist_${artistName.hashCode()}", // Generate unique ID based on name
                name = artistName,
                numberOfAlbums = albums.size,
                numberOfTracks = songs.size
            )
        }.sortedBy { it.name.lowercase() }
        
        Log.d(TAG, "Loaded ${artists.size} artists grouped by album artist")
        artists
    }

    /**
     * Tries to find a better artist name from their tracks
     */
    private suspend fun findBetterArtistName(artistId: String): String? =
        withContext(Dispatchers.IO) {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.ARTIST_ID} = ?"
            val selectionArgs = arrayOf(artistId)

            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.ARTIST),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artists = mutableSetOf<String>()

                while (cursor.moveToNext()) {
                    val artist = cursor.getString(artistColumn)
                    if (!artist.isNullOrBlank() && !artist.equals("<unknown>", ignoreCase = true)) {
                        artists.add(artist)
                    }
                }

                // Return the most common non-blank artist name
                artists.maxByOrNull { name ->
                    cursor.moveToFirst()
                    var count = 0
                    while (cursor.moveToNext()) {
                        if (cursor.getString(artistColumn) == name) count++
                    }
                    count
                }
            }
        }

    /**
     * Triggers a full rescan of music data from the device's MediaStore.
     * This method will reload songs, albums, and artists.
     */
    suspend fun refreshMusicData() {
        Log.d(TAG, "Refreshing music data...")
        // Simply calling loadSongs, loadAlbums, loadArtists will re-query MediaStore
        // and provide fresh data. The ViewModel will then update its StateFlows.
        // No need to return anything here, as the ViewModel will call these methods
        // and collect the results.
        loadSongs(forceRefresh = true)
        loadAlbums()
        loadArtists()
        Log.d(TAG, "Music data refresh complete.")
    }
    
    /**
     * Suggest folders to blacklist based on common patterns
     */
    suspend fun suggestFoldersToBlacklist(songs: List<Song>): List<FolderSuggestion> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Analyzing folders for blacklist suggestions...")
        val suggestions = mutableListOf<FolderSuggestion>()
        
        // Get folder statistics
        val folderStats = mutableMapOf<String, MutableList<Song>>()
        songs.forEach { song ->
            try {
                val uri = song.uri
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        val path = cursor.getString(dataIndex)
                        val folder = path.substringBeforeLast("/")
                        folderStats.getOrPut(folder) { mutableListOf() }.add(song)
                    }
                }
            } catch (e: Exception) {
                // Skip songs we can't get path for
            }
        }
        
        // Common system folders to suggest blacklisting
        val systemFolders = listOf(
            "Ringtones", "ringtones", "Notifications", "notifications",  
            "Alarms", "alarms", "WhatsApp", "Telegram", "ui", "system"
        )
        
        folderStats.forEach { (folder, folderSongs) ->
            // Check for system folder names
            systemFolders.forEach { sysFolder ->
                if (folder.contains(sysFolder, ignoreCase = true)) {
                    suggestions.add(FolderSuggestion(
                        path = folder,
                        reason = "System folder: $sysFolder",
                        songCount = folderSongs.size,
                        confidence = 0.9
                    ))
                }
            }
            
            // Check for folders with very short files (likely ringtones/notifications)
            val avgDuration = folderSongs.mapNotNull { it.duration }.average()
            if (avgDuration < 30000) { // Less than 30 seconds
                suggestions.add(FolderSuggestion(
                    path = folder,
                    reason = "Contains short audio files (avg ${(avgDuration / 1000).toInt()}s)",
                    songCount = folderSongs.size,
                    confidence = 0.8
                ))
            }
        }
        
        Log.d(TAG, "Generated ${suggestions.size} folder suggestions")
        return@withContext suggestions.distinctBy { it.path }.sortedByDescending { it.confidence }
    }
    
    /**
     * Calculate comprehensive scan statistics
     */
    fun calculateScanStatistics(
        allSongs: List<Song>,
        filteredSongs: List<Song>,
        albums: List<Album>,
        artists: List<Artist>,
        lastScanTime: Long,
        lastScanDuration: Long
    ): ScanStatistics {
        // Note: storageUsed set to 0 since Song doesn't track file size
        // Would require additional file system access to calculate
        val storageUsed = 0L
        val bitrates = allSongs.mapNotNull { it.bitrate }
        val avgBitrate = if (bitrates.isNotEmpty()) bitrates.average().toInt() else 0
        val duplicatesRemoved = allSongs.size - filteredSongs.size
        
        return ScanStatistics(
            lastScanTime = lastScanTime,
            scanDuration = lastScanDuration,
            totalSongs = allSongs.size,
            filteredSongs = filteredSongs.size,
            totalAlbums = albums.size,
            totalArtists = artists.size,
            storageUsedBytes = storageUsed,
            averageBitrate = avgBitrate,
            duplicatesFound = duplicatesRemoved
        )
    }

    /**
     * Fetches artist images from Deezer API for artists without images
     */
    suspend fun fetchArtistImages(artists: List<Artist>): List<Artist> =
        withContext(Dispatchers.IO) {
            val updatedArtists = mutableListOf<Artist>()

            // NetworkClient will handle API key dynamically (user-provided or fallback to default)

            for (artist in artists) {
                try {
                    Log.d(TAG, "Processing artist: ${artist.name}")

                    if (artist.artworkUri != null) {
                        Log.d(
                            TAG,
                            "Artist ${artist.name} already has artwork: ${artist.artworkUri}"
                        )
                        updatedArtists.add(artist)
                        continue
                    }

                    // Check cache first
                    val cachedUri = artistImageCache[artist.name]
                    if (cachedUri != null) {
                        Log.d(TAG, "Using cached image for artist: ${artist.name}")
                        updatedArtists.add(artist.copy(artworkUri = cachedUri))
                        continue
                    }

                    // Check local storage for artist image
                    val localImage = findLocalArtistImage(artist.name)
                    if (localImage != null) {
                        Log.d(TAG, "Using local image for artist: ${artist.name}")
                        artistImageCache[artist.name] = localImage
                        updatedArtists.add(artist.copy(artworkUri = localImage))
                        continue
                    }

                    // Skip artists with empty or "Unknown" names
                    if (artist.name.isBlank() || artist.name.equals(
                            "Unknown Artist",
                            ignoreCase = true
                        )
                    ) {
                        Log.d(TAG, "Skipping unknown/blank artist name")
                        val placeholderUri =
                            chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                                name = "Unknown Artist",
                                size = 500,
                                cacheDir = context.cacheDir
                            )
                        artistImageCache[artist.name] = placeholderUri
                        updatedArtists.add(artist.copy(artworkUri = placeholderUri))
                        continue
                    }

                    // Only try online fetch if network is available and Deezer API is enabled
                    if (isNetworkAvailable() && NetworkClient.isDeezerApiEnabled() && deezerApiService != null) {
                        Log.d(TAG, "Searching for artist on Deezer: ${artist.name}")
                        
                        // Intelligent delay based on API and previous request timing
                        val apiDelay = calculateApiDelay("deezer", System.currentTimeMillis())
                        if (apiDelay > 0) {
                            delay(apiDelay)
                        }
                        updateLastApiCall("deezer", System.currentTimeMillis())
                        
                        try {
                            var deezerArtist: DeezerArtist? = null
                            
                            // First attempt: exact artist name with fuzzy matching
                            var searchResponse = deezerApiService.searchArtists(artist.name)
                            deezerArtist = findBestMatch(searchResponse.data, artist.name)
                            
                            // Second attempt: try with cleaned artist name if first failed
                            if (deezerArtist == null && artist.name.isNotBlank()) {
                                val cleanedName = artist.name
                                    .replace(Regex("\\s*\\([^)]*\\)\\s*"), "") // Remove text in parentheses
                                    .replace(Regex("\\s*&\\s*.*"), "") // Remove everything after &
                                    .replace(Regex("\\s*,\\s*.*"), "") // Remove everything after comma
                                    .replace(Regex("\\s*feat\\.?\\s*.*", RegexOption.IGNORE_CASE), "") // Remove feat.
                                    .replace(Regex("\\s*ft\\.?\\s*.*", RegexOption.IGNORE_CASE), "") // Remove ft.
                                    .trim()
                                
                                if (cleanedName.isNotEmpty() && cleanedName != artist.name) {
                                    Log.d(TAG, "Retrying Deezer search with cleaned name: $cleanedName")
                                    searchResponse = deezerApiService.searchArtists(cleanedName)
                                    deezerArtist = findBestMatch(searchResponse.data, artist.name)
                                }
                            }
                            
                            // Third attempt: try with first word only for multi-word artists
                            if (deezerArtist == null && artist.name.contains(" ")) {
                                val firstWord = artist.name.split(" ").first().trim()
                                if (firstWord.isNotEmpty() && firstWord.length > 2) {
                                    Log.d(TAG, "Retrying Deezer search with first word: $firstWord")
                                    searchResponse = deezerApiService.searchArtists(firstWord)
                                    deezerArtist = findBestMatch(searchResponse.data, artist.name)
                                }
                            }

                            if (deezerArtist != null) {
                                Log.d(TAG, "Found Deezer artist: ${deezerArtist.name} for ${artist.name}")
                                
                                // Choose the highest quality image available
                                val imageUrl = when {
                                    !deezerArtist.pictureXl.isNullOrEmpty() -> deezerArtist.pictureXl
                                    !deezerArtist.pictureBig.isNullOrEmpty() -> deezerArtist.pictureBig
                                    !deezerArtist.pictureMedium.isNullOrEmpty() -> deezerArtist.pictureMedium
                                    !deezerArtist.picture.isNullOrEmpty() -> deezerArtist.picture
                                    else -> null
                                }
                                
                                if (!imageUrl.isNullOrEmpty()) {
                                    val imageUri = Uri.parse(imageUrl)
                                    Log.d(TAG, "Found image URL for ${artist.name}: $imageUrl")
                                    artistImageCache[artist.name] = imageUri
                                    saveLocalArtistImage(artist.name, imageUrl)
                                    updatedArtists.add(artist.copy(artworkUri = imageUri))
                                    continue
                                }
                            } else {
                                Log.d(TAG, "No Deezer artist found for: ${artist.name}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Deezer lookup failed for ${artist.name}: ${e.message}")
                        }
                    } else {
                        Log.d(
                            TAG,
                            "No network connection available while fetching ${artist.name} image"
                        )
                    }

                    // -------- YouTube Music fallback (right after Deezer fails) --------
                    if (NetworkClient.isYTMusicApiEnabled() && ytmusicApiService != null) {
                        try {
                            Log.d(TAG, "Trying YTMusic for artist: ${artist.name}")
                            
                        // Create search request for artist
                        val searchRequest = YTMusicSearchRequest(
                            context = YTMusicContext(YTMusicClient()),
                            query = artist.name,
                            params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D" // Artist search filter
                        )
                        
                        val searchResponse = ytmusicApiService.search(request = searchRequest)
                        if (searchResponse.isSuccessful) {
                            val imageUrl = searchResponse.body()?.extractArtistImageUrl()
                            if (!imageUrl.isNullOrEmpty()) {
                                val imageUri = Uri.parse(imageUrl)
                                artistImageCache[artist.name] = imageUri
                                saveLocalArtistImage(artist.name, imageUrl)
                                updatedArtists.add(artist.copy(artworkUri = imageUri))
                                Log.d(TAG, "Found YTMusic image for ${artist.name}: $imageUrl")
                                continue
                            }
                            
                            // If search result has browseId, try to get detailed artist info for better quality image
                            val browseId = searchResponse.body()?.extractArtistBrowseId()
                            if (!browseId.isNullOrEmpty()) {
                                val browseRequest = YTMusicBrowseRequest(
                                    context = YTMusicContext(YTMusicClient()),
                                    browseId = browseId
                                )
                                val artistResponse = ytmusicApiService.getArtist(request = browseRequest)
                                if (artistResponse.isSuccessful) {
                                    val detailedImageUrl = artistResponse.body()?.extractArtistThumbnail()
                                    if (!detailedImageUrl.isNullOrEmpty()) {
                                        val imageUri = Uri.parse(detailedImageUrl)
                                        artistImageCache[artist.name] = imageUri
                                        saveLocalArtistImage(artist.name, detailedImageUrl)
                                        updatedArtists.add(artist.copy(artworkUri = imageUri))
                                        Log.d(TAG, "Found detailed YTMusic image for ${artist.name}: $detailedImageUrl")
                                        continue
                                    }
                                }
                            }
                        }
                        } catch (e: Exception) {
                            Log.w(TAG, "YTMusic fallback failed for ${artist.name}: ${e.message}")
                        }
                    }

                    // If we get here, generate a placeholder image
                    Log.d(TAG, "Generating placeholder for artist: ${artist.name}")
                    val placeholderUri =
                        chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                            name = artist.name,
                            size = 500,
                            cacheDir = context.cacheDir
                        )
                    artistImageCache[artist.name] = placeholderUri
                    updatedArtists.add(artist.copy(artworkUri = placeholderUri))

                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching artist image for ${artist.name}", e)
                    try {
                        val placeholderUri =
                            chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                                name = artist.name,
                                size = 500,
                                cacheDir = context.cacheDir
                            )
                        updatedArtists.add(artist.copy(artworkUri = placeholderUri))
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error generating placeholder for ${artist.name}", e2)
                        updatedArtists.add(artist)
                    }
                }
            }

            updatedArtists
        }
    /**
     * Extracts embedded lyrics from audio file metadata - REDONE FROM SCRATCH
     * 
     * Improved extraction with:
     * 1. Better ID3v2.3/v2.4 USLT frame parsing
     * 2. Proper synchsafe integer handling
     * 3. Multiple charset support (ISO-8859-1, UTF-16, UTF-8)
     * 4. Safety checks to prevent hangs and crashes
     * 5. Support for both synced LRC and plain text lyrics
     * 6. Fallback to MediaMetadataRetriever for FLAC/other formats
     */
    private fun getEmbeddedLyrics(songUri: Uri): LyricsData? {
        return try {
            Log.d(TAG, "===== GET EMBEDDED LYRICS START: $songUri =====")
            
            // Primary method: Direct ID3v2 tag parsing (for MP3)
            val id3Lyrics = extractLyricsFromID3v2(songUri)
            if (id3Lyrics != null) {
                Log.d(TAG, "===== FOUND LYRICS VIA ID3V2 =====")
                return id3Lyrics
            }
            
            // Fallback: MediaMetadataRetriever (for FLAC, M4A, etc.)
            Log.d(TAG, "===== TRYING MediaMetadataRetriever =====")
            val retrieverLyrics = extractLyricsViaRetriever(songUri)
            if (retrieverLyrics != null) {
                Log.d(TAG, "===== FOUND LYRICS VIA RETRIEVER =====")
                return retrieverLyrics
            }
            
            Log.d(TAG, "===== NO EMBEDDED LYRICS FOUND =====")
            null
        } catch (e: Exception) {
            Log.w(TAG, "===== EMBEDDED LYRICS EXTRACTION FAILED: ${e.message} =====")
            null
        }
    }
    
    /**
     * Extract lyrics using MediaMetadataRetriever (works for FLAC, M4A, etc.)
     * For FLAC: Checks Vorbis comments (LYRICS/UNSYNCEDLYRICS tags)
     */
    private fun extractLyricsViaRetriever(songUri: Uri): LyricsData? {
        return try {
            Log.d(TAG, "===== extractLyricsViaRetriever START: $songUri =====")
            val retriever = android.media.MediaMetadataRetriever()
            retriever.use {
                context.contentResolver.openFileDescriptor(songUri, "r")?.use { pfd ->
                    it.setDataSource(pfd.fileDescriptor)
                    
                    // Try different metadata keys that might contain lyrics
                    // FLAC uses custom Vorbis comments, but Android exposes some through standard keys
                    val possibleKeys = listOf(
                        android.media.MediaMetadataRetriever.METADATA_KEY_WRITER,  // Sometimes contains lyrics
                        android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER, // Fallback
                    )
                    
                    for (key in possibleKeys) {
                        val value = it.extractMetadata(key)
                        if (value != null && value.isNotBlank() && value.length > 50) { // Likely lyrics if > 50 chars
                            Log.d(TAG, "Found potential lyrics in metadata key $key (${value.length} chars)")
                            val parsed = parseLyricsData(value)
                            if (parsed != null) return@use parsed
                        }
                    }
                    
                    // For FLAC files, try direct Vorbis comment parsing
                    val filePath = getFilePathFromUri(songUri)
                    Log.d(TAG, "===== Resolved file path: $filePath =====")
                    
                    if (filePath?.endsWith(".flac", ignoreCase = true) == true) {
                        Log.d(TAG, "===== Detected FLAC file, trying FLAC extraction =====")
                        return@use extractLyricsFromFLAC(filePath)
                    }
                    
                    // For M4A files, try direct iTunes metadata parsing
                    if (filePath?.endsWith(".m4a", ignoreCase = true) == true) {
                        Log.d(TAG, "===== Detected M4A file, trying M4A extraction =====")
                        return@use extractLyricsFromM4A(filePath)
                    }
                    
                    // For OGG files, try direct Vorbis comment parsing
                    if (filePath?.endsWith(".ogg", ignoreCase = true) == true || 
                        filePath?.endsWith(".oga", ignoreCase = true) == true) {
                        Log.d(TAG, "===== Detected OGG/Vorbis file, trying OGG extraction =====")
                        return@use extractLyricsFromOGG(filePath)
                    }
                    
                    Log.d(TAG, "===== No specific format handler matched =====")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "===== extractLyricsViaRetriever FAILED: ${e.message} =====")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Extract lyrics from FLAC Vorbis comments
     */
    private fun extractLyricsFromFLAC(filePath: String): LyricsData? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // Check FLAC signature
                val signature = ByteArray(4)
                if (raf.read(signature) != 4) return@use null
                if (String(signature, Charsets.ISO_8859_1) != "fLaC") {
                    Log.d(TAG, "Not a valid FLAC file")
                    return@use null
                }
                
                // Read metadata blocks
                var lastBlock = false
                while (!lastBlock && raf.filePointer < file.length()) {
                    val blockHeader = raf.read()
                    if (blockHeader == -1) break
                    
                    lastBlock = (blockHeader and 0x80) != 0
                    val blockType = blockHeader and 0x7F
                    
                    // Read block length (24-bit big-endian)
                    val length = ((raf.read() and 0xFF) shl 16) or
                               ((raf.read() and 0xFF) shl 8) or
                               (raf.read() and 0xFF)
                    
                    if (length <= 0 || length > 16_777_215) break // Max 16MB block
                    
                    // Block type 4 = VORBIS_COMMENT
                    if (blockType == 4) {
                        val commentData = ByteArray(length)
                        if (raf.read(commentData) != length) break
                        
                        val lyrics = parseVorbisComments(commentData)
                        if (lyrics != null) {
                            Log.d(TAG, "Found lyrics in FLAC Vorbis comments")
                            return@use lyrics
                        }
                    } else {
                        // Skip this block
                        raf.seek(raf.filePointer + length)
                    }
                }
                
                Log.d(TAG, "No LYRICS tag found in FLAC Vorbis comments")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "FLAC lyrics extraction failed: ${e.message}")
            null
        }
    }
    
    /**
     * Parse Vorbis comments to extract LYRICS or UNSYNCEDLYRICS tags
     */
    private fun parseVorbisComments(data: ByteArray): LyricsData? {
        try {
            var pos = 0
            
            // Read vendor string length (little-endian 32-bit)
            if (pos + 4 > data.size) return null
            val vendorLength = (data[pos].toInt() and 0xFF) or
                             ((data[pos + 1].toInt() and 0xFF) shl 8) or
                             ((data[pos + 2].toInt() and 0xFF) shl 16) or
                             ((data[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            
            // Skip vendor string
            if (vendorLength < 0 || pos + vendorLength > data.size) return null
            pos += vendorLength
            
            // Read number of comments
            if (pos + 4 > data.size) return null
            val commentCount = (data[pos].toInt() and 0xFF) or
                             ((data[pos + 1].toInt() and 0xFF) shl 8) or
                             ((data[pos + 2].toInt() and 0xFF) shl 16) or
                             ((data[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            
            if (commentCount < 0 || commentCount > 10000) return null // Safety limit
            
            // Parse each comment
            for (i in 0 until commentCount) {
                if (pos + 4 > data.size) break
                
                val commentLength = (data[pos].toInt() and 0xFF) or
                                  ((data[pos + 1].toInt() and 0xFF) shl 8) or
                                  ((data[pos + 2].toInt() and 0xFF) shl 16) or
                                  ((data[pos + 3].toInt() and 0xFF) shl 24)
                pos += 4
                
                if (commentLength < 0 || pos + commentLength > data.size) break
                
                val commentBytes = data.copyOfRange(pos, pos + commentLength)
                val comment = String(commentBytes, Charsets.UTF_8)
                pos += commentLength
                
                // Check if this is a lyrics tag
                val parts = comment.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].uppercase()
                    val value = parts[1]
                    
                    if ((key == "LYRICS" || key == "UNSYNCEDLYRICS") && value.isNotBlank()) {
                        Log.d(TAG, "Found $key tag in Vorbis comments (${value.length} chars)")
                        return parseLyricsData(value)
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vorbis comments: ${e.message}")
            return null
        }
    }
    
    /**
     * Extract lyrics from OGG/Vorbis files
     * OGG uses the same Vorbis comment format as FLAC
     */
    private fun extractLyricsFromOGG(filePath: String): LyricsData? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // Check OGG signature
                val signature = ByteArray(4)
                if (raf.read(signature) != 4) return@use null
                if (String(signature, Charsets.ISO_8859_1) != "OggS") {
                    Log.d(TAG, "Not a valid OGG file")
                    return@use null
                }
                
                // OGG files are organized into pages
                // Vorbis comments are typically in the second page (after the identification header)
                var foundCommentHeader = false
                var attemptCount = 0
                val maxAttempts = 100 // Prevent infinite loops
                
                // Reset to beginning
                raf.seek(0)
                
                while (raf.filePointer < file.length() && attemptCount < maxAttempts) {
                    attemptCount++
                    
                    // Read OGG page header
                    val pageSignature = ByteArray(4)
                    if (raf.read(pageSignature) != 4) break
                    if (String(pageSignature, Charsets.ISO_8859_1) != "OggS") {
                        // Try to resync - search for next OggS
                        val nextOggS = findNextOggSPage(raf)
                        if (nextOggS == -1L) break
                        raf.seek(nextOggS)
                        continue
                    }
                    
                    // Skip version (1 byte) and header type (1 byte)
                    raf.skipBytes(2)
                    
                    // Skip granule position (8 bytes), serial number (4 bytes), 
                    // sequence number (4 bytes), checksum (4 bytes)
                    raf.skipBytes(20)
                    
                    // Read number of page segments
                    val numSegments = raf.read()
                    if (numSegments == -1 || numSegments < 0) break
                    
                    // Read segment table
                    val segmentTable = ByteArray(numSegments)
                    if (raf.read(segmentTable) != numSegments) break
                    
                    // Calculate total page payload size
                    val pageSize = segmentTable.sumOf { (it.toInt() and 0xFF) }
                    
                    // Read the page data
                    if (pageSize > 0 && pageSize < 1_000_000) { // Safety limit: 1MB max page
                        val pageData = ByteArray(pageSize)
                        if (raf.read(pageData) != pageSize) break
                        
                        // Check if this is a Vorbis comment header
                        // Vorbis comment header starts with packet type 0x03 followed by "vorbis"
                        if (pageData.size >= 7 && 
                            pageData[0] == 0x03.toByte() &&
                            String(pageData.copyOfRange(1, 7), Charsets.ISO_8859_1) == "vorbis") {
                            
                            Log.d(TAG, "Found Vorbis comment header in OGG file")
                            foundCommentHeader = true
                            
                            // Parse the Vorbis comments (skip the 7-byte header)
                            val commentData = pageData.copyOfRange(7, pageData.size)
                            val lyrics = parseVorbisComments(commentData)
                            if (lyrics != null) {
                                Log.d(TAG, "Found lyrics in OGG Vorbis comments")
                                return@use lyrics
                            }
                        }
                    } else {
                        // Skip invalid or too-large page
                        if (pageSize > 0) {
                            raf.seek(raf.filePointer + pageSize)
                        }
                    }
                    
                    // If we found the comment header but no lyrics, we can stop
                    if (foundCommentHeader) break
                }
                
                Log.d(TAG, "No LYRICS tag found in OGG Vorbis comments (checked $attemptCount pages)")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "OGG lyrics extraction failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Helper function to find the next OggS page signature in the file
     */
    private fun findNextOggSPage(raf: java.io.RandomAccessFile): Long {
        val buffer = ByteArray(4096)
        var bytesRead: Int
        val targetPattern = "OggS".toByteArray(Charsets.ISO_8859_1)
        
        while (raf.filePointer < raf.length()) {
            bytesRead = raf.read(buffer)
            if (bytesRead == -1) return -1
            
            for (i in 0 until bytesRead - 3) {
                if (buffer[i] == targetPattern[0] &&
                    buffer[i + 1] == targetPattern[1] &&
                    buffer[i + 2] == targetPattern[2] &&
                    buffer[i + 3] == targetPattern[3]) {
                    return raf.filePointer - bytesRead + i
                }
            }
        }
        return -1
    }
    
    /**
     * Extract lyrics from M4A iTunes metadata (©lyr atom and alternatives)
     */
    private fun extractLyricsFromM4A(filePath: String): LyricsData? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // M4A files use MP4/QuickTime container format with atoms
                // We need to find the 'moov' atom, then 'udta', then 'meta', then 'ilst', then lyrics atoms
                
                // Try multiple possible lyrics atom names (different taggers use different formats)
                val lyricsAtomNames = listOf(
                    "©lyr",  // Standard iTunes lyrics
                    "\u00a9lyr", // Alternative encoding of ©
                    "lyr ",  // Alternative with space
                    "lyr\u0000",  // Null-terminated variant
                    "USLT",  // Unsynchronized lyrics (ID3-style)
                    "©day",  // Some taggers incorrectly use this
                    "----",  // Custom freeform atom (may contain lyrics)
                    "desc",  // Description field sometimes used
                    "©des",  // Description variant
                    "©cmt",  // Comment field (sometimes contains lyrics)
                    "©CMT"   // Comment uppercase variant
                )
                
                for (atomName in lyricsAtomNames) {
                    val lyricsAtom = findM4AAtom(raf, atomName)
                    if (lyricsAtom != null && lyricsAtom.isNotBlank()) {
                        Log.d(TAG, "Found text in M4A atom '$atomName' (length: ${lyricsAtom.length}): ${lyricsAtom.take(100)}...")
                        val parsed = parseLyricsData(lyricsAtom)
                        if (parsed != null) {
                            Log.d(TAG, "✓ Accepted lyrics from atom '$atomName'")
                            return@use parsed
                        } else {
                            Log.d(TAG, "✗ Text from atom '$atomName' was rejected as not lyrics-like")
                        }
                    }
                }
                
                // Last resort: scan all text atoms and look for lyrics-like content
                Log.d(TAG, "Attempting comprehensive M4A atom scan for lyrics-like content")
                val allTextAtoms = findAllTextAtoms(raf)
                Log.d(TAG, "Found ${allTextAtoms.size} text atoms in M4A file")
                
                for ((atomName, content) in allTextAtoms) {
                    Log.d(TAG, "Checking text atom '$atomName' (length: ${content.length}): ${content.take(100)}...")
                    if (content.length > 100 && looksLikeLyrics(content)) {
                        Log.d(TAG, "Text atom '$atomName' passed lyrics validation")
                        val parsed = parseLyricsData(content)
                        if (parsed != null) {
                            Log.d(TAG, "✓ Accepted lyrics from comprehensive scan atom '$atomName'")
                            return@use parsed
                        }
                    } else {
                        Log.d(TAG, "Text atom '$atomName' rejected: length=${content.length}, looksLikeLyrics=${looksLikeLyrics(content)}")
                    }
                }
                
                Log.d(TAG, "No lyrics atoms found in M4A file (checked: ${lyricsAtomNames.joinToString(", ")}, scanned ${allTextAtoms.size} text atoms)")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "M4A lyrics extraction failed: ${e.message}")
            null
        }
    }
    
    /**
     * Find and extract a specific atom from M4A file
     */
    private fun findM4AAtom(raf: java.io.RandomAccessFile, targetAtom: String): String? {
        try {
            // Recursively search for atoms
            return searchM4AAtoms(raf, 0, raf.length(), targetAtom, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search M4A atoms: ${e.message}")
            return null
        }
    }
    
    /**
     * Scan M4A file and collect all text atoms (for debugging and fallback lyrics search)
     */
    private fun findAllTextAtoms(raf: java.io.RandomAccessFile): Map<String, String> {
        val textAtoms = mutableMapOf<String, String>()
        try {
            collectTextAtoms(raf, 0, raf.length(), 0, textAtoms)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect text atoms: ${e.message}")
        }
        return textAtoms
    }
    
    /**
     * Recursively collect all text-containing atoms
     */
    private fun collectTextAtoms(
        raf: java.io.RandomAccessFile,
        offset: Long,
        endOffset: Long,
        depth: Int,
        results: MutableMap<String, String>
    ) {
        if (depth > 10 || results.size > 50) return // Prevent excessive recursion/results
        
        var pos = offset
        val atomsAtThisLevel = mutableListOf<String>()
        
        while (pos < endOffset - 8) {
            try {
                raf.seek(pos)
                
                // Read atom size and type
                val sizeBytes = ByteArray(4)
                if (raf.read(sizeBytes) != 4) break
                var atomSize = ((sizeBytes[0].toInt() and 0xFF) shl 24) or
                              ((sizeBytes[1].toInt() and 0xFF) shl 16) or
                              ((sizeBytes[2].toInt() and 0xFF) shl 8) or
                              (sizeBytes[3].toInt() and 0xFF)
                
                val typeBytes = ByteArray(4)
                if (raf.read(typeBytes) != 4) break
                val atomType = String(typeBytes, Charsets.ISO_8859_1)
                
                if (atomSize <= 0 || atomSize > 100_000_000) break
                
                // Track atoms for debugging
                atomsAtThisLevel.add("$atomType($atomSize)")
                
                // Look for 'data' atoms that contain text
                if (atomType == "data" && atomSize > 16 && atomSize < 500_000) {
                    raf.seek(pos + 16)
                    val dataBytes = ByteArray((atomSize - 16).coerceAtMost(100_000))
                    if (raf.read(dataBytes) == dataBytes.size) {
                        val text = String(dataBytes, Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r')
                        if (text.length > 10 && text.any { it.isLetter() }) {
                            Log.d(TAG, "Found text in 'data' atom at depth $depth: ${text.take(50)}...")
                            results["data_${pos}_${depth}"] = text
                        }
                    }
                }
                
                // Try to extract text from ANY atom with reasonable size
                if (atomSize > 20 && atomSize < 500_000 && atomType.length == 4) {
                    raf.seek(pos + 8)
                    val contentBytes = ByteArray((atomSize - 8).coerceAtMost(100_000))
                    if (raf.read(contentBytes) == contentBytes.size) {
                        // Skip binary data (check for text-like content)
                        val possibleText = String(contentBytes, Charsets.UTF_8)
                        val alphaCount = possibleText.count { it.isLetter() }
                        if (alphaCount > 50 && alphaCount > possibleText.length * 0.3) {
                            val text = possibleText.trim('\u0000', ' ', '\n', '\r')
                            if (text.length > 100 && looksLikeLyrics(text)) {
                                Log.d(TAG, "Found lyrics-like text in '$atomType' atom at depth $depth: ${text.take(50)}...")
                                results["${atomType}_${pos}_${depth}"] = text
                            }
                        }
                    }
                }
                
                // Special handling for 'meta' atom (has 4 bytes version/flags before children)
                var childOffset = pos + 8
                if (atomType == "meta") {
                    childOffset = pos + 12 // Skip version/flags
                }
                
                // Recurse into container atoms
                if (atomType == "moov" || atomType == "udta" || atomType == "meta" || 
                    atomType == "ilst" || atomType.startsWith("©") || atomType == "----") {
                    collectTextAtoms(raf, childOffset, pos + atomSize, depth + 1, results)
                }
                
                pos += atomSize
                if (atomSize < 8) break
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning atom at pos $pos, depth $depth: ${e.message}")
                pos += 8 // Skip problematic atom
            }
        }
        
        // Log atoms found at this level
        if (atomsAtThisLevel.isNotEmpty() && depth <= 5) {
            Log.d(TAG, "M4A depth $depth atoms: ${atomsAtThisLevel.take(10).joinToString(", ")}${if (atomsAtThisLevel.size > 10) "... (${atomsAtThisLevel.size} total)" else ""}")
        }
    }
    
    /**
     * Recursively search through M4A atom hierarchy
     */
    private fun searchM4AAtoms(
        raf: java.io.RandomAccessFile,
        offset: Long,
        endOffset: Long,
        targetAtom: String,
        depth: Int
    ): String? {
        if (depth > 10) return null // Prevent infinite recursion
        
        var pos = offset
        val foundAtoms = mutableListOf<String>() // Track what we find for debugging
        
        while (pos < endOffset - 8) {
            raf.seek(pos)
            
            // Read atom size (big-endian 32-bit)
            val sizeBytes = ByteArray(4)
            if (raf.read(sizeBytes) != 4) break
            var atomSize = ((sizeBytes[0].toInt() and 0xFF) shl 24) or
                          ((sizeBytes[1].toInt() and 0xFF) shl 16) or
                          ((sizeBytes[2].toInt() and 0xFF) shl 8) or
                          (sizeBytes[3].toInt() and 0xFF)
            
            // Read atom type (4 ASCII chars)
            val typeBytes = ByteArray(4)
            if (raf.read(typeBytes) != 4) break
            val atomType = String(typeBytes, Charsets.ISO_8859_1)
            
            // Log atoms at interesting depths (metadata level) - more inclusive pattern
            if (depth >= 2 && atomType.isNotEmpty()) {
                val isPrintable = atomType.all { it.isLetterOrDigit() || it in "©@- _" }
                if (isPrintable) {
                    foundAtoms.add(atomType)
                }
            }
            
            // Handle extended size (size = 1 means 64-bit size follows)
            if (atomSize == 1) {
                val extSizeBytes = ByteArray(8)
                if (raf.read(extSizeBytes) != 8) break
                atomSize = 0 // Skip large atoms for safety
            }
            
            if (atomSize <= 0 || atomSize > 100_000_000) break // Max 100MB per atom
            
            // Check if this is our target atom
            if (atomType == targetAtom) {
                // For ©lyr, data is usually in a nested 'data' atom
                val dataAtom = searchM4AAtoms(raf, pos + 8, pos + atomSize, "data", depth + 1)
                if (dataAtom != null) return dataAtom
                
                // If no 'data' atom, try reading directly
                if (atomSize > 16 && atomSize < 1_000_000) {
                    val dataBytes = ByteArray(atomSize - 8)
                    raf.seek(pos + 8)
                    raf.read(dataBytes)
                    val text = String(dataBytes, Charsets.UTF_8).trim('\u0000')
                    if (text.isNotBlank()) return text
                }
            }
            
            // Check if this is a 'data' atom (contains actual text)
            if (atomType == "data" && atomSize > 16 && atomSize < 1_000_000) {
                // Read data type flag (at position 8-11)
                raf.seek(pos + 8)
                val typeFlag = ByteArray(4)
                raf.read(typeFlag)
                val dataType = ((typeFlag[0].toInt() and 0xFF) shl 24) or
                              ((typeFlag[1].toInt() and 0xFF) shl 16) or
                              ((typeFlag[2].toInt() and 0xFF) shl 8) or
                              (typeFlag[3].toInt() and 0xFF)
                
                // Type 1 = UTF-8 text, Type 0 = binary/implicit
                // Skip 16 bytes total (8 for size+type, 8 for version+flags+reserved)
                raf.seek(pos + 16)
                val dataBytes = ByteArray(atomSize - 16)
                if (raf.read(dataBytes) == dataBytes.size) {
                    // Try both UTF-8 and ISO-8859-1 encodings
                    var text = String(dataBytes, Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r')
                    
                    // If UTF-8 decode fails or looks wrong, try ISO-8859-1
                    if (text.isEmpty() || text.any { it == '\uFFFD' }) {
                        text = String(dataBytes, Charsets.ISO_8859_1).trim('\u0000', ' ', '\n', '\r')
                    }
                    
                    if (text.isNotBlank() && text.length > 5) { // Reasonable lyrics length
                        Log.d(TAG, "Extracted text from 'data' atom (type: $dataType, length: ${text.length})")
                        return text
                    }
                }
            }
            
            // Recurse into container atoms
            // Special handling for 'meta' atom which has 4 bytes version/flags before children
            val childOffset = if (atomType == "meta") pos + 12 else pos + 8
            
            if (atomType == "moov" || atomType == "udta" || atomType == "meta" || 
                atomType == "ilst" || atomType.startsWith("©")) {
                val result = searchM4AAtoms(raf, childOffset, pos + atomSize, targetAtom, depth + 1)
                if (result != null) return result
            }
            
            // Move to next atom
            pos += atomSize
            if (atomSize < 8) break // Prevent infinite loop on malformed files
        }
        
        // Log what atoms we found at metadata level (helps debugging)
        if (foundAtoms.isNotEmpty() && depth >= 2) {
            Log.d(TAG, "M4A atoms found at depth $depth: ${foundAtoms.take(20).joinToString(", ")}${if (foundAtoms.size > 20) "..." else ""}")
        }
        
        return null
    }
    
    /**
     * NEW: Extract lyrics from ID3v2 tags with improved parsing
     * Supports ID3v2.3 and ID3v2.4 with synchsafe integers
     */
    private fun extractLyricsFromID3v2(songUri: Uri): LyricsData? {
        val filePath = getFilePathFromUri(songUri)
        
        if (filePath == null) {
            Log.d(TAG, "Could not resolve file path from URI")
            return null
        }
        
        val file = java.io.File(filePath)
        
        if (!file.exists()) {
            Log.d(TAG, "File does not exist: $filePath")
            return null
        }
        
        if (!file.canRead()) {
            Log.d(TAG, "Cannot read file: $filePath")
            return null
        }
        
        // Check if file is MP3 (ID3 tags only work for MP3)
        if (!filePath.endsWith(".mp3", ignoreCase = true)) {
            Log.d(TAG, "Not an MP3 file, skipping ID3v2 parsing: $filePath")
            return null
        }
        
        Log.d(TAG, "Parsing ID3v2 tags from: $filePath")
        
        return java.io.RandomAccessFile(file, "r").use { raf ->
            // Read and validate ID3v2 header
            val header = ByteArray(10)
            if (raf.read(header) != 10) return@use null
            
            // Check ID3v2 signature
            if (header[0] != 'I'.code.toByte() || 
                header[1] != 'D'.code.toByte() || 
                header[2] != '3'.code.toByte()) {
                return@use null
            }
            
            val majorVersion = header[3].toInt() and 0xFF
            val minorVersion = header[4].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            
            Log.d(TAG, "ID3v2.$majorVersion.$minorVersion detected, flags: $flags")
            
            // Only support v2.3 and v2.4
            if (majorVersion < 3 || majorVersion > 4) {
                Log.d(TAG, "Unsupported ID3 version: $majorVersion")
                return@use null
            }
            
            // Parse synchsafe integer for tag size
            val tagSize = decodeSynchsafe(
                header[6].toInt() and 0xFF,
                header[7].toInt() and 0xFF,
                header[8].toInt() and 0xFF,
                header[9].toInt() and 0xFF
            )
            
            // Validate tag size (max 10MB)
            if (tagSize <= 0 || tagSize > 10_485_760) {
                Log.w(TAG, "Invalid tag size: $tagSize")
                return@use null
            }
            
            // Read tag data
            val tagData = ByteArray(tagSize)
            val bytesRead = raf.read(tagData)
            if (bytesRead != tagSize) {
                Log.w(TAG, "Failed to read complete tag data")
                return@use null
            }
            
            // Parse frames and find USLT
            parseID3v2Frames(tagData, majorVersion)
        }
    }
    
    /**
     * NEW: Decode synchsafe integer (ID3v2 size encoding)
     */
    private fun decodeSynchsafe(b1: Int, b2: Int, b3: Int, b4: Int): Int {
        return ((b1 and 0x7F) shl 21) or
               ((b2 and 0x7F) shl 14) or
               ((b3 and 0x7F) shl 7) or
               (b4 and 0x7F)
    }
    
    /**
     * NEW: Parse ID3v2 frames and extract USLT (lyrics) frame
     */
    private fun parseID3v2Frames(tagData: ByteArray, version: Int): LyricsData? {
        var pos = 0
        var frameCount = 0
        val maxFrames = 1000 // Safety limit
        
        while (pos < tagData.size - 10 && frameCount < maxFrames) {
            frameCount++
            
            // Check for padding (null bytes indicate end of frames)
            if (tagData[pos] == 0.toByte()) break
            
            // Read frame header
            val frameId = String(tagData.copyOfRange(pos, pos + 4), Charsets.ISO_8859_1)
            
            // Calculate frame size (different for v2.3 and v2.4)
            val frameSize = if (version == 4) {
                // v2.4 uses synchsafe integers
                decodeSynchsafe(
                    tagData[pos + 4].toInt() and 0xFF,
                    tagData[pos + 5].toInt() and 0xFF,
                    tagData[pos + 6].toInt() and 0xFF,
                    tagData[pos + 7].toInt() and 0xFF
                )
            } else {
                // v2.3 uses regular 32-bit integer
                ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                (tagData[pos + 7].toInt() and 0xFF)
            }
            
            // Validate frame size
            if (frameSize <= 0 || frameSize > tagData.size - pos - 10 || frameSize > 2_097_152) {
                Log.w(TAG, "Invalid frame size: $frameSize for $frameId")
                break
            }
            
            // Check if this is a USLT frame
            if (frameId == "USLT") {
                val lyricsData = parseUSLTFrame(tagData, pos + 10, frameSize)
                if (lyricsData != null) {
                    Log.d(TAG, "Successfully extracted USLT lyrics")
                    return lyricsData
                }
            }
            
            // Move to next frame
            pos += 10 + frameSize
        }
        
        return null
    }
    
    /**
     * NEW: Parse USLT frame content
     */
    private fun parseUSLTFrame(data: ByteArray, offset: Int, size: Int): LyricsData? {
        try {
            if (size < 4) return null
            
            // USLT frame structure:
            // - Text encoding (1 byte)
            // - Language (3 bytes, ISO-639-2)
            // - Content descriptor (null-terminated string)
            // - Lyrics text (rest of frame)
            
            val encoding = data[offset].toInt() and 0xFF
            // Skip language (3 bytes)
            var pos = offset + 4
            
            // Skip content descriptor (null-terminated)
            val terminator = if (encoding == 1 || encoding == 2) 2 else 1 // UTF-16 uses 2-byte null
            var nullCount = 0
            while (pos < offset + size && nullCount < terminator) {
                if (data[pos] == 0.toByte()) nullCount++
                pos++
                if (pos - offset > 256) break // Safety: max 256 bytes for descriptor
            }
            
            // Extract lyrics text
            val lyricsEnd = offset + size
            if (pos >= lyricsEnd) return null
            
            val lyricsBytes = data.copyOfRange(pos, lyricsEnd)
            
            // Decode based on encoding
            val charset = when (encoding) {
                0 -> Charsets.ISO_8859_1
                1 -> Charsets.UTF_16  // UTF-16 with BOM
                2 -> Charsets.UTF_16BE
                3 -> Charsets.UTF_8
                else -> Charsets.UTF_8
            }
            
            val lyricsText = String(lyricsBytes, charset)
                .trim()
                .replace("\u0000", "") // Remove null characters
            
            if (lyricsText.isBlank()) return null
            
            return parseLyricsData(lyricsText)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse USLT frame: ${e.message}")
            return null
        }
    }
    
    /**
     * Helper to determine if text looks like lyrics
     */
    private fun looksLikeLyrics(text: String): Boolean {
        val trimmed = text.trim()
        
        // Reject if too short (likely just metadata)
        if (trimmed.length < 50) return false
        
        // Reject if it's just a single line (likely song title or metadata)
        val lines = trimmed.lines().filter { it.trim().isNotEmpty() }
        if (lines.size < 3) return false
        
        // Accept if has LRC timestamps
        val hasTimestamp = text.contains(Regex("\\[\\d{2}:\\d{2}"))
        if (hasTimestamp) return true
        
        // Reject common metadata patterns
        val lowerText = trimmed.lowercase()
        val metadataKeywords = listOf(
            "track", "album", "artist", "genre", "year", "composer",
            "copyright", "encoded", "encoder", "itunes", "id3"
        )
        val hasMetadataKeywords = metadataKeywords.any { lowerText.contains(it) }
        
        // Check for common lyrics structure markers
        val hasCommonLyricsWords = lowerText.let { 
            it.contains("verse") || it.contains("chorus") || it.contains("bridge") ||
            it.contains("refrain") || it.contains("intro") || it.contains("outro")
        }
        
        // Calculate how "lyrics-like" the text is
        val avgLineLength = lines.map { it.length }.average()
        val hasRepeatingPatterns = lines.distinct().size < lines.size * 0.8 // Some repetition expected
        val isProseLength = avgLineLength in 20.0..80.0 // Typical lyrics line length
        
        // Accept if it has multiple qualities of lyrics
        return (lines.size >= 5 && isProseLength && !hasMetadataKeywords) || 
               (hasCommonLyricsWords && lines.size >= 3) ||
               (hasRepeatingPatterns && lines.size >= 8 && avgLineLength < 100)
    }
    
    /**
     * Parses lyrics text into LyricsData with proper format detection and cleaning
     */
    private fun parseLyricsData(lyrics: String): LyricsData? {
        if (lyrics.isBlank()) {
            return null
        }
        
        // Log the first 200 characters to see what we're parsing
        Log.d(TAG, "Parsing lyrics data: ${lyrics.take(200)}${if (lyrics.length > 200) "..." else ""}")
        
        // Clean up the lyrics text
        val cleanedLyrics = lyrics
            .trim()
            .replace("\r\n", "\n") // Normalize line endings
            .replace("\r", "\n")
        
        // Check if this looks like just a song title or metadata
        val lines = cleanedLyrics.lines().filter { it.trim().isNotEmpty() }
        if (lines.size == 1) {
            Log.w(TAG, "Rejected lyrics: single line detected (likely metadata): ${cleanedLyrics.take(100)}")
            return null
        }
        
        // Check if lyrics are synced (contain LRC-style timestamps)
        // Support multiple timestamp formats: [mm:ss.xx], [mm:ss.xxx], [mm:ss]
        val lrcPattern = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{2,3})?]")
        val isSynced = lrcPattern.containsMatchIn(cleanedLyrics)
        
        return if (isSynced) {
            // Synced lyrics - validate format
            val hasLyricsContent = cleanedLyrics.lines().any { line ->
                lrcPattern.replace(line, "").trim().isNotEmpty()
            }
            
            if (hasLyricsContent) {
                // Check for Enhanced LRC format with word-level timestamps <mm:ss.xx>
                val hasWordTimestamps = LyricsParser.hasWordTimestamps(cleanedLyrics)
                
                if (hasWordTimestamps) {
                    Log.d(TAG, "Detected Enhanced LRC format with word-level timestamps")
                    
                    // Parse Enhanced LRC and convert to word-by-word format
                    val enhancedLines = LyricsParser.parseEnhancedLRC(cleanedLyrics)
                    
                    if (enhancedLines.isNotEmpty()) {
                        // Convert to Apple Music word-by-word format (JSON)
                        val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)
                        
                        // Also extract plain text and line-synced LRC
                        val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                        }
                        
                        val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            val timestamp = formatLRCTimestamp(line.lineTimestamp)
                            val text = line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                            "[$timestamp]$text"
                        }
                        
                        Log.d(TAG, "Successfully converted Enhanced LRC to word-by-word format (${enhancedLines.size} lines)")
                        return LyricsData(plainText, syncedLrc, wordByWordJson)
                    }
                }
                
                // Standard LRC format (line-by-line only)
                LyricsData(null, cleanedLyrics, null)
            } else {
                // Empty synced lyrics
                null
            }
        } else {
            // Plain text lyrics - validate it's not just metadata
            val meaningfulLines = cleanedLyrics.lines().filter { line ->
                val trimmed = line.trim()
                // Filter out common metadata markers
                trimmed.isNotEmpty() && 
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("#") &&
                trimmed.length > 2
            }
            
            if (meaningfulLines.isNotEmpty()) {
                LyricsData(cleanedLyrics, null, null)
            } else {
                null
            }
        }
    }
    
    /**
     * Convert Enhanced LRC format to Apple Music word-by-word JSON format
     */
    private fun convertEnhancedLRCToWordByWord(enhancedLines: List<EnhancedLyricLine>): String {
        val appleMusicLines = enhancedLines.map { line: EnhancedLyricLine ->
            val words = line.words.map { word: EnhancedWord ->
                mapOf(
                    "text" to word.text,
                    "part" to false, // Not syllables, just words
                    "timestamp" to word.timestamp,
                    "endtime" to word.endtime
                )
            }
            
            mapOf(
                "text" to words,
                "background" to false,
                "timestamp" to line.lineTimestamp,
                "endtime" to line.lineEndtime
            )
        }
        
        return com.google.gson.Gson().toJson(appleMusicLines)
    }
    
    /**
     * Format timestamp to LRC format [mm:ss.xx]
     */
    private fun formatLRCTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = (milliseconds % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, millis)
    }
    
    // TODO: Implement export functionality for Enhanced LRC format
    /**
     * Export word-by-word lyrics to Enhanced LRC format
     * @param lyricsData Lyrics data containing word-by-word JSON
     * @return Enhanced LRC formatted string, or null if not available
     */
    fun exportToEnhancedLRC(lyricsData: LyricsData): String? {
        val wordByWordJson = lyricsData.wordByWordLyrics ?: return null
        
        try {
            // Parse the word-by-word JSON directly
            val gson = com.google.gson.Gson()
            val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val parsedLines: List<Map<String, Any>> = gson.fromJson(wordByWordJson, listType)
            
            if (parsedLines.isEmpty()) return null
            
            // Convert to Enhanced LRC format
            val enhancedLines = parsedLines.mapNotNull { lineMap ->
                @Suppress("UNCHECKED_CAST")
                val wordsData = lineMap["text"] as? List<Map<String, Any>> ?: return@mapNotNull null
                val lineTimestamp = (lineMap["timestamp"] as? Number)?.toLong() ?: 0L
                val lineEndtime = (lineMap["endtime"] as? Number)?.toLong() ?: 0L
                
                val words = wordsData.map { wordMap ->
                    EnhancedWord(
                        text = wordMap["text"] as? String ?: "",
                        timestamp = (wordMap["timestamp"] as? Number)?.toLong() ?: 0L,
                        endtime = (wordMap["endtime"] as? Number)?.toLong() ?: 0L
                    )
                }
                
                EnhancedLyricLine(
                    words = words,
                    lineTimestamp = lineTimestamp,
                    lineEndtime = lineEndtime
                )
            }
            
            return LyricsParser.toEnhancedLRC(enhancedLines)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to Enhanced LRC: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Helper to get file path from content URI
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                        if (columnIndex >= 0) {
                            return cursor.getString(columnIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get file path from URI: ${e.message}")
            }
        }
        
        return null
    }

    /**
     * Fetches lyrics for a song using various sources based on user preference.
     * @param artist Song artist
     * @param title Song title
     * @param songId Optional song ID for cache key - prevents wrong lyrics for songs with similar names
     * @param songUri Optional song URI for embedded lyrics extraction
     * @param sourcePreference User's preferred lyrics source order
     * @param forceRefresh If true, bypasses cache and forces fresh extraction
     */
    suspend fun fetchLyrics(
        artist: String, 
        title: String, 
        songId: String? = null,
        songUri: Uri? = null,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.API_FIRST,
        forceRefresh: Boolean = false
    ): LyricsData? = withContext(Dispatchers.IO) {
        Log.d(TAG, "===== FETCH LYRICS START: $artist - $title (songId=$songId, forceRefresh=$forceRefresh, source=$sourcePreference) =====")
        
        if (artist.isBlank() || title.isBlank())
            return@withContext LyricsData("No lyrics available for this song", null, null)

        // Use song ID in cache key if available to prevent wrong lyrics for songs with similar metadata
        val cacheKey = if (songId != null) {
            "$songId:$artist:$title".lowercase()
        } else {
            "$artist:$title".lowercase()
        }
        
        Log.d(TAG, "===== Cache key: $cacheKey, Cache size: ${lyricsCache.size} =====")
        
        // Check cache unless force refresh is requested
        if (!forceRefresh) {
            lyricsCache[cacheKey]?.let { cached ->
                val lyricLength = cached.plainLyrics?.length ?: cached.syncedLyrics?.length ?: 0
                Log.d(TAG, "===== RETURNING IN-MEMORY CACHED LYRICS ($lyricLength chars) =====")
                return@withContext cached
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        // Define source fetchers
        val fetchFromLocal: suspend () -> LyricsData? = {
            findLocalLyrics(artist, title)
        }
        
        val fetchFromEmbedded: suspend () -> LyricsData? = {
            songUri?.let { uri -> getEmbeddedLyrics(uri) }
        }
        
        val fetchFromAPI: suspend () -> LyricsData? = {
            if (isNetworkAvailable()) {
                fetchLyricsFromAPIs(artist, title)
            } else {
                null
            }
        }
        
        // Try sources in order based on preference, with fallback to others
        val sourceFetchers = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> listOf(fetchFromAPI, fetchFromEmbedded, fetchFromLocal)
            LyricsSourcePreference.EMBEDDED_FIRST -> listOf(fetchFromEmbedded, fetchFromAPI, fetchFromLocal)
            LyricsSourcePreference.LOCAL_FIRST -> listOf(fetchFromLocal, fetchFromEmbedded, fetchFromAPI)
        }
        
        // Try each source in order until we find lyrics
        for ((index, fetcher) in sourceFetchers.withIndex()) {
            try {
                val lyrics = fetcher()
                if (lyrics != null && lyrics.hasLyrics()) {
                    val sourceName = when (sourceFetchers[index]) {
                        fetchFromAPI -> "API"
                        fetchFromEmbedded -> "Embedded"
                        fetchFromLocal -> "Local"
                        else -> "Unknown"
                    }
                    Log.d(TAG, "Found lyrics from $sourceName for: $artist - $title")
                    lyricsCache[cacheKey] = lyrics
                    if (sourceName == "API") {
                        // Only save API-fetched lyrics to local cache
                        saveLocalLyrics(artist, title, lyrics)
                    }
                    return@withContext lyrics
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from source ${index + 1}: ${e.message}")
                // Continue to next source
            }
        }

        // No lyrics found from any source
        Log.d(TAG, "No lyrics found from any source for: $artist - $title")
        return@withContext LyricsData("No lyrics found for this song", null, null)
    }
    
    /**
     * Fetches lyrics from online APIs (LRCLib, etc.)
     * Extracted as a separate method for cleaner code
     */
    private suspend fun fetchLyricsFromAPIs(artist: String, title: String): LyricsData? {
        val cleanArtist = artist.trim().replace(Regex("\\(.*?\\)"), "").trim()
        val cleanTitle = title.trim().replace(Regex("\\(.*?\\)"), "").trim()

        var plainLyrics: String? = null
        var syncedLyrics: String? = null
        var wordByWordLyrics: String? = null

            // ---- LRCLib (Enhanced search with multiple strategies - line-by-line synced) ----
            if (NetworkClient.isLrcLibApiEnabled() && lrclibApiService != null) {
                try {
                    // Strategy 1: Search by track name and artist name
                    var results =
                    lrclibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)

                // If no results, try fallback strategies
                if (results.isEmpty()) {
                    // Strategy 2: Search with generic query combining artist and title
                    val query = "$cleanArtist $cleanTitle"
                    results = lrclibApiService.searchLyrics(query = query)
                }

                if (results.isEmpty()) {
                    // Strategy 3: Try without parenthetical content and with simplified names
                    val simplifiedArtist =
                        cleanArtist.split(" feat.", " ft.", " featuring").first().trim()
                    val simplifiedTitle =
                        cleanTitle.split(" feat.", " ft.", " featuring").first().trim()
                    results = lrclibApiService.searchLyrics(
                        trackName = simplifiedTitle,
                        artistName = simplifiedArtist
                    )
                }

                // Find the best match - prioritize exact matches, then synced lyrics, then any lyrics
                val bestMatch = results.firstOrNull { result ->
                    val artistMatch =
                        result.artistName?.lowercase()?.contains(cleanArtist.lowercase()) == true ||
                                cleanArtist.lowercase()
                                    .contains(result.artistName?.lowercase() ?: "")
                    val titleMatch =
                        result.trackName?.lowercase()?.contains(cleanTitle.lowercase()) == true ||
                                cleanTitle.lowercase().contains(result.trackName?.lowercase() ?: "")

                    (artistMatch && titleMatch) && result.hasLyrics()
                } ?: results.firstOrNull { it.hasSyncedLyrics() } // Prefer synced lyrics
                ?: results.firstOrNull { it.hasLyrics() } // Then any lyrics

                bestMatch?.let { bm ->
                    syncedLyrics = bm.getSyncedLyricsOrNull()
                    plainLyrics = bm.getPlainLyricsOrNull()

                    if (syncedLyrics != null || plainLyrics != null) {
                        Log.d(
                            TAG,
                            "LRCLib lyrics found - Synced: ${syncedLyrics != null}, Plain: ${plainLyrics != null}"
                        )
                        val lyricsData = LyricsData(plainLyrics, syncedLyrics, wordByWordLyrics)
                        return lyricsData
                    }
                }
                } catch (e: Exception) {
                    Log.e(TAG, "LRCLib lyrics fetch failed: ${e.message}", e)
                }
            }

        // No lyrics found from APIs
        return null
    }

    /**
     * Finds local lyrics file in app's files directory OR next to the music file
     * Supports both .lrc files (in music folder) and .json cache files (in app folder)
     */
    private fun findLocalLyrics(artist: String, title: String): LyricsData? {
        Log.d(TAG, "===== findLocalLyrics START: $artist - $title =====")
        
        // First, check for .lrc file next to the music file
        try {
            val lrcLyrics = findLrcFileForSong(artist, title)
            if (lrcLyrics != null) {
                Log.d(TAG, "===== FOUND LOCAL .LRC FILE =====")
                return lrcLyrics
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for .lrc file: ${e.message}")
        }
        
        // Second, check for cached JSON lyrics in app's files directory
        val fileName = "${artist}_${title}.json".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(context.filesDir, "lyrics/$fileName")
        Log.d(TAG, "===== Checking for saved JSON file: $fileName (exists=${file.exists()}) =====")
        return try {
            if (file.exists()) {
                val json = file.readText()
                val data = Gson().fromJson(json, LyricsData::class.java)
                Log.d(TAG, "===== LOADED LYRICS FROM SAVED JSON FILE (THIS IS THE OLD CACHE!) =====")
                data
            } else {
                Log.d(TAG, "===== NO SAVED JSON FILE FOUND =====")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local lyrics file: ${e.message}", e)
            null
        }
    }
    
    /**
     * Searches for .lrc file next to the music file
     * Looks for files with same name as the song or generic patterns
     */
    private fun findLrcFileForSong(artist: String, title: String): LyricsData? {
        try {
            // Find the song in MediaStore to get its path
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
            val selectionArgs = arrayOf(title, artist)
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val songPath = cursor.getString(dataIndex)
                    
                    if (songPath != null) {
                        val songFile = File(songPath)
                        val directory = songFile.parentFile
                        val songNameWithoutExt = songFile.nameWithoutExtension
                        
                        if (directory != null && directory.exists()) {
                            // Look for .lrc file with same name as the song
                            val lrcFile = File(directory, "$songNameWithoutExt.lrc")
                            if (lrcFile.exists() && lrcFile.canRead()) {
                                val lrcContent = lrcFile.readText()
                                return parseLrcFile(lrcContent)
                            }
                            
                            // Also try with artist - title pattern
                            val cleanArtist = artist.replace(Regex("[^a-zA-Z0-9]"), "_")
                            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
                            val alternativeLrcFile = File(directory, "${cleanArtist}_${cleanTitle}.lrc")
                            if (alternativeLrcFile.exists() && alternativeLrcFile.canRead()) {
                                val lrcContent = alternativeLrcFile.readText()
                                return parseLrcFile(lrcContent)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for .lrc file", e)
        }
        return null
    }
    
    /**
     * Parses .lrc file content into LyricsData format
     * LRC format: [mm:ss.xx]lyrics text
     */
    private fun parseLrcFile(lrcContent: String): LyricsData? {
        try {
            if (lrcContent.isBlank()) return null
            
            val lines = lrcContent.lines()
            val syncedLines = mutableListOf<String>()
            val plainLines = mutableListOf<String>()
            var hasSyncedLyrics = false
            
            // Pattern to match LRC timestamps [mm:ss.xx] or [mm:ss]
            val timestampPattern = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)") 
            
            // Check for Enhanced LRC format with word-level timestamps
            val hasWordTimestamps = LyricsParser.hasWordTimestamps(lrcContent)
            
            if (hasWordTimestamps) {
                Log.d(TAG, "Detected Enhanced LRC format in .lrc file with word-level timestamps")
                
                // Parse Enhanced LRC and convert to word-by-word format
                val enhancedLines = LyricsParser.parseEnhancedLRC(lrcContent)
                
                if (enhancedLines.isNotEmpty()) {
                    // Convert to Apple Music word-by-word format (JSON)
                    val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)
                    
                    // Also extract plain text and line-synced LRC
                    val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                        line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                    }
                    
                    val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                        val timestamp = formatLRCTimestamp(line.lineTimestamp)
                        val text = line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                        "[$timestamp]$text"
                    }
                    
                    Log.d(TAG, "Successfully converted Enhanced LRC from .lrc file to word-by-word format (${enhancedLines.size} lines)")
                    return LyricsData(plainText, syncedLrc, wordByWordJson)
                }
            }
            
            // Standard LRC format (line-by-line only)
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue
                
                // Check if line has timestamp
                val match = timestampPattern.find(trimmedLine)
                if (match != null) {
                    hasSyncedLyrics = true
                    syncedLines.add(trimmedLine) // Keep the timestamp for synced lyrics
                    val lyricsText = match.groupValues[4].trim()
                    if (lyricsText.isNotEmpty()) {
                        plainLines.add(lyricsText) // Extract just the lyrics text for plain version
                    }
                } else {
                    // Metadata line (like [ar:], [ti:], [al:]) or plain text
                    if (!trimmedLine.startsWith("[") || !trimmedLine.contains("]")) {
                        plainLines.add(trimmedLine)
                    }
                }
            }
            
            val plainLyrics = if (plainLines.isNotEmpty()) plainLines.joinToString("\n") else null
            val syncedLyrics = if (hasSyncedLyrics && syncedLines.isNotEmpty()) syncedLines.joinToString("\n") else null
            
            if (plainLyrics != null || syncedLyrics != null) {
                Log.d(TAG, "Successfully parsed .lrc file - Synced: ${syncedLyrics != null}, Plain: ${plainLyrics != null}")
                return LyricsData(plainLyrics, syncedLyrics, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing .lrc file", e)
        }
        return null
    }

    /**

     * Saves lyrics to a local file
     */
    private fun saveLocalLyrics(artist: String, title: String, lyricsData: LyricsData) {
        try {
            val fileName = "${artist}_${title}.json".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val file = File(lyricsDir, fileName)
            val json = Gson().toJson(lyricsData)
            file.writeText(json)
            Log.d(TAG, "Saved lyrics to local file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to local file: ${e.message}", e)
        }
    }

    /**
     * Fetches album art from Deezer API for albums without artwork
     */
    suspend fun fetchAlbumArtwork(albums: List<Album>): List<Album> = withContext(Dispatchers.IO) {
        val updatedAlbums = mutableListOf<Album>()

        for (album in albums) {
            // Check if the album has a content:// URI and if it actually exists
            if (album.artworkUri != null) {
                if (album.artworkUri.toString()
                        .startsWith("content://media/external/audio/albumart")
                ) {
                    // Try to open the input stream to check if the artwork exists
                    var artworkExists = false
                    try {
                        context.contentResolver.openInputStream(album.artworkUri)?.use {
                            artworkExists = true
                        }
                    } catch (e: Exception) {
                        Log.d(
                            TAG,
                            "Album artwork URI exists but can't be accessed for ${album.title}: ${album.artworkUri}",
                            e
                        )
                        artworkExists = false
                    }

                    if (artworkExists) {
                        updatedAlbums.add(album)
                        continue
                    }
                } else {
                    updatedAlbums.add(album)
                    continue
                }
            }

            // Check cache first
            val cacheKey = "${album.artist}:${album.title}"
            val cachedUri = albumImageCache[cacheKey]
            if (cachedUri != null) {
                updatedAlbums.add(album.copy(artworkUri = cachedUri))
                continue
            }

            try {
                // Skip albums with empty or "Unknown" artist/title
                if (album.artist.isBlank() || album.title.isBlank() ||
                    album.artist.equals("Unknown", ignoreCase = true) ||
                    album.title.equals("Unknown", ignoreCase = true)
                ) {
                    updatedAlbums.add(album)
                    continue
                }

                // Generate a custom placeholder image based on album name
                val placeholderUri = chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                    name = album.title,
                    size = 500,
                    cacheDir = context.cacheDir
                )

                // Add delay to avoid rate limiting
                delay(500)

                Log.d(TAG, "Searching for album: ${album.title} by ${album.artist}")

                // Search for the album on Deezer (only if enabled)
                if (NetworkClient.isDeezerApiEnabled() && deezerApiService != null) {
                    // Add delay to avoid rate limiting
                    delay(300)
                    
                    try {
                        // First try searching for the album by title and artist
                        val searchQuery = "${album.title} ${album.artist}"
                        var albumSearchResponse = deezerApiService.searchAlbums(searchQuery)
                        var deezerAlbum = findBestAlbumMatch(albumSearchResponse.data, album.title, album.artist)

                        // If no match, try with just the album title
                        if (deezerAlbum == null && album.title.isNotBlank()) {
                            albumSearchResponse = deezerApiService.searchAlbums(album.title)
                            deezerAlbum = findBestAlbumMatch(albumSearchResponse.data, album.title, album.artist)
                        }

                        if (deezerAlbum != null) {
                            // Choose the highest quality image available for album artwork
                            val imageUrl = when {
                                !deezerAlbum.coverXl.isNullOrEmpty() -> deezerAlbum.coverXl
                                !deezerAlbum.coverBig.isNullOrEmpty() -> deezerAlbum.coverBig
                                !deezerAlbum.coverMedium.isNullOrEmpty() -> deezerAlbum.coverMedium
                                !deezerAlbum.cover.isNullOrEmpty() -> deezerAlbum.cover
                                else -> null
                            }

                            if (!imageUrl.isNullOrEmpty() && imageUrl.startsWith("http")) {
                                val imageUri = Uri.parse(imageUrl)
                                albumImageCache[cacheKey] = imageUri
                                updatedAlbums.add(album.copy(artworkUri = imageUri))
                                Log.d(TAG, "Found Deezer album artwork for: ${album.title}, URL: $imageUrl")
                                continue
                            }
                        } else {
                            Log.d(TAG, "No Deezer album found for: ${album.title} by ${album.artist}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Deezer album search failed for ${album.title}: ${e.message}")
                    }
                }

                // -------- YTMusic fallback (only when local album art is absent) --------
                // Check if we should use YTMusic fallback for albums
                if (NetworkClient.isYTMusicApiEnabled() && ytmusicApiService != null) {
                    var foundAlbumArt = false
                    try {
                        Log.d(TAG, "Trying YTMusic for album: ${album.title} by ${album.artist}")
                        
                        // Create search request for album
                        val searchQuery = "${album.title} ${album.artist}"
                    val searchRequest = YTMusicSearchRequest(
                        context = YTMusicContext(YTMusicClient()),
                        query = searchQuery,
                        params = "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D" // Album search filter
                    )
                    
                    val searchResponse = ytmusicApiService.search(request = searchRequest)
                    if (searchResponse.isSuccessful) {
                        val imageUrl = searchResponse.body()?.extractAlbumImageUrl()
                        if (!imageUrl.isNullOrEmpty()) {
                            val imageUri = Uri.parse(imageUrl)
                            albumImageCache[cacheKey] = imageUri
                            updatedAlbums.add(album.copy(artworkUri = imageUri))
                            Log.d(TAG, "Found YTMusic album art for ${album.title}: $imageUrl")
                            foundAlbumArt = true
                        } else {
                            // Try to get detailed album info for better quality cover art
                            val browseId = searchResponse.body()?.extractAlbumBrowseId()
                            if (!browseId.isNullOrEmpty()) {
                                val browseRequest = YTMusicBrowseRequest(
                                    context = YTMusicContext(YTMusicClient()),
                                    browseId = browseId
                                )
                                val albumResponse = ytmusicApiService.getAlbum(request = browseRequest)
                                if (albumResponse.isSuccessful) {
                                    val detailedImageUrl = albumResponse.body()?.extractAlbumCover()
                                    if (!detailedImageUrl.isNullOrEmpty()) {
                                        val imageUri = Uri.parse(detailedImageUrl)
                                        albumImageCache[cacheKey] = imageUri
                                        updatedAlbums.add(album.copy(artworkUri = imageUri))
                                        Log.d(TAG, "Found detailed YTMusic album art for ${album.title}: $detailedImageUrl")
                                        foundAlbumArt = true
                                    }
                                }
                            }
                        }
                    }
                    } catch (e: Exception) {
                        Log.w(TAG, "YTMusic fallback failed for album ${album.title}: ${e.message}")
                    }

                    // If YTMusic didn't find anything, use placeholder
                    if (!foundAlbumArt) {
                        Log.d(
                            TAG,
                            "No valid image found for album: ${album.title}, using generated placeholder"
                        )
                        albumImageCache[cacheKey] = placeholderUri
                        updatedAlbums.add(album.copy(artworkUri = placeholderUri))
                    }
                } else {
                    // YTMusic is disabled, use placeholder
                    Log.d(
                        TAG,
                        "No valid image found for album: ${album.title}, using generated placeholder"
                    )
                    albumImageCache[cacheKey] = placeholderUri
                    updatedAlbums.add(album.copy(artworkUri = placeholderUri))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching album artwork for ${album.title}", e)
                // Try to use a generated placeholder
                try {
                    val placeholderUri =
                        chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                            name = album.title,
                            size = 500,
                            cacheDir = context.cacheDir
                        )
                    updatedAlbums.add(album.copy(artworkUri = placeholderUri))
                } catch (e2: Exception) {
                    // If even placeholder generation fails, keep the original album without changes
                    updatedAlbums.add(album)
                }
            }
        }

        updatedAlbums
    }

    /**
     * Fetches track/song images from YTMusic API for songs without artwork.
     * This is used as a fallback when local album art is absent and other APIs fail.
     */
    suspend fun fetchTrackArtwork(songs: List<Song>): List<Song> = withContext(Dispatchers.IO) {
        val updatedSongs = mutableListOf<Song>()

        for (song in songs) {
            // Only fetch if song doesn't have artwork already
            if (song.artworkUri != null) {
                updatedSongs.add(song)
                continue
            }

            // Check if album has artwork - if yes, we don't need track-specific artwork
            if (song.albumId.isNotBlank()) {
                val albums = loadAlbums()
                val album = albums.find { it.id == song.albumId }
                if (album?.artworkUri != null) {
                    // Album has artwork, no need for track-specific image
                    updatedSongs.add(song)
                    continue
                }
            }

            val cacheKey = "${song.artist}:${song.title}"
            
            // Check cache first
            val cachedUri = albumImageCache[cacheKey] // Reuse album cache for tracks
            if (cachedUri != null) {
                updatedSongs.add(song.copy(artworkUri = cachedUri))
                continue
            }

            try {
                // Skip songs with empty or "Unknown" artist/title
                if (song.artist.isBlank() || song.title.isBlank() ||
                    song.artist.equals("Unknown", ignoreCase = true) ||
                    song.title.equals("Unknown", ignoreCase = true)
                ) {
                    updatedSongs.add(song)
                    continue
                }

                if (!NetworkClient.isYTMusicApiEnabled() || ytmusicApiService == null) {
                    Log.d(TAG, "YTMusic API is disabled or unavailable, skipping track artwork for: ${song.title}")
                    updatedSongs.add(song)
                    continue
                }

                Log.d(TAG, "Searching YTMusic for track: ${song.title} by ${song.artist}")

                // Add delay to avoid rate limiting
                delay(200)

                // Create search request for song/track
                val searchQuery = "${song.title} ${song.artist}"
                val searchRequest = YTMusicSearchRequest(
                    context = YTMusicContext(YTMusicClient()),
                    query = searchQuery,
                    params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D" // Song search filter
                )

                val searchResponse = ytmusicApiService.search(request = searchRequest)
                if (searchResponse.isSuccessful) {
                    // For tracks, we can extract image from the first result
                    val imageUrl = searchResponse.body()?.extractAlbumImageUrl() // Tracks use same thumbnail structure
                    if (!imageUrl.isNullOrEmpty()) {
                        val imageUri = Uri.parse(imageUrl)
                        albumImageCache[cacheKey] = imageUri // Cache for future use
                        updatedSongs.add(song.copy(artworkUri = imageUri))
                        Log.d(TAG, "Found YTMusic track image for ${song.title}: $imageUrl")
                        continue
                    }
                }

                Log.d(TAG, "No YTMusic image found for track: ${song.title}")
                updatedSongs.add(song)

            } catch (e: Exception) {
                Log.w(TAG, "YTMusic track image fetch failed for ${song.title}: ${e.message}")
                updatedSongs.add(song)
            }
        }

        updatedSongs
    }

    suspend fun getSongsForArtist(artistId: String): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = loadSongs() // Ensure songs are loaded once
        val allArtists = loadArtists() // Ensure artists are loaded once
        val appSettings = AppSettings.getInstance(context)
        val groupByAlbumArtist = appSettings.groupByAlbumArtist.value

        Log.d("MusicRepository", "Getting songs for artist ID: $artistId")

        // Find the artist by ID
        val artist = allArtists.find { it.id == artistId }
        if (artist == null) {
            Log.e("MusicRepository", "Artist not found with ID: $artistId")
            return@withContext emptyList()
        }

        Log.d("MusicRepository", "Found artist: ${artist.name} (ID: $artistId, groupByAlbumArtist=$groupByAlbumArtist)")

        // Filter songs that match the artist's name
        val artistSongs = allSongs.filter { song ->
            if (groupByAlbumArtist) {
                // When grouping by album artist, match exactly against album artist (with fallback to track artist)
                val songArtistName = (song.albumArtist?.takeIf { it.isNotBlank() } ?: song.artist).trim()
                songArtistName == artist.name
            } else {
                // When not grouping, check if artist name appears in the track artist field (exact or as part of collaboration)
                val artistNames = splitArtistNames(song.artist)
                artistNames.any { it.equals(artist.name, ignoreCase = true) }
            }
        }

        Log.d("MusicRepository", "Found ${artistSongs.size} songs for artist: ${artist.name}")
        return@withContext artistSongs
    }

    suspend fun getAlbumsForArtist(artistId: String): List<Album> = withContext(Dispatchers.IO) {
        val allAlbums = loadAlbums() // Ensure albums are loaded once
        val allArtists = loadArtists() // Ensure artists are loaded once
        val allSongs = loadSongs() // Need songs to check album artist
        val appSettings = AppSettings.getInstance(context)
        val groupByAlbumArtist = appSettings.groupByAlbumArtist.value

        Log.d("MusicRepository", "Getting albums for artist ID: $artistId")

        // Find the artist by ID
        val artist = allArtists.find { it.id == artistId }
        if (artist == null) {
            Log.e("MusicRepository", "Artist not found with ID: $artistId")
            return@withContext emptyList()
        }

        Log.d("MusicRepository", "Found artist: ${artist.name} (ID: $artistId, groupByAlbumArtist=$groupByAlbumArtist)")

        // Filter albums that match the artist's name
        val artistAlbums = allAlbums.filter { album ->
            if (groupByAlbumArtist) {
                // When grouping by album artist, check if any song in the album has matching album artist
                allSongs.any { song ->
                    song.album == album.title &&
                    song.albumId == album.id &&
                    (song.albumArtist?.takeIf { it.isNotBlank() } ?: song.artist).trim() == artist.name
                }
            } else {
                // When not grouping, check if artist appears in any song's track artist field for this album
                allSongs.any { song ->
                    song.album == album.title &&
                    song.albumId == album.id &&
                    splitArtistNames(song.artist).any { it.equals(artist.name, ignoreCase = true) }
                }
            }
        }

        Log.d("MusicRepository", "Found ${artistAlbums.size} albums for artist: ${artist.name}")
        return@withContext artistAlbums
    }

    suspend fun createPlaylist(name: String): Playlist {
        return Playlist(
            id = System.currentTimeMillis().toString(),
            name = name
        )
    }

    // Mock data for locations
    fun getLocations(): List<PlaybackLocation> {
        return listOf(
            PlaybackLocation(
                id = "living_room",
                name = "Living room",
                icon = 0 // Replace with actual icon resource
            ),
            PlaybackLocation(
                id = "bedroom",
                name = "Bedroom",
                icon = 0 // Replace with actual icon resource
            ),
            PlaybackLocation(
                id = "kitchen",
                name = "Kitchen",
                icon = 0 // Replace with actual icon resource
            )
        )
    }

    /**
     * Checks if the device is currently connected to the internet
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    /**
     * Finds locally stored artist image
     */
    private fun findLocalArtistImage(artistName: String): Uri? {
        val fileName = "${artistName}.jpg".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(context.filesDir, "artist_images/$fileName")
        return if (file.exists()) Uri.fromFile(file) else null
    }

    /**
     * Saves artist image to local storage
     */
    private fun saveLocalArtistImage(artistName: String, imageUrl: String) {
        try {
            val fileName = "${artistName}.jpg".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val imageDir = File(context.filesDir, "artist_images")
            imageDir.mkdirs()

            val file = File(imageDir, fileName)
            URL(imageUrl).openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Saved artist image to local file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving artist image to local file: ${e.message}", e)
        }
    }
    
    /**
     * Clears all in-memory caches
     */
    fun clearInMemoryCaches() {
        try {
            synchronized(artistImageCache) {
                artistImageCache.clear()
            }
            synchronized(albumImageCache) {
                albumImageCache.clear()
            }
            synchronized(lyricsCache) {
                lyricsCache.clear()
            }
            Log.d(TAG, "Cleared all in-memory caches (artist images, album images, lyrics)")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing in-memory caches", e)
        }
    }
    
    /**
     * Clears only the lyrics cache
     */
    fun clearLyricsCache() {
        try {
            synchronized(lyricsCache) {
                val count = lyricsCache.size
                lyricsCache.clear()
                Log.d(TAG, "===== CLEARED IN-MEMORY LYRICS CACHE ($count entries) =====")
            }
            
            // Also delete all saved local lyrics files
            try {
                val lyricsDir = File(context.filesDir, "lyrics")
                if (lyricsDir.exists() && lyricsDir.isDirectory) {
                    val files = lyricsDir.listFiles()
                    val deletedCount = files?.count { it.delete() } ?: 0
                    Log.d(TAG, "===== DELETED $deletedCount SAVED LYRICS FILES FROM DISK =====")
                } else {
                    Log.d(TAG, "===== NO LYRICS DIRECTORY FOUND ON DISK =====")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting saved lyrics files: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing lyrics cache", e)
        }
    }
    
    /**
     * Performs cache maintenance - removes expired entries and optimizes memory usage
     */
    suspend fun performCacheMaintenance() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting cache maintenance...")
            
            // Check if cache cleanup is needed based on app settings
            val maxCacheSize = try {
                val settings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context)
                settings.maxCacheSize.value
            } catch (e: Exception) {
                Log.w(TAG, "Error getting cache size setting, using default", e)
                512L * 1024L * 1024L // Default 512MB
            }
            
            // Clean up file system cache if needed
            val cacheManager = chromahub.rhythm.app.util.CacheManager
            val cacheCleanedUp = cacheManager.cleanCacheIfNeeded(context, maxCacheSize)
            
            if (cacheCleanedUp) {
                Log.d(TAG, "File system cache was cleaned up")
            }
            
            // Optimize in-memory caches
            val initialArtistCacheSize = artistImageCache.size
            val initialAlbumCacheSize = albumImageCache.size
            val initialLyricsCacheSize = lyricsCache.size
            
            // The LinkedHashMap LRU implementation will automatically evict oldest entries
            // when new entries are added and the cache exceeds its limit
            
            Log.d(TAG, "Cache maintenance completed. " +
                    "Artist cache: $initialArtistCacheSize entries, " +
                    "Album cache: $initialAlbumCacheSize entries, " +
                    "Lyrics cache: $initialLyricsCacheSize entries")
                    
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache maintenance", e)
        }
    }
    
    /**
     * Gets the current size of in-memory caches
     * @return A map containing cache names and their sizes
     */
    fun getInMemoryCacheInfo(): Map<String, Int> {
        return mapOf(
            "artistImageCache" to artistImageCache.size,
            "albumImageCache" to albumImageCache.size,
            "lyricsCache" to lyricsCache.size
        )
    }
    
    /**
     * Finds the best matching Deezer artist from search results using fuzzy matching
     */
    private fun findBestMatch(artists: List<DeezerArtist>, originalName: String): DeezerArtist? {
        if (artists.isEmpty()) return null
        
        val lowerOriginal = originalName.lowercase().trim()
        
        // First, try exact match (case insensitive)
        artists.find { it.name.lowercase().trim() == lowerOriginal }?.let { return it }
        
        // Second, try starts with match
        artists.find { it.name.lowercase().trim().startsWith(lowerOriginal) }?.let { return it }
        
        // Third, try contains match
        artists.find { it.name.lowercase().contains(lowerOriginal) }?.let { return it }
        
        // Fourth, try reversed contains (original contains artist name)
        artists.find { lowerOriginal.contains(it.name.lowercase().trim()) }?.let { return it }
        
        // Fifth, try word-by-word matching
        val originalWords = lowerOriginal.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (originalWords.isNotEmpty()) {
            artists.find { artist ->
                val artistWords = artist.name.lowercase().split(Regex("\\s+"))
                originalWords.any { originalWord ->
                    artistWords.any { artistWord ->
                        originalWord == artistWord || 
                        originalWord.startsWith(artistWord) || 
                        artistWord.startsWith(originalWord)
                    }
                }
            }?.let { return it }
        }
        
        // Finally, return the first result with the most fans (most popular)
        return artists.maxByOrNull { it.nbFan }
    }
    
    /**
     * Finds the best matching Deezer album from search results using fuzzy matching
     */
    private fun findBestAlbumMatch(albums: List<DeezerAlbum>, originalTitle: String, originalArtist: String): DeezerAlbum? {
        if (albums.isEmpty()) return null

        val lowerTitle = originalTitle.lowercase().trim()
        val lowerArtist = originalArtist.lowercase().trim()

        // First, try exact match (case insensitive) for both title and artist
        albums.find { album ->
            album.title.lowercase().trim() == lowerTitle &&
            (album.artist?.name?.lowercase()?.trim() == lowerArtist || lowerArtist.contains(album.artist?.name?.lowercase()?.trim() ?: ""))
        }?.let { return it }

        // Second, try exact title match with fuzzy artist match
        albums.find { album ->
            album.title.lowercase().trim() == lowerTitle
        }?.let { return it }

        // Third, try contains match for title
        albums.find { album ->
            album.title.lowercase().contains(lowerTitle) || lowerTitle.contains(album.title.lowercase())
        }?.let { return it }

        // Fourth, try word-by-word matching for title
        val titleWords = lowerTitle.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (titleWords.isNotEmpty()) {
            albums.find { album ->
                val albumWords = album.title.lowercase().split(Regex("\\s+"))
                titleWords.any { titleWord ->
                    albumWords.any { albumWord ->
                        titleWord == albumWord ||
                        titleWord.startsWith(albumWord) ||
                        albumWord.startsWith(titleWord)
                    }
                }
            }?.let { return it }
        }

        // Finally, return the first result with the most tracks (most complete album)
        return albums.maxByOrNull { it.nbTracks }
    }

    /**
     * Detects genres for songs in background after initial app load
     * This method processes songs in batches to avoid blocking the UI
     * @param songs List of songs to detect genres for
     * @param onProgress Callback to report progress (current, total)
     * @param onComplete Callback when genre detection is complete
     */
    suspend fun detectGenresInBackground(
        songs: List<Song>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: ((List<Song>) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val songsWithoutGenres = songs.filter { 
                it.genre == null || it.genre.isBlank() || it.genre.equals("unknown", ignoreCase = true) 
            }
            
            if (songsWithoutGenres.isEmpty()) {
                Log.d(TAG, "All songs already have genres, skipping background detection")
                onComplete?.invoke(songs)
                return@withContext
            }

            Log.d(TAG, "Starting background genre detection for ${songsWithoutGenres.size} songs out of ${songs.size} total")
            val updatedSongs = mutableListOf<Song>()
            val batchSize = 50 // Process in smaller batches for better responsiveness
            var processedCount = 0

            songsWithoutGenres.chunked(batchSize).forEach { batch ->
                val batchStartTime = System.currentTimeMillis()

                batch.forEach { song ->
                    try {
                        val songId = song.id.toLongOrNull()
                        if (songId == null) {
                            Log.w(TAG, "Invalid song ID for ${song.title}, skipping")
                            updatedSongs.add(song)
                            processedCount++
                            onProgress?.invoke(processedCount, songsWithoutGenres.size)
                            return@forEach
                        }
                        
                        val contentUri = song.uri
                        val genre = getGenreForSong(context, contentUri, songId.toInt())

                        if (genre != null && genre.isNotBlank() && !genre.equals("unknown", ignoreCase = true)) {
                            val updatedSong = song.copy(genre = genre)
                            updatedSongs.add(updatedSong)
                            // Cache the detected genre using async apply() to prevent blocking
                            try {
                                genrePrefs.edit().putString("genre_$songId", genre).apply()
                                Log.d(TAG, "Detected and cached genre '$genre' for song ID $songId: ${song.title}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to cache genre for song ID $songId", e)
                            }
                        } else {
                            // Keep the original song if no valid genre was found
                            updatedSongs.add(song)
                        }

                        processedCount++
                        onProgress?.invoke(processedCount, songsWithoutGenres.size)

                    } catch (e: Exception) {
                        Log.w(TAG, "Error detecting genre for song ${song.title}", e)
                        updatedSongs.add(song) // Keep original song on error
                        processedCount++
                        onProgress?.invoke(processedCount, songsWithoutGenres.size)
                    }
                }

                val batchEndTime = System.currentTimeMillis()
                val batchDuration = batchEndTime - batchStartTime
                Log.d(TAG, "Processed batch of ${batch.size} songs in ${batchDuration}ms")

                // Yield control to allow other coroutines to run
                yield()

                // Small delay between batches to prevent overwhelming the system
                if (batchDuration < 100) { // If batch processed quickly, add a small delay
                    delay(50)
                }
            }

            val finalSongs = songs.map { originalSong ->
                updatedSongs.find { it.id == originalSong.id } ?: originalSong
            }

            val genreCount = finalSongs.count { song -> 
                song.genre != null && song.genre.isNotBlank() && !song.genre.equals("unknown", ignoreCase = true)
            }
            Log.d(TAG, "Background genre detection complete. $genreCount out of ${finalSongs.size} songs now have genres")
            onComplete?.invoke(finalSongs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during genre detection", e)
            // Always invoke onComplete to prevent hanging UI
            onComplete?.invoke(songs)
        }
    }
    
    /**
     * Extracts audio metadata (bitrate, sample rate, channels, codec) for songs in background
     * This is done lazily to avoid slowing down initial library load
     */
    suspend fun extractAudioMetadataInBackground(
        songs: List<Song>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: ((List<Song>) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val songsWithoutMetadata = songs.filter { 
            it.bitrate == null || it.sampleRate == null || it.channels == null || it.codec == null 
        }
        
        if (songsWithoutMetadata.isEmpty()) {
            Log.d(TAG, "All songs already have audio metadata, skipping background extraction")
            onComplete?.invoke(songs)
            return@withContext
        }

        Log.d(TAG, "Starting background audio metadata extraction for ${songsWithoutMetadata.size} songs")
        val updatedSongs = mutableListOf<Song>()
        val batchSize = 20 // Smaller batches for metadata extraction (more expensive operation)
        var processedCount = 0

        songsWithoutMetadata.chunked(batchSize).forEach { batch ->
            val batchStartTime = System.currentTimeMillis()

            batch.forEach { song ->
                try {
                    // Use AudioFormatDetector for more accurate metadata extraction
                    val formatInfo = AudioFormatDetector.detectFormat(context, song.uri, song)
                    
                    Log.d(TAG, "Extracted metadata for ${song.title}: codec=${formatInfo.codec}, bitrate=${formatInfo.bitrateKbps}kbps, sampleRate=${formatInfo.sampleRateHz}Hz, channels=${formatInfo.channelCount}, bitDepth=${formatInfo.bitDepth}")
                    
                    val updatedSong = song.copy(
                        bitrate = if (formatInfo.bitrateKbps > 0) formatInfo.bitrateKbps * 1000 else null,
                        sampleRate = if (formatInfo.sampleRateHz > 0) formatInfo.sampleRateHz else null,
                        channels = if (formatInfo.channelCount > 0) formatInfo.channelCount else null,
                        codec = if (formatInfo.codec != "Unknown") formatInfo.codec else null
                    )
                    updatedSongs.add(updatedSong)
                    
                    processedCount++
                    onProgress?.invoke(processedCount, songsWithoutMetadata.size)

                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting audio metadata for song ${song.title}", e)
                    updatedSongs.add(song) // Keep original song on error
                    processedCount++
                    onProgress?.invoke(processedCount, songsWithoutMetadata.size)
                }
            }

            val batchEndTime = System.currentTimeMillis()
            val batchDuration = batchEndTime - batchStartTime
            Log.d(TAG, "Processed metadata batch of ${batch.size} songs in ${batchDuration}ms")

            // Yield control to allow other coroutines to run
            yield()

            // Small delay between batches to prevent overwhelming the system
            if (batchDuration < 200) { // If batch processed quickly, add a small delay
                delay(100)
            }
        }

        val finalSongs = songs.map { originalSong ->
            updatedSongs.find { it.id == originalSong.id } ?: originalSong
        }

        Log.d(TAG, "Background audio metadata extraction complete. Updated ${updatedSongs.size} songs")
        onComplete?.invoke(finalSongs)
    }
    
    /**
     * Cleanup method to clear caches and cancel coroutines
     * Call this when the repository is no longer needed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up MusicRepository...")
        
        // Cancel all coroutines
        repositoryScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        
        // Clear all caches
        artistImageCache.clear()
        albumImageCache.clear()
        lyricsCache.clear()
        
        // Clear rate limiting maps
        lastApiCalls.clear()
        apiCallCounts.clear()
        
        Log.d(TAG, "MusicRepository cleaned up")
    }
    
    /**
     * Get all songs for a specific album by album ID.
     * This retrieves the songs list from the songs flow and filters by album ID.
     */
    suspend fun getSongsForAlbum(albumId: String): List<PlayableItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allSongs = loadSongs()
            val allAlbums = loadAlbums()

            Log.d(TAG, "Getting songs for album ID: $albumId")

            // Find the album by ID
            val album = allAlbums.find { it.id == albumId }
            if (album == null) {
                Log.e(TAG, "Album not found with ID: $albumId")
                return@withContext emptyList()
            }

            Log.d(TAG, "Found album: ${album.title} (ID: $albumId)")

            // Filter songs that match the album's title and ID
            val albumSongs = allSongs.filter { song ->
                val albumTitleMatch = song.album == album.title
                val albumIdMatch = song.albumId == albumId
                albumTitleMatch && albumIdMatch
            }

            Log.d(TAG, "Found ${albumSongs.size} songs for album: ${album.title}")

            return@withContext albumSongs.map { it as PlayableItem }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting songs for album $albumId", e)
            emptyList()
        }
    }

    /**
     * Local method to get songs for album that returns Song objects (not PlayableItem)
     * This is used by the local MusicViewModel which works with Song objects directly
     */
    suspend fun getSongsForAlbumLocal(albumId: String): List<Song> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allSongs = loadSongs()
            val allAlbums = loadAlbums()

            Log.d(TAG, "Getting songs for album ID (local): $albumId")

            // Find the album by ID
            val album = allAlbums.find { it.id == albumId }
            if (album == null) {
                Log.e(TAG, "Album not found with ID: $albumId")
                return@withContext emptyList()
            }

            Log.d(TAG, "Found album: ${album.title} (ID: $albumId)")

            // Filter songs that match the album's title and ID
            val albumSongs = allSongs.filter { song ->
                val albumTitleMatch = song.album == album.title
                val albumIdMatch = song.albumId == albumId
                albumTitleMatch && albumIdMatch
            }

            Log.d(TAG, "Found ${albumSongs.size} songs for album: ${album.title}")

            return@withContext albumSongs
        } catch (e: Exception) {
            Log.e(TAG, "Error getting songs for album $albumId", e)
            emptyList()
        }
    }
}
