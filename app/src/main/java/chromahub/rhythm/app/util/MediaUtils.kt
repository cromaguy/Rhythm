package chromahub.rhythm.app.util

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import androidx.core.net.toUri
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ExtendedSongInfo
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import android.os.ParcelFileDescriptor


/**
 * Exception wrapper for RecoverableSecurityException that includes the pending intent
 * and temp file path for retry after permission is granted
 */
class RecoverableSecurityExceptionWrapper(
    message: String,
    val userAction: RemoteAction,
    val fileUri: Uri,
    val tempFilePath: String
) : Exception(message)

/**
 * Data class representing a pending write request for Android 11+
 * This is used when the system needs user permission to modify a file
 */
data class PendingWriteRequest(
    val intentSender: IntentSender,
    val song: Song,
    val newTitle: String,
    val newArtist: String,
    val newAlbum: String,
    val newGenre: String,
    val newYear: Int,
    val newTrackNumber: Int,
    val tempFilePath: String,
    val artworkUriString: String?,
    val removeArtwork: Boolean,
    val newAlbumArtist: String? = null,
    val newComposer: String? = null,
    val newDiscNumber: Int = 1
)

/**
 * Data class representing a pending batch write request for Android 11+
 * This is used when the system needs user permission to modify multiple files at once
 */
data class PendingBatchWriteRequest(
    val intentSender: android.content.IntentSender,
    val songs: List<Song>,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val year: Int?,
    val artworkUriString: String?,
    val removeArtwork: Boolean,
    val onProgress: (Int, Int) -> Unit,
    val onComplete: (successCount: Int, failCount: Int) -> Unit
)

/**
 * Data class representing a pending lyrics write request for Android 11+
 */
data class PendingLyricsWriteRequest(
    val intentSender: IntentSender,
    val song: Song,
    val lyrics: String,
    val tempFilePath: String
)

/**
 * Utility class for handling media-related operations
 */
object MediaUtils {
    private const val TAG = "MediaUtils"
    private const val EMBEDDED_ARTWORK_CACHE_DIR = "embedded_artwork"
    private const val EMBEDDED_ART_CACHE_MAX_BYTES = 256L * 1024 * 1024
    private const val EMBEDDED_ART_CACHE_MAX_FILES = 1200
    private const val EMBEDDED_ART_CACHE_CLEANUP_INTERVAL_MS = 10 * 60 * 1000L

    @Volatile
    private var lastEmbeddedArtworkCleanupMs: Long = 0L

    private fun applyArtworkToTag(
        context: Context,
        tag: Tag,
        artworkUri: Uri?,
        removeArtwork: Boolean
    ) {
        if (!removeArtwork && artworkUri == null) return

        try {
            tag.deleteArtworkField()
        } catch (e: Throwable) {
            Log.w(TAG, "Could not clear existing artwork field", e)
        }

        if (removeArtwork || artworkUri == null) return

        try {
            val mimeType = context.contentResolver.getType(artworkUri) ?: "image/jpeg"
            val artworkBytes =
                context.contentResolver.openInputStream(artworkUri)?.use { it.readBytes() }

            if (artworkBytes == null || artworkBytes.isEmpty()) {
                Log.w(TAG, "Selected artwork URI has no readable data: $artworkUri")
                return
            }

            val artwork = ArtworkFactory.getNew().apply {
                setMimeType(mimeType)
                setBinaryData(artworkBytes)
                setPictureType(3) // 3 = front cover
            }
            tag.setField(artwork)
            Log.d(TAG, "Applied artwork to audio tag ($mimeType, ${artworkBytes.size} bytes)")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply artwork to audio tag", e)
        }
    }

    /**
     * Finds a song in MediaStore that matches the given external file URI.
     * This helps identify if an external file is actually in the user's library,
     * preventing duplicate song entries and ensuring consistent playback.
     *
     * @param context The application context
     * @param uri The URI of the external file
     * @return A Song object from MediaStore if found, null otherwise
     */
    fun findSongInMediaStore(context: Context, uri: Uri): Song? {
        return try {
            // Get the file path from the URI
            val filePath = when (uri.scheme) {
                "content" -> {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIndex =
                                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                cursor.getString(dataIndex)
                            } else null
                        }
                }

                "file" -> uri.path
                else -> null
            } ?: return null

            Log.d(TAG, "Searching MediaStore for file: $filePath")

            // Search MediaStore for this file path
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
                MediaStore.Audio.Media.YEAR
            )

            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(filePath)

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val albumIdIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val durationIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                        val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                        val id = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val albumArtUri = ContentUris.withAppendedId(
                            android.net.Uri.parse("content://media/external/audio/albumart"),
                            cursor.getLong(albumIdIndex)
                        )

                        // Keep full artist string so songs appear under all their artists
                        val artist = cursor.getString(artistIndex) ?: "Unknown Artist"

                        Log.d(TAG, "Found matching song in MediaStore with ID: $id")

                        return Song(
                            id = id.toString(),
                            title = cursor.getString(titleIndex),
                            artist = artist,
                            album = cursor.getString(albumIndex),
                            albumId = cursor.getLong(albumIdIndex).toString(),
                            duration = cursor.getLong(durationIndex),
                            uri = contentUri,
                            artworkUri = albumArtUri,
                            trackNumber = cursor.getInt(trackIndex),
                            year = cursor.getInt(yearIndex)
                        )
                    }
                }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching MediaStore", e)
            null
        }
    }

    /**
     * Extracts metadata from an external audio file
     * @param context The application context
     * @param uri The URI of the audio file
     * @return A Song object with metadata extracted from the file
     */
    fun extractMetadataFromUri(context: Context, uri: Uri): Song {
        Log.d(TAG, "Extracting metadata from URI: $uri")
        val contentResolver = context.contentResolver
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long = 0
        var artworkUri: Uri? = null
        var year: Int = 0
        var genre: String? = null

        try {
            // First verify we can access the URI
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    // Read a small amount to ensure the file is accessible
                    val buffer = ByteArray(1024)
                    stream.read(buffer)
                    Log.d(TAG, "URI is accessible and readable")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot access URI: $uri", e)
                throw IllegalArgumentException("Cannot access file at $uri", e)
            }

            // Try to get basic info from content resolver (with error handling)
            try {
                contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // Try to get file metadata from content resolver
                        val displayNameIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)

                        if (displayNameIndex != -1) {
                            val displayName = cursor.getString(displayNameIndex)
                            if (!displayName.isNullOrBlank()) {
                                // Remove extension from display name to use as fallback title
                                title = displayName.substringBeforeLast(".")
                                Log.d(TAG, "Got display name: $displayName")
                            }
                        }

                        if (titleIndex != -1) {
                            val cursorTitle = cursor.getString(titleIndex)
                            if (!cursorTitle.isNullOrBlank()) {
                                title = cursorTitle
                                Log.d(TAG, "Got cursor title: $cursorTitle")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query content resolver for basic metadata", e)
                // Continue with MediaMetadataRetriever
            }

            // Now use MediaMetadataRetriever for more detailed metadata
            try {
                retriever.setDataSource(context, uri)

                // Extract metadata
                val extractedTitle =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val extractedArtist =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val extractedAlbum =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val extractedDuration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val extractedYear =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                val extractedGenre =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

                // Use extracted metadata if available, otherwise use fallbacks
                title = extractedTitle ?: title ?: uri.lastPathSegment?.substringBeforeLast(".")
                        ?: "Unknown"

                // Keep full artist string so songs appear under all their artists
                artist = extractedArtist ?: "Unknown Artist"

                album = extractedAlbum ?: "Unknown Album"
                duration = extractedDuration?.toLongOrNull() ?: 0L
                year = extractedYear?.toIntOrNull() ?: 0
                genre = extractedGenre

                Log.d(
                    TAG,
                    "Metadata extraction successful: Title=$title, Artist=$artist, Duration=$duration, Genre=$genre"
                )
            } catch (e: Exception) {
                Log.w(TAG, "MediaMetadataRetriever failed, using fallback values", e)
                // Use basic fallbacks
                title = title ?: uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown File"
                artist = "Unknown Artist"
                album = "Unknown Album"
                duration = 0L
                year = 0
                genre = null
            }

            // Extract and cache embedded album art with bounded disk usage.
            val embeddedArt = retriever.embeddedPicture
            if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                artworkUri = cacheEmbeddedArtworkBytes(
                    songUri = uri,
                    cacheDir = context.cacheDir,
                    embeddedArt = embeddedArt,
                    lossless = false
                )
            }

            // If we couldn't extract artwork, try to generate a placeholder
            if (artworkUri == null) {
                artworkUri = ImageUtils.generatePlaceholderImage(
                    name = title,
                    size = 500,
                    cacheDir = context.cacheDir
                )
            }

            Log.d(
                TAG,
                "Extracted metadata - Title: $title, Artist: $artist, Album: $album, Duration: $duration"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }

        // Create and return a Song object with the extracted metadata
        return Song(
            id = uri.toString(),
            title = title ?: "Unknown",
            artist = artist ?: "Unknown Artist",
            album = album ?: "Unknown Album",
            duration = duration,
            uri = uri,
            artworkUri = artworkUri,
            trackNumber = 0,
            year = year,
            genre = genre
        )
    }

    /**
     * Gets the mime type of a file from its URI
     * @param context The application context
     * @param uri The URI of the file
     * @return The mime type of the file, or null if it couldn't be determined
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> context.contentResolver.getType(uri)
            ContentResolver.SCHEME_FILE -> {
                val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
            }

            else -> null
        }
    }

    fun getMediaMimeType(context: Context, uri: Uri): String? {
        val type = getMimeType(context, uri)
        if (type != null && type != "application/octet-stream") {
            return type
        }
        val path = uri.path ?: uri.toString()
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "opus", "opa" -> "audio/ogg"
            "ogg", "oga" -> "audio/ogg"
            "mkv", "mka" -> "audio/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a", "m4b" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac", "adts" -> "audio/aac"
            "ac3" -> "audio/ac3"
            "ac4" -> "audio/ac4"
            "mid", "midi" -> "audio/midi"
            "ape" -> "audio/x-ape"
            "wv" -> "audio/x-wavpack"
            "tta" -> "audio/x-tta"
            "tak" -> "audio/x-tak"
            "aiff", "aif" -> "audio/aiff"
            "dsf", "dff", "dsd" -> "audio/dsd"
            else -> type
        }
    }


    /**
     * Gets extended information about a song including file size, bitrate, sample rate, etc.
     * @param context The application context
     * @param song The song to get extended info for
     * @return ExtendedSongInfo with additional metadata
     */
    fun getExtendedSongInfo(context: Context, song: Song): ExtendedSongInfo {
        val contentResolver = context.contentResolver
        val retriever = MediaMetadataRetriever()

        var fileSize = 0L
        var bitrate = "Unknown"
        var sampleRate = "Unknown"
        var format = "Unknown"
        var dateAdded = song.dateAdded
        var dateModified = 0L
        var filePath = ""
        var composer = ""
        var discNumber = 0
        var totalTracks = 0
        var albumArtist = ""
        var year = 0
        var mimeType = ""
        var channels = "Unknown"
        var hasLyrics = false
        var isBookmark = -1L
        var genre = song.genre // Initialize with existing song genre

        // Use song's built-in audio metadata if available
        if (song.bitrate != null) {
            bitrate = "${song.bitrate / 1000} kbps"
        }
        if (song.sampleRate != null) {
            sampleRate = "${song.sampleRate / 1000} kHz"
        }
        if (song.channels != null) {
            channels = when (song.channels) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1 Surround"
                8 -> "7.1 Surround"
                else -> "${song.channels} channels"
            }
        }
        if (song.codec != null) {
            format = song.codec
        }

        try {
            // Query ContentResolver for file information
            val projection = mutableListOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media._ID // Need song ID for genre lookup
            ).apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                    add(MediaStore.Audio.Media.ALBUM_ARTIST)
                }
            }.toTypedArray()

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(song.id),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateAddedIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val composerIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
                    val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val albumArtistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val mimeTypeIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val songIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

                    filePath = cursor.getString(dataIndex) ?: ""
                    fileSize = cursor.getLong(sizeIndex)
                    val mediaStoreDateAdded = cursor.getLong(dateAddedIndex) * 1000 // Convert to milliseconds
                    dateAdded = when {
                        dateAdded > 0L && mediaStoreDateAdded > 0L -> minOf(dateAdded, mediaStoreDateAdded)
                        mediaStoreDateAdded > 0L -> mediaStoreDateAdded
                        else -> dateAdded
                    }
                    dateModified =
                        cursor.getLong(dateModifiedIndex) * 1000 // Convert to milliseconds
                    composer = cursor.getString(composerIndex) ?: ""
                    albumArtist = if (albumArtistIndex != -1) cursor.getString(albumArtistIndex) ?: "" else ""
                    year = cursor.getInt(yearIndex)
                    mimeType = cursor.getString(mimeTypeIndex) ?: ""
                    val songId = cursor.getLong(songIdIndex)

                    // Use enhanced genre detection with multiple fallbacks
                    if (genre.isNullOrEmpty()) {
                        genre = getGenreForSong(context, song.uri, songId.toInt())
                    }

                    // isBookmark is not available in all Android versions, so we'll skip it

                    // Extract track and disc numbers from TRACK field
                    val trackInfo = cursor.getInt(trackIndex)
                    if (trackInfo > 0) {
                        discNumber = trackInfo / 1000
                        totalTracks = trackInfo % 1000
                    }
                }
            }

            // Use MediaMetadataRetriever for additional audio metadata
            if (filePath.isNotEmpty()) {
                retriever.setDataSource(filePath)

                // Get bitrate
                val bitrateStr =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                if (bitrateStr != null) {
                    val bitrateValue = bitrateStr.toIntOrNull()
                    if (bitrateValue != null) {
                        bitrate = "${bitrateValue / 1000} kbps"
                    }
                }

                // Get sample rate - METADATA_KEY_SAMPLERATE is available since API 10, works on Android 8+
                val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)

                if (sampleRateStr != null) {
                    val sampleRateValue = sampleRateStr.toIntOrNull()
                    if (sampleRateValue != null) {
                        sampleRate = "${sampleRateValue} Hz"
                    }
                }

                // Get number of audio channels using MediaExtractor (correct method)
                // Only extract if song doesn't already have this info
                if (channels == "Unknown") {
                    var extractor: android.media.MediaExtractor? = null
                    try {
                        extractor = android.media.MediaExtractor()
                        extractor.setDataSource(filePath)

                        // Find the audio track
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(android.media.MediaFormat.KEY_MIME)

                            if (mime?.startsWith("audio/") == true) {
                                // Get channel count from MediaFormat
                                if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                                    val channelCount =
                                        format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                                    channels = when (channelCount) {
                                        1 -> "Mono"
                                        2 -> "Stereo"
                                        6 -> "5.1 Surround"
                                        8 -> "7.1 Surround"
                                        else -> "$channelCount channels"
                                    }
                                }
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(
                            TAG,
                            "Error extracting channel count with MediaExtractor: ${e.message}"
                        )
                        channels = "Stereo" // Default fallback
                    } finally {
                        try {
                            extractor?.release()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error releasing MediaExtractor: ${e.message}")
                        }
                    }
                }

                // Check for lyrics availability (METADATA_KEY_LYRICS not available in all versions)
                // We'll skip lyrics detection for now to maintain compatibility
                hasLyrics = false

                // Fill in missing composer if available from MediaMetadataRetriever
                if (composer.isEmpty()) {
                    val composerStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    if (!composerStr.isNullOrEmpty()) {
                        composer = composerStr
                    }
                }

                // Fill in missing album artist if available
                if (albumArtist.isEmpty()) {
                    val albumArtistStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    if (!albumArtistStr.isNullOrEmpty()) {
                        albumArtist = albumArtistStr
                    }
                }

                // Fill in missing year if available
                if (year == 0) {
                    val yearStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    if (!yearStr.isNullOrEmpty()) {
                        year = yearStr.toIntOrNull() ?: 0
                    }
                }

                // Fill in missing genre if available from MediaMetadataRetriever
                if (genre.isNullOrEmpty()) {
                    val genreStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    if (!genreStr.isNullOrEmpty()) {
                        genre = genreStr
                    }
                }

                // Determine format from file extension and MIME type
                val file = File(filePath)
                val extension = file.extension.lowercase()

                // Try to detect advanced audio format using AudioFormatDetector
                val audioFormatInfo = try {
                    AudioFormatDetector.detectFormat(context, song.uri)
                } catch (e: Exception) {
                    Log.w(TAG, "AudioFormatDetector failed, using fallback detection", e)
                    null
                }

                // Use detected codec or fall back to extension-based detection
                format = when {
                    audioFormatInfo != null && audioFormatInfo.codec != "Unknown" -> audioFormatInfo.codec
                    mimeType.contains("mp3", ignoreCase = true) || extension == "mp3" -> "MP3"
                    mimeType.contains("flac", ignoreCase = true) || extension == "flac" -> "FLAC"
                    mimeType.contains("ogg", ignoreCase = true) || extension == "ogg" || extension == "oga" -> "OGG"
                    mimeType.contains("alac", ignoreCase = true) || extension == "alac" -> "ALAC"
                    mimeType.contains("opus", ignoreCase = true) || extension == "opus" || extension == "opa" -> "Opus"
                    mimeType.contains("ac3", ignoreCase = true) || mimeType.contains("ac-3", ignoreCase = true) || extension == "ac3" -> "AC-3"
                    mimeType.contains("ac4", ignoreCase = true) || extension == "ac4" -> "AC-4"
                    mimeType.contains("truehd", ignoreCase = true) -> "TrueHD"
                    mimeType.contains("atmos", ignoreCase = true) -> "Dolby Atmos"
                    mimeType.contains("dts-hd", ignoreCase = true) || mimeType.contains("dtshd", ignoreCase = true) -> "DTS-HD MA"
                    mimeType.contains("dts", ignoreCase = true) -> "DTS"
                    mimeType.contains("dsd", ignoreCase = true) -> "DSD"
                    mimeType.contains("midi", ignoreCase = true) || extension == "mid" || extension == "midi" -> "MIDI"
                    extension == "m4a" -> {
                        // M4A can be AAC or ALAC - check with AudioFormatDetector
                        if (AudioFormatDetector.isALAC(context, song.uri)) "ALAC" else "AAC"
                    }

                    mimeType.contains("aac", ignoreCase = true) || extension == "aac" -> "AAC"
                    mimeType.contains("wav", ignoreCase = true) || extension == "wav" -> "WAV"
                    mimeType.contains("wma", ignoreCase = true) || extension == "wma" -> "WMA"
                    extension.isNotEmpty() -> extension.uppercase()
                    mimeType.isNotEmpty() -> mimeType.substringAfter("/").uppercase()
                    else -> "Unknown"
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting extended song info", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }

        // Detect audio format information for lossless, Dolby, etc.
        // Pass the song object to help with bit depth calculation
        val audioFormatInfo = try {
            AudioFormatDetector.detectFormat(context, song.uri, song)
        } catch (e: Exception) {
            Log.w(TAG, "AudioFormatDetector failed in getExtendedSongInfo", e)
            null
        }

        // Calculate detailed audio quality using AudioQualityDetector
        // Prefer Song's metadata when available as it's more reliable
        val songBitrate = song.bitrate ?: 0
        val songSampleRate = song.sampleRate ?: 0
        val songChannels = song.channels ?: 0

        val bitrateKbps = if (songBitrate > 0) {
            songBitrate / 1000
        } else {
            audioFormatInfo?.bitrateKbps ?: 0
        }

        val sampleRateValue = if (songSampleRate > 0) {
            songSampleRate
        } else {
            audioFormatInfo?.sampleRateHz ?: 0
        }

        val channelCountValue = if (songChannels > 0) {
            songChannels
        } else {
            audioFormatInfo?.channelCount ?: 2
        }

        val codecValue = audioFormatInfo?.codec ?: song.codec ?: format
        val bitDepthValue = audioFormatInfo?.bitDepth ?: 0

        val audioQuality = AudioQualityDetector.detectQuality(
            codec = codecValue,
            sampleRateHz = sampleRateValue,
            bitrateKbps = bitrateKbps,
            bitDepth = bitDepthValue,
            channelCount = channelCountValue
        )

        return ExtendedSongInfo(
            fileSize = fileSize,
            bitrate = bitrate,
            sampleRate = sampleRate,
            format = format,
            dateAdded = dateAdded,
            dateModified = dateModified,
            filePath = filePath,
            composer = composer,
            discNumber = discNumber,
            albumArtist = albumArtist,
            year = year,
            mimeType = mimeType,
            channels = channels,
            hasLyrics = hasLyrics,
            genre = genre ?: "", // Pass the extracted genre
            // Audio quality indicators (legacy)
            isLossless = audioQuality.isLossless,
            isDolby = audioQuality.isDolby,
            isDTS = audioQuality.isDTS,
            isHiRes = audioQuality.isHiRes,
            audioCodec = audioFormatInfo?.codec ?: format,
            formatName = audioFormatInfo?.formatName ?: format,
            // Enhanced quality information
            qualityType = audioQuality.qualityType.name,
            qualityLabel = audioQuality.qualityLabel,
            qualityDescription = audioQuality.qualityDescription,
            bitDepth = audioQuality.bitDepthEstimate,
            qualityCategory = audioQuality.category
        )
    }

    /**
     * Embeds lyrics into the audio file's metadata tags using jaudiotagger.
     * Supports synced (LRC) and unsynced (plain) lyrics via FieldKey.LYRICS.
     * @param context The application context
     * @param song The song to embed lyrics into
     * @param lyrics The lyrics text (plain or LRC formatted) to embed
     * @return true if lyrics were successfully embedded, false otherwise
     */
    fun embedLyricsInFile(
        context: Context,
        song: Song,
        lyrics: String
    ): Boolean {
        val targetUri = getSpecificVolumeUri(context, song.id.toLongOrNull() ?: 0L) ?: song.uri
        val song = song.copy(uri = targetUri)
        return try {
            val contentResolver = context.contentResolver

            if (!song.uri.toString().startsWith("content://media/")) {
                Log.e(TAG, "Invalid URI scheme for lyrics embedding: ${song.uri}")
                return false
            }

            // Get file path from URI
            val filePath = when (song.uri.scheme) {
                "content" -> {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    contentResolver.query(song.uri, projection, null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                cursor.getString(dataIndex)
                            } else null
                        }
                }
                "file" -> song.uri.path
                else -> null
            }

            var fileWriteSucceeded = false

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11+: Direct write will fail for external media files.
                // Return false so ViewModel triggers createWriteRequest for proper permission UI.
                return false
            } else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                // Android 10: WRITE_EXTERNAL_STORAGE is granted; write via temp file + ContentResolver stream
                try {
                    val extension = filePath?.substringAfterLast('.', "mp3") ?: "mp3"
                    var tempFile = File(
                        context.cacheDir,
                        "temp_lyrics_${System.currentTimeMillis()}.$extension"
                    )
                    try {
                        // Copy original file to temp
                        contentResolver.openInputStream(song.uri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (!tempFile.exists() || tempFile.length() == 0L) {
                            throw Exception("Failed to copy file to temp for lyrics embedding")
                        }
                        tempFile = adjustTempFileExtension(tempFile)
                        // Embed lyrics in temp file with format-aware routing
                        if (!embedLyricsInAudioFile(tempFile, lyrics)) {
                            throw Exception("Failed to embed lyrics in temp file")
                        }
                        // Write temp back via ContentResolver (WRITE_EXTERNAL_STORAGE covers API 29)
                        val outputStream = contentResolver.openOutputStream(song.uri, "wt")
                        if (outputStream != null) {
                            outputStream.use { out ->
                                tempFile.inputStream().use { input -> input.copyTo(out) }
                            }
                            fileWriteSucceeded = true
                        } else {
                            // Fallback: write directly via file path
                            if (filePath != null) {
                                val audioFile = File(filePath)
                                if (audioFile.exists() && audioFile.canWrite()) {
                                    fileWriteSucceeded = embedLyricsInAudioFile(audioFile, lyrics)
                                }
                            }
                        }
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                } catch (e: RecoverableSecurityExceptionWrapper) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to embed lyrics on Android 10", e)
                    // Last resort: direct file path with WRITE_EXTERNAL_STORAGE
                    if (filePath != null) {
                        try {
                            val audioFile = File(filePath)
                            if (audioFile.exists() && audioFile.canWrite()) {
                                fileWriteSucceeded = embedLyricsInAudioFile(audioFile, lyrics)
                            }
                        } catch (e2: Exception) {
                            Log.e(TAG, "Direct path write also failed", e2)
                        }
                    }
                }
            } else {
                // Android 9 and below: direct file path write
                if (filePath != null) {
                    val audioFile = File(filePath)
                    if (audioFile.exists() && audioFile.canWrite()) {
                        try {
                            fileWriteSucceeded = embedLyricsInAudioFile(audioFile, lyrics)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to embed lyrics using jaudiotagger", e)
                        }
                    } else {
                        Log.e(TAG, "File not writable or not found: $filePath")
                    }
                } else {
                    Log.e(TAG, "Could not get file path for lyrics embedding")
                }
            }

            if (fileWriteSucceeded) {
                try {
                    if (filePath != null) {
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error triggering media scanner after lyrics embedding", e)
                }
            }

            fileWriteSucceeded
        } catch (e: RecoverableSecurityExceptionWrapper) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error embedding lyrics into file", e)
            false
        }
    }

    /**
     * Returns true if jaudiotagger can read and write the given file extension.
     * OGG Opus, WebM, MKA, and other non-Vorbis OGG variants are not supported.
     */
    fun isSupportedByJaudiotagger(extension: String): Boolean {
        return when (extension.lowercase()) {
            "mp3", "flac", "ogg", "wav", "wave", "aif", "aiff",
            "mp4", "m4a", "m4p", "m4b", "wma", "dsf", "dff",
            "opus", "opa" -> true
            else -> false
        }
    }

    /**
     * Creates a write request for Android 11+ to get permission to embed lyrics in a file
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    fun createWriteRequestForLyrics(
        context: Context,
        song: Song,
        lyrics: String
    ): PendingLyricsWriteRequest? {
        val targetUri = getSpecificVolumeUri(context, song.id.toLongOrNull() ?: 0L) ?: song.uri
        val song = song.copy(uri = targetUri)
        return try {
            val contentResolver = context.contentResolver
            Log.d(TAG, "Creating lyrics write request for song: ${song.title}")

            val urisToModify = listOf(song.uri)
            val pendingIntent = MediaStore.createWriteRequest(contentResolver, urisToModify)

            // Attempt to pre-prepare the temp file, but do NOT let failure block the
            // permission dialog. completeLyricsWriteAfterPermission will retry if absent.
            val tempFilePath = try {
                prepareTempFileWithLyrics(context, song, lyrics)
            } catch (e: Exception) {
                Log.w(TAG, "Could not pre-prepare lyrics temp file; will retry after permission: ${e.message}")
                null
            }

            PendingLyricsWriteRequest(
                intentSender = pendingIntent.intentSender,
                song = song,
                lyrics = lyrics,
                tempFilePath = tempFilePath ?: "" // empty string = retry in completion
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create lyrics write request for song: ${song.title}", e)
            null
        }
    }

    /**
     * Prepares a temp file with lyrics embedded for use after permission is granted
     */
    private fun prepareTempFileWithLyrics(
        context: Context,
        song: Song,
        lyrics: String
    ): String? {
        return try {
            val contentResolver = context.contentResolver
            val filePath = contentResolver.query(
                song.uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

            val extension = filePath?.substringAfterLast('.', "mp3") ?: "mp3"
            var tempFile = File(context.cacheDir, "temp_lyrics_perm_${System.currentTimeMillis()}.$extension")

            // Copy original file
            contentResolver.openInputStream(song.uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "Failed to copy file to temp for lyrics permission flow")
                return null
            }

            tempFile = adjustTempFileExtension(tempFile)

            // Write lyrics to temp with format-aware routing
            if (!embedLyricsInAudioFile(tempFile, lyrics)) {
                Log.e(TAG, "Failed to embed lyrics in temp file during prep")
                return null
            }

            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing temp file with lyrics", e)
            null
        }
    }

    /**
     * Completes lyrics embedding after user grants permission via createWriteRequest
     */
    fun completeLyricsWriteAfterPermission(
        context: Context,
        pendingRequest: PendingLyricsWriteRequest
    ): Boolean {
        val targetUri = getSpecificVolumeUri(context, pendingRequest.song.id.toLongOrNull() ?: 0L) ?: pendingRequest.song.uri
        val pendingRequest = pendingRequest.copy(song = pendingRequest.song.copy(uri = targetUri))
        return try {
            val contentResolver = context.contentResolver

            // Resolve temp file — may be empty if prep failed before permission was granted.
            // Now that we have write access, retry preparation from the original URI.
            var tempFile = if (pendingRequest.tempFilePath.isNotEmpty()) File(pendingRequest.tempFilePath) else null
            if (tempFile == null || !tempFile.exists()) {
                Log.d(TAG, "Temp lyrics file absent; re-preparing after permission grant")
                val retryPath = prepareTempFileWithLyrics(context, pendingRequest.song, pendingRequest.lyrics)
                if (retryPath == null) {
                    Log.e(TAG, "Cannot embed lyrics: format not supported or file unreadable")
                    return false
                }
                tempFile = File(retryPath)
            }

            val outputStream = contentResolver.openOutputStream(pendingRequest.song.uri, "w")
            if (outputStream == null) {
                Log.e(TAG, "Cannot open output stream after permission granted for lyrics")
                return false
            }

            outputStream.use { outStream ->
                tempFile.inputStream().use { input -> input.copyTo(outStream) }
            }

            // Trigger media scanner
            val filePath = contentResolver.query(
                pendingRequest.song.uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (filePath != null) {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            }

            // Cleanup temp
            tempFile.delete()

            Log.d(TAG, "Successfully completed lyrics embedding after permission")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error completing lyrics write after permission", e)
            false
        }
    }

    /**
     * Cleans up a pending lyrics write request
     */
    fun cleanupPendingLyricsWriteRequest(pendingRequest: PendingLyricsWriteRequest) {
        try {
            val tempFile = File(pendingRequest.tempFilePath)
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(TAG, "Cleaned up pending lyrics write temp file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up pending lyrics write request", e)
        }
    }

    /**
     * Updates song metadata using ContentResolver
     * @param context The application context
     * @param song The song to update
     * @param newTitle The new title
     * @param newArtist The new artist
     * @param newAlbum The new album
     * @param newGenre The new genre
     * @param newTrackNumber The new track number
     * @return true if the update was successful, false otherwise
     */
    fun updateSongMetadata(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: Int = 0,
        newTrackNumber: Int,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        newAlbumArtist: String? = null,
        newComposer: String? = null,
        newDiscNumber: Int = 1
    ): Boolean {
        val targetUri = getSpecificVolumeUri(context, song.id.toLongOrNull() ?: 0L) ?: song.uri
        val song = song.copy(uri = targetUri)
        return try {
            val contentResolver = context.contentResolver

            // Log initial state for debugging
            Log.d(TAG, "Attempting to update metadata for song: ${song.title}")
            Log.d(TAG, "Song URI: ${song.uri}")
            Log.d(TAG, "Song ID: ${song.id}")
            Log.d(
                TAG,
                "New values - Title: $newTitle, Artist: $newArtist, Album: $newAlbum, Genre: $newGenre, Year: $newYear, Track: $newTrackNumber, ReplaceArtwork=${artworkUri != null}, RemoveArtwork=$removeArtwork, AlbumArtist=$newAlbumArtist"
            )

            // Check if URI is valid and accessible
            if (!song.uri.toString().startsWith("content://media/")) {
                Log.e(TAG, "Invalid URI scheme for metadata update: ${song.uri}")
                return false
            }

            // Get file path from URI for metadata writing
            val filePath = when (song.uri.scheme) {
                "content" -> {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    contentResolver.query(song.uri, projection, null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIndex =
                                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                cursor.getString(dataIndex)
                            } else null
                        }
                }

                "file" -> song.uri.path
                else -> null
            }

            // IMPORTANT: Update MediaStore FIRST before writing to file
            // This ensures that when media scanner runs, it reads the updated metadata
            // instead of overwriting our changes with cached old data

            // Check permissions first
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                val hasWritePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasWritePermission) {
                    Log.e(TAG, "Missing WRITE_EXTERNAL_STORAGE permission")
                    return false
                }
            }

            // Prepare MediaStore values
            val values = android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newTitle)
                put(MediaStore.Audio.Media.ARTIST, newArtist)
                put(MediaStore.Audio.Media.ALBUM, newAlbum)
                if (newGenre.isNotBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.GENRE, newGenre)
                }
                if (newYear > 0) {
                    put(MediaStore.Audio.Media.YEAR, newYear)
                }
                if (newTrackNumber > 0) {
                    put(MediaStore.Audio.Media.TRACK, newTrackNumber)
                }
                if (newAlbumArtist != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, newAlbumArtist)
                }
                if (newComposer != null) {
                    put(MediaStore.Audio.Media.COMPOSER, newComposer)
                }
                if (newDiscNumber > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.CD_TRACK_NUMBER, newDiscNumber)
                }
            }

            // Update MediaStore first
            val mediaStoreRowsUpdated =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val directUpdate = contentResolver.update(
                            song.uri,
                            values,
                            null,
                            null
                        )

                        if (directUpdate > 0) {
                            Log.d(
                                TAG,
                                "MediaStore direct update successful for song: ${song.title}"
                            )
                            directUpdate
                        } else {
                            Log.w(
                                TAG,
                                "MediaStore direct update failed, trying alternative approach"
                            )
                            updateViaMediaStore(contentResolver, song, values)
                        }
                    } catch (e: SecurityException) {
                        Log.w(
                            TAG,
                            "Security exception during MediaStore update, trying alternative",
                            e
                        )
                        updateViaMediaStore(contentResolver, song, values)
                    }
                } else {
                    contentResolver.update(
                        song.uri,
                        values,
                        null,
                        null
                    )
                }

            val mediaStoreSuccess = mediaStoreRowsUpdated > 0
            Log.d(TAG, "MediaStore updated $mediaStoreRowsUpdated rows for song: ${song.title}")

            // Now write metadata to the actual file
            var fileWriteSucceeded = false

            // For Android 10+ (API 29+), we need to use a temporary file approach
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    Log.d(TAG, "Using temporary file approach for Android 10+ file write")
                    Log.d(TAG, "Song URI: ${song.uri}")
                    Log.d(TAG, "Song path: $filePath")

                    // Get the file extension to preserve format
                    val fileName = song.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                    val extension = filePath?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                        ?: song.uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                        ?: "mp3"
                    var tempFile = File(
                        context.cacheDir,
                        "temp_audio_${java.util.UUID.randomUUID()}.$extension"
                    )

                    try {
                        // Step 1: Copy original file to temp location
                        Log.d(TAG, "Step 1: Copying original file to temp...")
                        var bytesRead = 0L
                        contentResolver.openInputStream(song.uri)?.use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                bytesRead = inputStream.copyTo(outputStream)
                            }
                        }
                        Log.d(
                            TAG,
                            "Copied $bytesRead bytes to temp location: ${tempFile.absolutePath}"
                        )

                        if (!tempFile.exists() || tempFile.length() == 0L) {
                            throw Exception("Failed to copy file to temp location")
                        }

                        tempFile = adjustTempFileExtension(tempFile)

                        // Step 2: Modify metadata in temp file
                        Log.d(TAG, "Step 2: Modifying metadata in temp file...")
                        val container = detectContainerFormat(tempFile.absolutePath)
                        val useTagLib = container != DetectedContainer.UNKNOWN
                        
                        if (useTagLib) {
                            Log.d(TAG, "Using TagLib for metadata write (detected: $container)")
                            val tagLibSuccess = updateFileMetadataWithTagLib(
                                context = context,
                                file = tempFile,
                                newTitle = newTitle,
                                newArtist = newArtist,
                                newAlbum = newAlbum,
                                newGenre = newGenre,
                                newYear = newYear,
                                newTrackNumber = newTrackNumber,
                                newAlbumArtist = newAlbumArtist,
                                newComposer = newComposer,
                                newDiscNumber = newDiscNumber,
                                artworkUri = artworkUri,
                                removeArtwork = removeArtwork
                            )
                            if (!tagLibSuccess) {
                                throw Exception("TagLib failed to write metadata")
                            }
                        } else {
                            val audioFileObj = AudioFileIO.read(tempFile)
                            val tag: Tag = audioFileObj.tag ?: audioFileObj.createDefaultTag()

                            tag.setField(FieldKey.TITLE, newTitle)
                            tag.setField(FieldKey.ARTIST, newArtist)
                            tag.setField(FieldKey.ALBUM, newAlbum)
                            if (newGenre.isNotBlank()) {
                                tag.setField(FieldKey.GENRE, newGenre)
                            } else {
                                tag.deleteField(FieldKey.GENRE)
                            }
                            if (newYear > 0) {
                                tag.setField(FieldKey.YEAR, newYear.toString())
                            }
                            if (newTrackNumber > 0) {
                                tag.setField(FieldKey.TRACK, newTrackNumber.toString())
                            }
                            if (newAlbumArtist != null) {
                                tag.setField(FieldKey.ALBUM_ARTIST, newAlbumArtist)
                            }
                            if (newComposer != null) {
                                if (newComposer.isNotBlank()) {
                                    tag.setField(FieldKey.COMPOSER, newComposer)
                                } else {
                                    tag.deleteField(FieldKey.COMPOSER)
                                }
                            }
                            if (newDiscNumber > 0) {
                                tag.setField(FieldKey.DISC_NO, newDiscNumber.toString())
                            }
                            applyArtworkToTag(context, tag, artworkUri, removeArtwork)

                            audioFileObj.tag = tag
                            AudioFileIO.write(audioFileObj)
                        }
                        Log.d(TAG, "Metadata written to temp file successfully")

                        // Step 3: Copy modified temp file back to original location
                        Log.d(TAG, "Step 3: Copying modified file back to original location...")

                        // Try to open output stream - this is where it might fail on Android 10+
                        val outputStream = try {
                            contentResolver.openOutputStream(song.uri, "w")
                        } catch (e: android.app.RecoverableSecurityException) {
                            // Android 11+ requires user permission via createWriteRequest
                            Log.e(
                                TAG,
                                "RecoverableSecurityException - Need to request user permission for this file"
                            )
                            Log.e(
                                TAG,
                                "This file was not created by this app. User permission is required to modify it."
                            )
                            Log.e(
                                TAG,
                                "Solution: App needs to use MediaStore.createWriteRequest() and show permission dialog"
                            )

                            // Store the temp file path so it can be used after permission is granted
                            // For now, we'll throw with the pending intent info
                            throw RecoverableSecurityExceptionWrapper(
                                "User permission required to modify this file",
                                e.userAction,
                                song.uri,
                                tempFile.absolutePath
                            )
                        } catch (e: SecurityException) {
                            Log.e(
                                TAG,
                                "SecurityException opening output stream - app may not have write permission for this file",
                                e
                            )
                            null
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Exception opening output stream: ${e.javaClass.simpleName} - ${e.message}",
                                e
                            )
                            null
                        }

                        if (outputStream == null) {
                            Log.e(
                                TAG,
                                "Failed to open output stream for writing. This typically means:"
                            )
                            Log.e(
                                TAG,
                                "1. File is on external SD card (requires special permissions)"
                            )
                            Log.e(TAG, "2. File is in a protected directory")
                            Log.e(
                                TAG,
                                "3. App doesn't own this file (Android 11+ scoped storage restriction)"
                            )
                            throw Exception("Cannot open output stream for URI: ${song.uri}")
                        }

                        outputStream.use { outStream ->
                            tempFile.inputStream().use { inputStream ->
                                val bytesCopied = inputStream.copyTo(outStream)
                                Log.d(TAG, "Copied $bytesCopied bytes back to original location")
                            }
                        }

                        fileWriteSucceeded = true
                        Log.d(
                            TAG,
                            "Successfully wrote metadata using temp file approach for Android 10+"
                        )

                    } catch (inner: RecoverableSecurityExceptionWrapper) {
                        // This is a special case where we need user permission
                        Log.e(TAG, "RecoverableSecurityException caught - user permission required")
                        // Re-throw to be handled by the ViewModel/UI layer
                        throw inner
                    } catch (inner: Exception) {
                        Log.e(TAG, "Error during temp file operations", inner)
                        throw inner
                    } finally {
                        // Clean up temp file
                        if (tempFile.exists()) {
                            val deleted = tempFile.delete()
                            Log.d(TAG, "Temp file cleanup: ${if (deleted) "success" else "failed"}")
                        }
                    }
                } catch (e: RecoverableSecurityExceptionWrapper) {
                    // User permission required - propagate up to UI layer
                    Log.w(TAG, "User permission required for file modification: ${e.message}")
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Failed to write metadata using temp file approach on Android 10+",
                        e
                    )
                    Log.e(TAG, "Error details: ${e.message}")
                    e.printStackTrace()
                    // Continue with MediaStore update even if file writing fails
                }
            } else {
                // For Android 9 and below, use traditional file access
                if (filePath == null) {
                    Log.e(TAG, "Could not get file path for metadata update")
                } else {
                    val audioFile = File(filePath)
                    if (!audioFile.exists()) {
                        Log.e(TAG, "File does not exist: $filePath")
                    } else if (!audioFile.canWrite()) {
                        Log.e(TAG, "File is not writable: $filePath")
                    } else {
                        try {
                            val container = detectContainerFormat(audioFile.absolutePath)
                            val useTagLib = container != DetectedContainer.UNKNOWN
                            
                            if (useTagLib) {
                                Log.d(TAG, "Using TagLib for metadata write (Android 9-, detected: $container)")
                                val tagLibSuccess = updateFileMetadataWithTagLib(
                                    context = context,
                                    file = audioFile,
                                    newTitle = newTitle,
                                    newArtist = newArtist,
                                    newAlbum = newAlbum,
                                    newGenre = newGenre,
                                    newYear = newYear,
                                    newTrackNumber = newTrackNumber,
                                    newAlbumArtist = newAlbumArtist,
                                    newComposer = newComposer,
                                    newDiscNumber = newDiscNumber,
                                    artworkUri = artworkUri,
                                    removeArtwork = removeArtwork
                                )
                                if (!tagLibSuccess) {
                                    throw Exception("TagLib failed to write metadata")
                                }
                                fileWriteSucceeded = true
                            } else {
                                val audioFileObj = AudioFileIO.read(audioFile)
                                val tag: Tag = audioFileObj.tag ?: audioFileObj.createDefaultTag()

                                tag.setField(FieldKey.TITLE, newTitle)
                                tag.setField(FieldKey.ARTIST, newArtist)
                                tag.setField(FieldKey.ALBUM, newAlbum)
                                if (newGenre.isNotBlank()) {
                                    tag.setField(FieldKey.GENRE, newGenre)
                                } else {
                                    tag.deleteField(FieldKey.GENRE)
                                }
                                if (newYear > 0) {
                                    tag.setField(FieldKey.YEAR, newYear.toString())
                                }
                                if (newTrackNumber > 0) {
                                    tag.setField(FieldKey.TRACK, newTrackNumber.toString())
                                }
                                if (newAlbumArtist != null) {
                                    tag.setField(FieldKey.ALBUM_ARTIST, newAlbumArtist)
                                }
                                if (newComposer != null) {
                                    if (newComposer.isNotBlank()) {
                                        tag.setField(FieldKey.COMPOSER, newComposer)
                                    } else {
                                        tag.deleteField(FieldKey.COMPOSER)
                                    }
                                }
                                if (newDiscNumber > 0) {
                                    tag.setField(FieldKey.DISC_NO, newDiscNumber.toString())
                                }
                                applyArtworkToTag(context, tag, artworkUri, removeArtwork)

                                audioFileObj.tag = tag
                                AudioFileIO.write(audioFileObj)
                                fileWriteSucceeded = true
                            }
                            Log.d(
                                TAG,
                                "Successfully wrote metadata to file (Android 9-): $filePath"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write metadata to file", e)
                        }
                    }
                }
            }

            // Log the results of both operations
            Log.d(
                TAG,
                "Metadata update results - MediaStore: $mediaStoreSuccess, File write: $fileWriteSucceeded"
            )

            // Only trigger media scanner if BOTH operations succeeded
            if (mediaStoreSuccess && fileWriteSucceeded) {
                // Trigger media scanner to refresh the metadata
                // IMPORTANT: This ensures external files that are edited/processed
                // will be properly indexed in MediaStore, allowing the app to
                // recognize them as library songs on subsequent scans.
                try {
                    // Get file path from URI
                    val scanFilePath = when (song.uri.scheme) {
                        "content" -> {
                            val projection = arrayOf(MediaStore.Audio.Media.DATA)
                            contentResolver.query(song.uri, projection, null, null, null)
                                ?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val dataIndex =
                                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                        cursor.getString(dataIndex)
                                    } else null
                                }
                        }

                        "file" -> song.uri.path
                        else -> null
                    }

                    if (scanFilePath != null) {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(scanFilePath),
                            null,
                            null
                        )
                        Log.d(TAG, "Media scanner triggered for updated file: $scanFilePath")
                    } else {
                        Log.w(TAG, "Could not get file path for media scanner")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to trigger media scanner", e)
                }
            } else if (mediaStoreSuccess && !fileWriteSucceeded) {
                Log.i(
                    TAG,
                    "MediaStore updated but file write failed - skipping media scanner to preserve MediaStore changes"
                )
            }

            // Return true ONLY if file write succeeded (the actual persistent change)
            // MediaStore updates are temporary and will be overwritten by media scanner
            fileWriteSucceeded

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update song metadata: ${song.title}", e)
            false
        }
    }

    /**
     * Creates a write request for Android 11+ using MediaStore.createWriteRequest()
     * This shows a system permission dialog to the user to allow modifying the file
     *
     * @param context The application context
     * @param song The song to request write access for
     * @param newTitle The new title to be set after permission is granted
     * @param newArtist The new artist to be set after permission is granted
     * @param newAlbum The new album to be set after permission is granted
     * @param newGenre The new genre to be set after permission is granted
     * @param newYear The new year to be set after permission is granted
     * @param newTrackNumber The new track number to be set after permission is granted
     * @return PendingWriteRequest with the IntentSender if successful, null otherwise
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    fun createWriteRequestForSong(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: Int,
        newTrackNumber: Int,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        newAlbumArtist: String? = null,
        newComposer: String? = null,
        newDiscNumber: Int = 1
    ): PendingWriteRequest? {
        val targetUri = getSpecificVolumeUri(context, song.id.toLongOrNull() ?: 0L) ?: song.uri
        val song = song.copy(uri = targetUri)
        return try {
            val contentResolver = context.contentResolver
            Log.d(TAG, "Creating write request for song: ${song.title}")

            val urisToModify = listOf(song.uri)
            val pendingIntent = MediaStore.createWriteRequest(contentResolver, urisToModify)

            // Attempt to pre-prepare the temp file, but do NOT let failure block the
            // permission dialog. completeWriteAfterPermissionGranted will retry if absent.
            val tempFilePath = try {
                prepareTempFileWithMetadata(
                    context = context,
                    song = song,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = newGenre,
                    newYear = newYear,
                    newTrackNumber = newTrackNumber,
                    artworkUri = artworkUri,
                    removeArtwork = removeArtwork,
                    newAlbumArtist = newAlbumArtist,
                    newComposer = newComposer,
                    newDiscNumber = newDiscNumber
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not pre-prepare metadata temp file; will retry after permission: ${e.message}")
                null
            }

            PendingWriteRequest(
                intentSender = pendingIntent.intentSender,
                song = song,
                newTitle = newTitle,
                newArtist = newArtist,
                newAlbum = newAlbum,
                newGenre = newGenre,
                newYear = newYear,
                newTrackNumber = newTrackNumber,
                tempFilePath = tempFilePath ?: "", // empty string = retry in completion
                artworkUriString = artworkUri?.toString(),
                removeArtwork = removeArtwork,
                newAlbumArtist = newAlbumArtist,
                newComposer = newComposer,
                newDiscNumber = newDiscNumber
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create write request for song: ${song.title}", e)
            null
        }
    }

    /**
     * Prepares a temp file with modified metadata for use after permission is granted
     */
    private fun prepareTempFileWithMetadata(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: Int,
        newTrackNumber: Int,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        newAlbumArtist: String? = null,
        newComposer: String? = null,
        newDiscNumber: Int = 1
    ): String? {
        return try {
            val contentResolver = context.contentResolver

            // Get file extension
            val filePath = when (song.uri.scheme) {
                "content" -> {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    contentResolver.query(song.uri, projection, null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIndex =
                                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                cursor.getString(dataIndex)
                            } else null
                        }
                }

                "file" -> song.uri.path
                else -> null
            }

            val extension = filePath?.substringAfterLast('.', "mp3") ?: "mp3"
            var tempFile = File(
                context.cacheDir,
                "pending_metadata_${song.id}_${System.currentTimeMillis()}.$extension"
            )

            // Copy original file to temp location
            contentResolver.openInputStream(song.uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "Failed to copy file to temp location")
                return null
            }

            tempFile = adjustTempFileExtension(tempFile)

            // Modify metadata in temp file
            val container = detectContainerFormat(tempFile.absolutePath)
            val useTagLib = container != DetectedContainer.UNKNOWN
            
            if (useTagLib) {
                Log.d(TAG, "Using TagLib for metadata write in prepareTempFileWithMetadata (detected: $container)")
                val tagLibSuccess = updateFileMetadataWithTagLib(
                    context = context,
                    file = tempFile,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = newGenre,
                    newYear = newYear,
                    newTrackNumber = newTrackNumber,
                    newAlbumArtist = newAlbumArtist,
                    newComposer = newComposer,
                    newDiscNumber = newDiscNumber,
                    artworkUri = artworkUri,
                    removeArtwork = removeArtwork
                )
                if (!tagLibSuccess) {
                    throw Exception("TagLib failed to write metadata")
                }
            } else {
                val audioFileObj = AudioFileIO.read(tempFile)
                val tag: Tag = audioFileObj.tag ?: audioFileObj.createDefaultTag()

                tag.setField(FieldKey.TITLE, newTitle)
                tag.setField(FieldKey.ARTIST, newArtist)
                tag.setField(FieldKey.ALBUM, newAlbum)
                if (newGenre.isNotBlank()) {
                    tag.setField(FieldKey.GENRE, newGenre)
                } else {
                    tag.deleteField(FieldKey.GENRE)
                }
                if (newYear > 0) {
                    tag.setField(FieldKey.YEAR, newYear.toString())
                }
                if (newTrackNumber > 0) {
                    tag.setField(FieldKey.TRACK, newTrackNumber.toString())
                }
                if (newAlbumArtist != null) {
                    tag.setField(FieldKey.ALBUM_ARTIST, newAlbumArtist)
                }
                if (newComposer != null) {
                    if (newComposer.isNotBlank()) {
                        tag.setField(FieldKey.COMPOSER, newComposer)
                    } else {
                        tag.deleteField(FieldKey.COMPOSER)
                    }
                }
                if (newDiscNumber > 0) {
                    tag.setField(FieldKey.DISC_NO, newDiscNumber.toString())
                }
                applyArtworkToTag(context, tag, artworkUri, removeArtwork)

                audioFileObj.tag = tag
                AudioFileIO.write(audioFileObj)
            }

            Log.d(TAG, "Temp file with modified metadata created: ${tempFile.absolutePath}")
            tempFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare temp file with metadata", e)
            null
        }
    }

    /**
     * Completes the metadata write operation after user grants permission via createWriteRequest
     *
     * @param context The application context
     * @param pendingRequest The pending write request containing all necessary info
     * @return true if the write was successful, false otherwise
     */
    fun completeWriteAfterPermissionGranted(
        context: Context,
        pendingRequest: PendingWriteRequest
    ): Boolean {
        val targetUri = getSpecificVolumeUri(context, pendingRequest.song.id.toLongOrNull() ?: 0L) ?: pendingRequest.song.uri
        val pendingRequest = pendingRequest.copy(song = pendingRequest.song.copy(uri = targetUri))
        return try {
            val contentResolver = context.contentResolver

            // Resolve temp file — may be empty if prep failed before permission was granted.
            // Now that we have write access, retry preparation from the original URI.
            var tempFile = if (pendingRequest.tempFilePath.isNotEmpty()) File(pendingRequest.tempFilePath) else null
            if (tempFile == null || !tempFile.exists()) {
                Log.d(TAG, "Temp file absent; re-preparing metadata after permission grant")
                val retryPath = prepareTempFileWithMetadata(
                    context = context,
                    song = pendingRequest.song,
                    newTitle = pendingRequest.newTitle,
                    newArtist = pendingRequest.newArtist,
                    newAlbum = pendingRequest.newAlbum,
                    newGenre = pendingRequest.newGenre,
                    newYear = pendingRequest.newYear,
                    newTrackNumber = pendingRequest.newTrackNumber,
                    artworkUri = pendingRequest.artworkUriString?.toUri(),
                    removeArtwork = pendingRequest.removeArtwork,
                    newAlbumArtist = pendingRequest.newAlbumArtist,
                    newComposer = pendingRequest.newComposer,
                    newDiscNumber = pendingRequest.newDiscNumber
                )
                if (retryPath == null) {
                    Log.e(TAG, "Cannot write metadata: format not supported or file unreadable")
                    return false
                }
                tempFile = File(retryPath)
            }

            Log.d(TAG, "Completing write operation after permission granted for: ${pendingRequest.song.title}")

            // Now we have permission, copy the temp file back to original location
            val outputStream = contentResolver.openOutputStream(pendingRequest.song.uri, "w")
            if (outputStream == null) {
                Log.e(TAG, "Failed to open output stream even after permission granted")
                return false
            }

            outputStream.use { outStream ->
                tempFile.inputStream().use { inputStream ->
                    val bytesCopied = inputStream.copyTo(outStream)
                    Log.d(TAG, "Copied $bytesCopied bytes back to original location")
                }
            }

            // Update MediaStore as well
            val values = android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, pendingRequest.newTitle)
                put(MediaStore.Audio.Media.ARTIST, pendingRequest.newArtist)
                put(MediaStore.Audio.Media.ALBUM, pendingRequest.newAlbum)
                if (pendingRequest.newGenre.isNotBlank() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.GENRE, pendingRequest.newGenre)
                }
                if (pendingRequest.newYear > 0) {
                    put(MediaStore.Audio.Media.YEAR, pendingRequest.newYear)
                }
                if (pendingRequest.newTrackNumber > 0) {
                    put(MediaStore.Audio.Media.TRACK, pendingRequest.newTrackNumber)
                }
                if (pendingRequest.newAlbumArtist != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, pendingRequest.newAlbumArtist)
                }
                if (pendingRequest.newComposer != null) {
                    put(MediaStore.Audio.Media.COMPOSER, pendingRequest.newComposer)
                }
                if (pendingRequest.newDiscNumber > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.CD_TRACK_NUMBER, pendingRequest.newDiscNumber)
                }
            }

            contentResolver.update(pendingRequest.song.uri, values, null, null)

            // Clean up temp file
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(TAG, "Temp file cleaned up")
            }

            // Trigger media scanner
            val filePath = when (pendingRequest.song.uri.scheme) {
                "content" -> {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    contentResolver.query(pendingRequest.song.uri, projection, null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIndex =
                                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                cursor.getString(dataIndex)
                            } else null
                        }
                }

                "file" -> pendingRequest.song.uri.path
                else -> null
            }

            if (filePath != null) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null,
                    null
                )
                Log.d(TAG, "Media scanner triggered for: $filePath")
            }

            Log.d(TAG, "Successfully completed metadata write for: ${pendingRequest.song.title}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete write after permission granted", e)
            // Clean up temp file on error
            try {
                File(pendingRequest.tempFilePath).delete()
            } catch (ignored: Exception) {
            }
            false
        }
    }

    /**
     * Cleans up a pending write request (delete temp file)
     */
    fun cleanupPendingWriteRequest(pendingRequest: PendingWriteRequest) {
        try {
            val tempFile = File(pendingRequest.tempFilePath)
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(
                    TAG,
                    "Cleaned up pending write request temp file: ${pendingRequest.tempFilePath}"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup pending write request", e)
        }
    }

    /**
     * Gets the genre name from MediaStore.Audio.Genres table using the song ID
     * @param contentResolver The ContentResolver to use for queries
     * @param songId The song ID to look up genre for
     * @return The genre name, or null if not found
     */
    private fun getGenreNameFromMediaStore(contentResolver: ContentResolver, songId: Int): String? {
        return try {
            // First get the genre ID from the audio_genres_map table
            val genreIdProjection = arrayOf(MediaStore.Audio.Genres.Members.GENRE_ID)
            val genreIdCursor = contentResolver.query(
                MediaStore.Audio.Genres.getContentUriForAudioId("external", songId),
                genreIdProjection,
                null,
                null,
                null
            )

            genreIdCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val genreIdIndex =
                        cursor.getColumnIndex(MediaStore.Audio.Genres.Members.GENRE_ID)
                    if (genreIdIndex != -1) {
                        val genreId = cursor.getLong(genreIdIndex)

                        // Now get the genre name from the genres table
                        val genreNameProjection = arrayOf(MediaStore.Audio.Genres.NAME)
                        val genreNameCursor = contentResolver.query(
                            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                            genreNameProjection,
                            "${MediaStore.Audio.Genres._ID} = ?",
                            arrayOf(genreId.toString()),
                            null
                        )

                        genreNameCursor?.use { nameCursor ->
                            if (nameCursor.moveToFirst()) {
                                val nameIndex =
                                    nameCursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
                                if (nameIndex != -1) {
                                    val genreName = nameCursor.getString(nameIndex)
                                    Log.d(TAG, "Found genre name: $genreName for song ID: $songId")
                                    return genreName
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "No genre found for song ID: $songId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting genre name from MediaStore", e)
            null
        }
    }

    /**
     * Enhanced genre detection with multiple fallback methods
     * @param context The application context
     * @param songUri The URI of the song file
     * @param songId The song ID for MediaStore queries
     * @return The detected genre name, or null if not found
     */
    fun getGenreForSong(context: Context, songUri: Uri, songId: Int): String? {
        // Method 1: Try MediaStore.Audio.Media.GENRE column (may contain genre ID or name)
        try {
            val genreFromMediaStoreColumn = getGenreFromMediaStoreColumn(context, songId)
            if (!genreFromMediaStoreColumn.isNullOrBlank()) {
                Log.d(
                    TAG,
                    "Found genre from MediaStore column: $genreFromMediaStoreColumn for song ID: $songId"
                )
                return genreFromMediaStoreColumn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from MediaStore column", e)
        }

        // Method 2: Try MediaStore.Audio.Genres table lookup
        try {
            val genreFromGenresTable = getGenreNameFromMediaStore(context.contentResolver, songId)
            if (!genreFromGenresTable.isNullOrBlank()) {
                Log.d(
                    TAG,
                    "Found genre from Genres table: $genreFromGenresTable for song ID: $songId"
                )
                return genreFromGenresTable
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from Genres table", e)
        }

        // Method 3: Try MediaMetadataRetriever
        try {
            val genreFromRetriever = getGenreFromMediaMetadataRetriever(context, songUri)
            if (!genreFromRetriever.isNullOrBlank()) {
                Log.d(
                    TAG,
                    "Found genre from MediaMetadataRetriever: $genreFromRetriever for song URI: $songUri"
                )
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
    private fun getGenreFromMediaStoreColumn(context: Context, songId: Int): String? {
        // GENRE column only available on API 30+ (Android 11)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
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
    private fun getGenreNameFromId(contentResolver: ContentResolver, genreId: Long): String? {
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
    private fun getGenreFromMediaMetadataRetriever(context: Context, songUri: Uri): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, songUri)
            val genre =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
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

    private fun updateViaMediaStore(
        contentResolver: android.content.ContentResolver,
        song: Song,
        values: android.content.ContentValues
    ): Int {
        return try {
            Log.d(TAG, "Attempting update via MediaStore table for song ID: ${song.id}")

            // Try to update via MediaStore query using the song ID
            val selection = "${MediaStore.Audio.Media._ID} = ?"
            val selectionArgs = arrayOf(song.id)

            val rowsUpdated = contentResolver.update(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
                selection,
                selectionArgs
            )

            Log.d(TAG, "MediaStore update result: $rowsUpdated rows updated")

            if (rowsUpdated == 0) {
                // Try alternative approach with different content URI
                Log.d(TAG, "Trying alternative content URI")
                val altRowsUpdated = contentResolver.update(
                    MediaStore.Audio.Media.getContentUri("external"),
                    values,
                    selection,
                    selectionArgs
                )
                Log.d(TAG, "Alternative URI update result: $altRowsUpdated rows updated")
                return altRowsUpdated
            }

            rowsUpdated
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during MediaStore update - check app permissions", e)
            0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update via MediaStore table", e)
            0
        }
    }

    /**
     * Extracts embedded album art directly from an audio file.
     * This bypasses the Android MediaStore cache and reads from the file header directly.
     *
     * @param context The application context
     * @param songUri The URI of the song file
     * @param cacheDir The cache directory to save the extracted artwork
     * @param lossless If true, saves raw bytes as PNG without re-compression; if false, compresses to JPEG quality 90
     * @return Uri pointing to the cached artwork file, or null if no embedded art found
     */
    fun extractEmbeddedAlbumArt(
        context: Context,
        songUri: Uri,
        cacheDir: File,
        lossless: Boolean = false
    ): Uri? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, songUri)

            var embeddedArt = retriever.embeddedPicture
            var filePath: String? = null
            if (embeddedArt == null || embeddedArt.isEmpty()) {
                // Try jaudiotagger fallback
                filePath = when (songUri.scheme) {
                    "content" -> {
                        val projection = arrayOf(MediaStore.Audio.Media.DATA)
                        context.contentResolver.query(songUri, projection, null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                                    if (dataIndex != -1) cursor.getString(dataIndex) else null
                                } else null
                            }
                    }
                    "file" -> songUri.path
                    else -> null
                }
                if (filePath != null) {
                    try {
                        val file = File(filePath)
                        if (file.exists() && isSupportedByJaudiotagger(file.extension)) {
                            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                            val tag = audioFile.tag
                            if (tag != null) {
                                val artwork = tag.firstArtwork
                                if (artwork != null) {
                                    val artworkBytes = artwork.binaryData
                                    if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                                        embeddedArt = artworkBytes
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract embedded art via jaudiotagger fallback: ${e.message}")
                    }
                }
            }

            // Fallback to folder cover discovery if embeddedArt is still not found
            if (embeddedArt == null || embeddedArt.isEmpty()) {
                if (filePath == null) {
                    filePath = when (songUri.scheme) {
                        "content" -> {
                            val projection = arrayOf(MediaStore.Audio.Media.DATA)
                            context.contentResolver.query(songUri, projection, null, null, null)
                                ?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                                        if (dataIndex != -1) cursor.getString(dataIndex) else null
                                    } else null
                                }
                        }
                        "file" -> songUri.path
                        else -> null
                    }
                }
                if (filePath != null) {
                    try {
                        val file = File(filePath)
                        val parentFolder = file.parentFile
                        if (parentFolder != null && parentFolder.exists() && parentFolder.isDirectory) {
                            val coverFile = findBestCover(parentFolder)
                            if (coverFile != null && coverFile.exists()) {
                                val coverBytes = coverFile.readBytes()
                                if (coverBytes.isNotEmpty()) {
                                    embeddedArt = coverBytes
                                    Log.d(TAG, "Found folder cover art: ${coverFile.absolutePath}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to find folder cover artwork: ${e.message}")
                    }
                }
            }

            if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                return cacheEmbeddedArtworkBytes(songUri, cacheDir, embeddedArt, lossless)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract embedded album art", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
        return null
    }

    /**
     * Looks up cached embedded album artwork for a song URI.
     * Supports both the current cache layout and legacy file names.
     */
    fun getCachedEmbeddedAlbumArtUri(cacheDir: File, songUri: Uri, lossless: Boolean = false): Uri? {
        val songKey = buildArtworkCacheKey(songUri)
        val primaryPrefix = if (lossless) "embedded_art_lossless_$songKey" else "embedded_art_$songKey"
        val fallbackPrefix = if (lossless) "embedded_art_$songKey" else "embedded_art_lossless_$songKey"

        val directoriesToSearch = mutableListOf(File(cacheDir, EMBEDDED_ARTWORK_CACHE_DIR))
        val parent = cacheDir.parentFile
        if (parent != null && parent.exists()) {
            val altDirName = if (cacheDir.name == "cache") "files" else "cache"
            directoriesToSearch.add(File(File(parent, altDirName), EMBEDDED_ARTWORK_CACHE_DIR))
        }

        val extensions = listOf("jpg", "png", "webp", "gif", "bmp", "img")
        for (dir in directoriesToSearch) {
            val modernCandidate = findFirstExistingArtworkFile(
                directory = dir,
                prefixes = listOf(primaryPrefix, fallbackPrefix),
                extensions = extensions
            )
            if (modernCandidate != null) {
                return modernCandidate.toUri()
            }
        }

        val legacyHash = songUri.hashCode()
        val primaryLegacyPrefix = if (lossless) {
            "embedded_art_lossless_$legacyHash"
        } else {
            "embedded_art_$legacyHash"
        }
        val fallbackLegacyPrefix = if (lossless) {
            "embedded_art_$legacyHash"
        } else {
            "embedded_art_lossless_$legacyHash"
        }

        val legacyCandidate = findFirstExistingArtworkFile(
            directory = cacheDir,
            prefixes = listOf(primaryLegacyPrefix, fallbackLegacyPrefix),
            extensions = extensions
        )
        if (legacyCandidate != null) {
            return legacyCandidate.toUri()
        }

        return null
    }

    private fun findFirstExistingArtworkFile(
        directory: File,
        prefixes: List<String>,
        extensions: List<String>
    ): File? {
        for (prefix in prefixes) {
            for (ext in extensions) {
                val candidate = File(directory, "$prefix.$ext")
                if (candidate.exists() && candidate.length() > 0L) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun cacheEmbeddedArtworkBytes(
        songUri: Uri,
        cacheDir: File,
        embeddedArt: ByteArray,
        lossless: Boolean
    ): Uri? {
        if (embeddedArt.isEmpty()) return null

        getCachedEmbeddedAlbumArtUri(cacheDir, songUri, lossless)?.let {
            maybePruneArtworkCache(cacheDir)
            return it
        }

        val artworkCacheDir = File(cacheDir, EMBEDDED_ARTWORK_CACHE_DIR)
        if (!artworkCacheDir.exists() && !artworkCacheDir.mkdirs()) {
            Log.w(TAG, "Could not create artwork cache directory: ${artworkCacheDir.absolutePath}")
            return null
        }

        val songKey = buildArtworkCacheKey(songUri)
        val prefix = if (lossless) "embedded_art_lossless_$songKey" else "embedded_art_$songKey"
        val detectedExtension = detectArtworkExtension(embeddedArt)

        val cachedFile = if (lossless) {
            val target = File(artworkCacheDir, "$prefix.$detectedExtension")
            when {
                target.exists() && target.length() > 0L -> target
                writeBytesAtomically(target, embeddedArt) -> target
                else -> null
            }
        } else {
            val lossyTarget = File(artworkCacheDir, "$prefix.jpg")
            when {
                lossyTarget.exists() && lossyTarget.length() > 0L -> lossyTarget
                writeLossyArtwork(lossyTarget, embeddedArt) -> lossyTarget
                else -> {
                    val fallbackTarget = File(artworkCacheDir, "$prefix.$detectedExtension")
                    when {
                        fallbackTarget.exists() && fallbackTarget.length() > 0L -> fallbackTarget
                        writeBytesAtomically(fallbackTarget, embeddedArt) -> fallbackTarget
                        else -> null
                    }
                }
            }
        }

        cachedFile ?: return null
        maybePruneArtworkCache(cacheDir)
        return cachedFile.toUri()
    }

    private fun writeLossyArtwork(targetFile: File, embeddedArt: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size, bounds)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1200, 1200)
        }

        val bitmap = BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size, decodeOptions)
            ?: return false

        return try {
            writeBitmapAtomically(targetFile, bitmap, Bitmap.CompressFormat.JPEG, 88)
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeBitmapAtomically(
        targetFile: File,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Boolean {
        val parent = targetFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            return false
        }

        val tempFile = File(parent, "${targetFile.name}.tmp")
        return try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            var compressed = false
            FileOutputStream(tempFile).use { out ->
                compressed = bitmap.compress(format, quality, out)
                out.flush()
            }

            if (!compressed) {
                tempFile.delete()
                return false
            }

            replaceFile(tempFile, targetFile)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write bitmap cache file: ${targetFile.absolutePath}", e)
            tempFile.delete()
            false
        }
    }

    private fun writeBytesAtomically(targetFile: File, bytes: ByteArray): Boolean {
        val parent = targetFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            return false
        }

        val tempFile = File(parent, "${targetFile.name}.tmp")
        return try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            FileOutputStream(tempFile).use { out ->
                out.write(bytes)
                out.flush()
            }

            replaceFile(tempFile, targetFile)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write artwork bytes: ${targetFile.absolutePath}", e)
            tempFile.delete()
            false
        }
    }

    private fun replaceFile(tempFile: File, targetFile: File): Boolean {
        if (targetFile.exists() && !targetFile.delete()) {
            return false
        }

        if (tempFile.renameTo(targetFile)) {
            return true
        }

        return try {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to move temp artwork file into place", e)
            false
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): Int {
        if (width <= 0 || height <= 0) return 1

        var inSampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2

        while ((halfWidth / inSampleSize) >= requestedWidth &&
            (halfHeight / inSampleSize) >= requestedHeight
        ) {
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun detectArtworkExtension(data: ByteArray): String {
        if (data.size >= 2 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte()
        ) {
            return "jpg"
        }

        if (data.size >= 4 &&
            data[0] == 0x89.toByte() &&
            data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() &&
            data[3] == 0x47.toByte()
        ) {
            return "png"
        }

        if (data.size >= 12 &&
            data[0] == 'R'.code.toByte() &&
            data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() &&
            data[3] == 'F'.code.toByte() &&
            data[8] == 'W'.code.toByte() &&
            data[9] == 'E'.code.toByte() &&
            data[10] == 'B'.code.toByte() &&
            data[11] == 'P'.code.toByte()
        ) {
            return "webp"
        }

        if (data.size >= 4 &&
            data[0] == 'G'.code.toByte() &&
            data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() &&
            data[3] == '8'.code.toByte()
        ) {
            return "gif"
        }

        if (data.size >= 2 &&
            data[0] == 'B'.code.toByte() &&
            data[1] == 'M'.code.toByte()
        ) {
            return "bmp"
        }

        return "img"
    }

    private fun buildArtworkCacheKey(songUri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(songUri.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun maybePruneArtworkCache(cacheDir: File) {
        val now = System.currentTimeMillis()
        val shouldPrune = synchronized(this) {
            if (now - lastEmbeddedArtworkCleanupMs < EMBEDDED_ART_CACHE_CLEANUP_INTERVAL_MS) {
                false
            } else {
                lastEmbeddedArtworkCleanupMs = now
                true
            }
        }

        if (!shouldPrune) return

        val artworkCacheDir = File(cacheDir, EMBEDDED_ARTWORK_CACHE_DIR)
        val currentArtworkFiles = artworkCacheDir
            .listFiles { file -> file.isFile }
            ?.toMutableList()
            ?: mutableListOf()

        val legacyArtworkFiles = cacheDir
            .listFiles { file ->
                file.isFile &&
                    (file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_"))
            }
            ?.toList()
            .orEmpty()

        val allArtworkFiles = mutableListOf<File>().apply {
            addAll(currentArtworkFiles)
            addAll(legacyArtworkFiles)
        }

        if (allArtworkFiles.isEmpty()) return

        var totalSize = allArtworkFiles.sumOf { it.length() }
        var fileCount = allArtworkFiles.size

        if (totalSize <= EMBEDDED_ART_CACHE_MAX_BYTES && fileCount <= EMBEDDED_ART_CACHE_MAX_FILES) {
            return
        }

        allArtworkFiles.sortBy { it.lastModified() }

        for (file in allArtworkFiles) {
            if (totalSize <= EMBEDDED_ART_CACHE_MAX_BYTES && fileCount <= EMBEDDED_ART_CACHE_MAX_FILES) {
                break
            }

            val fileSize = file.length()
            if (file.delete()) {
                totalSize -= fileSize
                fileCount--
            }
        }

        Log.d(
            TAG,
            "Pruned artwork cache to ${totalSize / (1024 * 1024)}MB across $fileCount files"
        )
    }

    /*
     *     Copyright (C) 2025 nift4
     *
     *     Gramophone is free software: you can redistribute it and/or modify
     *     it under the terms of the GNU General Public License as published by
     *     the Free Software Foundation, either version 3 of the License, or
     *     (at your option) any later version.
     *
     *     Gramophone is distributed in the hope that it will be useful,
     *     but WITHOUT ANY WARRANTY; without even the implied warranty of
     *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     *     GNU General Public License for more details.
     *
     *     You should have received a copy of the GNU General Public License
     *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
     */
    fun deleteCachedEmbeddedArtwork(cacheDir: File, songUri: Uri) {
        try {
            val songKey = buildArtworkCacheKey(songUri)
            val legacyHash = songUri.hashCode()
            val primaryPrefix = "embedded_art_lossless_$songKey"
            val secondaryPrefix = "embedded_art_$songKey"
            val legacyPrimaryPrefix = "embedded_art_lossless_$legacyHash"
            val legacySecondaryPrefix = "embedded_art_$legacyHash"

            val prefixes = listOf(primaryPrefix, secondaryPrefix, legacyPrimaryPrefix, legacySecondaryPrefix)
            val artworkCacheDir = File(cacheDir, EMBEDDED_ARTWORK_CACHE_DIR)
            
            if (artworkCacheDir.exists() && artworkCacheDir.isDirectory) {
                artworkCacheDir.listFiles()?.forEach { file ->
                    if (prefixes.any { file.name.startsWith(it) }) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted cached embedded artwork: ${file.name}")
                        }
                    }
                }
            }

            cacheDir.listFiles()?.forEach { file ->
                if (prefixes.any { file.name.startsWith(it) }) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted legacy cached embedded artwork: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete cached embedded artwork for $songUri", e)
        }
    }

    fun findBestCover(songFolder: File): File? {
        val allowedExt = listOf("jpg", "png", "jpeg", "bmp", "tiff", "tif", "webp")
        var bestScore = 0
        var bestFile: File? = null
        try {
            val files = songFolder.listFiles() ?: return null
            for (file in files) {
                if (file.extension.lowercase() !in allowedExt)
                    continue
                var score = 1
                when (file.extension.lowercase()) {
                    "jpg" -> score += 3
                    "png" -> score += 2
                    "jpeg" -> score += 1
                }
                if (file.nameWithoutExtension.contentEquals("albumart", true)) score += 24
                else if (file.nameWithoutExtension.contentEquals("cover", true)) score += 20
                else if (file.nameWithoutExtension.startsWith("albumart", true)) score += 16
                else if (file.nameWithoutExtension.startsWith("cover", true)) score += 12
                else if (file.nameWithoutExtension.contains("albumart", true)) score += 8
                else if (file.nameWithoutExtension.contains("cover", true)) score += 4
                if (bestScore < score) {
                    bestScore = score
                    bestFile = file
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files for best cover", e)
        }
        if (bestScore >= 3) {
            return bestFile
        }
        return null
    }

    private fun adjustTempFileExtension(tempFile: File): File {
        val container = detectContainerFormat(tempFile.absolutePath)
        if (container != DetectedContainer.UNKNOWN) {
            val currentExtension = tempFile.extension
            val newExtension = container.canonicalExtension
            if (currentExtension.lowercase() != newExtension.lowercase()) {
                val newFile = File(
                    tempFile.parentFile,
                    tempFile.nameWithoutExtension + "." + newExtension
                )
                if (tempFile.renameTo(newFile)) {
                    Log.d(TAG, "Renamed temp file from extension $currentExtension to detected extension $newExtension")
                    return newFile
                }
            }
        }
        return tempFile
    }

    private enum class DetectedContainer(val canonicalExtension: String) {
        MP3("mp3"),
        MP4("m4a"),
        FLAC("flac"),
        OGG("ogg"),
        WAV("wav"),
        UNKNOWN("")
    }

    private fun detectContainerFormat(filePath: String): DetectedContainer {
        return try {
            File(filePath).inputStream().use { input ->
                val header = ByteArray(512)
                var bytesRead = 0
                while (bytesRead < header.size) {
                    val read = input.read(header, bytesRead, header.size - bytesRead)
                    if (read <= 0) break
                    bytesRead += read
                }
                if (bytesRead < 4) return DetectedContainer.UNKNOWN
                when {
                    header[0] == 'I'.code.toByte() &&
                        header[1] == 'D'.code.toByte() &&
                        header[2] == '3'.code.toByte() -> DetectedContainer.MP3
                    header[0] == 0xFF.toByte() &&
                        (header[1].toInt() and 0xE0) == 0xE0 -> DetectedContainer.MP3
                    bytesRead >= 8 &&
                        header[4] == 'f'.code.toByte() &&
                        header[5] == 't'.code.toByte() &&
                        header[6] == 'y'.code.toByte() &&
                        header[7] == 'p'.code.toByte() -> DetectedContainer.MP4
                    header[0] == 'f'.code.toByte() &&
                        header[1] == 'L'.code.toByte() &&
                        header[2] == 'a'.code.toByte() &&
                        header[3] == 'C'.code.toByte() -> DetectedContainer.FLAC
                    header[0] == 'O'.code.toByte() &&
                        header[1] == 'g'.code.toByte() &&
                        header[2] == 'g'.code.toByte() &&
                        header[3] == 'S'.code.toByte() -> DetectedContainer.OGG
                    bytesRead >= 12 &&
                        header[0] == 'R'.code.toByte() &&
                        header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte() &&
                        header[3] == 'F'.code.toByte() &&
                        header[8] == 'W'.code.toByte() &&
                        header[9] == 'A'.code.toByte() &&
                        header[10] == 'V'.code.toByte() &&
                        header[11] == 'E'.code.toByte() -> DetectedContainer.WAV
                    else -> DetectedContainer.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Container detection failed for $filePath")
            DetectedContainer.UNKNOWN
        }
    }

    fun getSpecificVolumeUri(context: Context, songId: Long): Uri? {
        if (songId <= 0) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val volumeName = try {
                val projection = arrayOf(MediaStore.Audio.Media.VOLUME_NAME)
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
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME))
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }

            val targetVolume = if (volumeName != null && volumeName != MediaStore.VOLUME_EXTERNAL) {
                volumeName
            } else {
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            }
            MediaStore.Audio.Media.getContentUri(targetVolume, songId)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        }
    }

    private fun updateFileMetadataWithTagLib(
        context: Context,
        file: File,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: Int,
        newTrackNumber: Int,
        newAlbumArtist: String?,
        newComposer: String?,
        newDiscNumber: Int,
        artworkUri: Uri?,
        removeArtwork: Boolean
    ): Boolean {
        return try {
            Log.d(TAG, "TAGLIB: Opening file for edit: ${file.absolutePath}")
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                val metadataFd = fd.dup()
                val existingMetadata = try {
                    TagLib.getMetadata(metadataFd.detachFd())
                } catch (e: Exception) {
                    Log.w(TAG, "TAGLIB: Failed to read existing metadata, will overwrite", e)
                    null
                }
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                propertyMap["TITLE"] = arrayOf(newTitle)
                propertyMap["ARTIST"] = arrayOf(newArtist)
                propertyMap["ALBUM"] = arrayOf(newAlbum)
                
                if (!newAlbumArtist.isNullOrBlank()) {
                    propertyMap["ALBUMARTIST"] = arrayOf(newAlbumArtist)
                } else {
                    propertyMap.remove("ALBUMARTIST")
                }
                
                if (!newComposer.isNullOrBlank()) {
                    propertyMap["COMPOSER"] = arrayOf(newComposer)
                } else {
                    propertyMap.remove("COMPOSER")
                }
                
                if (newGenre.isNotBlank()) {
                    propertyMap["GENRE"] = arrayOf(newGenre)
                } else {
                    propertyMap.remove("GENRE")
                }
                
                if (newTrackNumber > 0) {
                    propertyMap["TRACKNUMBER"] = arrayOf(newTrackNumber.toString())
                } else {
                    propertyMap.remove("TRACKNUMBER")
                }
                
                if (newDiscNumber > 0) {
                    propertyMap["DISCNUMBER"] = arrayOf(newDiscNumber.toString())
                } else {
                    propertyMap.remove("DISCNUMBER")
                }
                
                if (newYear > 0) {
                    propertyMap["DATE"] = arrayOf(newYear.toString())
                } else {
                    propertyMap.remove("DATE")
                }

                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                if (!metadataSaved) {
                    Log.e(TAG, "TAGLIB: Failed to save property map")
                    return false
                }

                // Handle Artwork
                if (removeArtwork) {
                    val pictureFd = fd.dup()
                    TagLib.savePictures(pictureFd.detachFd(), emptyArray())
                    Log.d(TAG, "TAGLIB: Removed artwork")
                } else if (artworkUri != null) {
                    try {
                        val mimeType = context.contentResolver.getType(artworkUri) ?: "image/jpeg"
                        val artworkBytes = context.contentResolver.openInputStream(artworkUri)?.use { it.readBytes() }
                        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                            val picture = Picture(
                                data = artworkBytes,
                                description = "Front Cover",
                                pictureType = "Front Cover",
                                mimeType = mimeType
                            )
                            val pictureFd = fd.dup()
                            val coverSaved = TagLib.savePictures(pictureFd.detachFd(), arrayOf(picture))
                            if (!coverSaved) {
                                Log.w(TAG, "TAGLIB: Failed to save cover art")
                            } else {
                                Log.d(TAG, "TAGLIB: Successfully saved cover art")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "TAGLIB: Failed to embed cover art", e)
                    }
                }
            }

            // Sync file
            try {
                java.io.RandomAccessFile(file, "rw").use { raf ->
                    raf.fd.sync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "TAGLIB: sync failed", e)
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "TAGLIB: Failed to update metadata", e)
            false
        }
    }

    private fun embedLyricsWithTagLib(file: File, lyrics: String): Boolean {
        return try {
            Log.d(TAG, "TAGLIB: Opening file for lyrics embed: ${file.absolutePath}")
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                val metadataFd = fd.dup()
                val existingMetadata = try {
                    TagLib.getMetadata(metadataFd.detachFd())
                } catch (e: Exception) {
                    Log.w(TAG, "TAGLIB: Failed to read existing metadata for lyrics", e)
                    null
                }
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                if (lyrics.isNotBlank()) {
                    propertyMap["LYRICS"] = arrayOf(lyrics)
                } else {
                    propertyMap.remove("LYRICS")
                }

                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                if (!metadataSaved) {
                    Log.e(TAG, "TAGLIB: Failed to save property map with lyrics")
                    return false
                }
            }
            try {
                java.io.RandomAccessFile(file, "rw").use { raf ->
                    raf.fd.sync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "TAGLIB: sync failed after lyrics embed", e)
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "TAGLIB: Failed to embed lyrics", e)
            false
        }
    }

    private fun embedLyricsInAudioFile(audioFile: File, lyrics: String): Boolean {
        return try {
            val audioFileObj = AudioFileIO.read(audioFile)
            val tag: Tag = audioFileObj.tag ?: audioFileObj.createDefaultTag()
            tag.setField(FieldKey.LYRICS, lyrics)
            audioFileObj.tag = tag
            AudioFileIO.write(audioFileObj)
            true
        } catch (e: Exception) {
            Log.w(TAG, "JAudioTagger failed for lyrics embed, falling back to TagLib", e)
            embedLyricsWithTagLib(audioFile, lyrics)
        }
    }

    fun readLyricsViaTagLib(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val metadata = TagLib.getMetadata(fd.detachFd())
                val propertyMap = metadata?.propertyMap ?: emptyMap()
                val lyricsValues = propertyMap["LYRICS"]
                if (!lyricsValues.isNullOrEmpty() && lyricsValues[0].isNotBlank()) {
                    lyricsValues[0]
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TAGLIB: Failed to read lyrics", e)
            null
        }
    }
}

