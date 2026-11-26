/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import chromahub.rhythm.app.core.domain.backup.BackupSongResolver
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Marker exception used to signal a Rhythm backup file was selected in the single-playlist import flow
class RhythmBackupDetectedException(val backupJson: String) : Exception("Rhythm backup file detected")

/**
 * Utility class for importing and exporting playlists in various formats
 */
object PlaylistImportExportUtils {
    private const val TAG = "PlaylistImportExport"
    
    /**
     * Builds a map of file paths to songs by querying MediaStore.
     * This is essential for M3U/PLS import since content URIs don't contain file paths.
     */
    private fun buildFilePathToSongMap(context: Context, availableSongs: List<Song>): Map<String, Song> {
        val pathToSong = mutableMapOf<String, Song>()
        val songIdToSong = availableSongs.associateBy { it.id }
        
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DATA
            )
            
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val dataIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex).toString()
                    val filePath = cursor.getString(dataIndex) ?: continue
                    
                    songIdToSong[id]?.let { song ->
                        // Store both original path and lowercase version for case-insensitive matching
                        pathToSong[filePath] = song
                        pathToSong[filePath.lowercase()] = song
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error building file path map", e)
        }
        
        Log.d(TAG, "Built file path map with ${pathToSong.size / 2} unique entries")
        return pathToSong
    }
    
    /**
     * Gets the directory path from the M3U file URI for resolving relative paths.
     */
    private fun getM3uDirectoryPath(context: Context, m3uUri: Uri): String? {
        return try {
            when (m3uUri.scheme) {
                "content" -> {
                    // For content URIs, try to get the path from DocumentFile
                    val documentFile = DocumentFile.fromSingleUri(context, m3uUri)
                    val fileName = documentFile?.name ?: return null
                    
                    // Try to get the actual file path via MediaStore
                    context.contentResolver.query(m3uUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            // Try DATA column if available
                            val dataIndex = cursor.getColumnIndex("_data")
                            if (dataIndex >= 0) {
                                val path = cursor.getString(dataIndex)
                                if (!path.isNullOrEmpty()) {
                                    return@use java.io.File(path).parent
                                }
                            }
                        }
                        null
                    }
                }
                "file" -> {
                    m3uUri.path?.let { java.io.File(it).parent }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting M3U directory path", e)
            null
        }
    }
    
    enum class PlaylistExportFormat(val extension: String, val mimeType: String, val displayName: String) {
        JSON(".json", "application/json", "JSON Format"),
        M3U(".m3u", "audio/x-mpegurl", "M3U Playlist"),
        M3U8(".m3u8", "application/x-mpegURL", "M3U8 Playlist"),
        PLS(".pls", "audio/x-scpls", "PLS Playlist"),
        XSPF(".xspf", "application/xspf+xml", "XSPF Playlist"),
        WPL(".wpl", "application/vnd.ms-wpl", "WPL Playlist")
    }
    
    data class PlaylistExportData(
        @SerializedName("name") val name: String,
        @SerializedName("id") val id: String,
        @SerializedName("dateCreated") val dateCreated: Long,
        @SerializedName("dateModified") val dateModified: Long,
        @SerializedName("songs") val songs: List<PlaylistSongEntry>,
        @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
        @SerializedName("exportedBy") val exportedBy: String = "Rhythm Music Player"
    )
    
    data class PlaylistSongEntry(
        @SerializedName("title") val title: String,
        @SerializedName("artist") val artist: String,
        @SerializedName("album") val album: String,
        @SerializedName("duration") val duration: Long,
        @SerializedName("filePath") val filePath: String?, // Original file path if available
        @SerializedName("uri") val uri: String, // Content URI
        @SerializedName("trackNumber") val trackNumber: Int = 0,
        @SerializedName("year") val year: Int = 0
    )
    
    /**
     * Exports a single playlist to the specified format
     */
    fun exportPlaylist(
        context: Context,
        playlist: Playlist,
        format: PlaylistExportFormat,
        outputDirectory: File? = null,
        userSelectedDirectoryUri: Uri? = null
    ): Result<File> {
        return try {
            val fileName = sanitizeFileName("${playlist.name}_${getCurrentTimestamp()}${format.extension}")
            
            // Use user-selected directory if provided, otherwise use default
            val outputFile = if (userSelectedDirectoryUri != null) {
                // Use Storage Access Framework for user-selected directory
                exportToUserSelectedDirectory(context, userSelectedDirectoryUri, playlist, format, fileName)
            } else {
                // Use traditional file system approach
                val exportDir = outputDirectory ?: getDefaultExportDirectory(context)
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val file = File(exportDir, fileName)
                
                when (format) {
                    PlaylistExportFormat.JSON -> exportToJson(playlist, file)
                    PlaylistExportFormat.M3U -> file.writeText(PlaylistSerializer.serializeM3u(playlist, false))
                    PlaylistExportFormat.M3U8 -> file.writeText(PlaylistSerializer.serializeM3u(playlist, true))
                    PlaylistExportFormat.PLS -> file.writeText(PlaylistSerializer.serializePls(playlist))
                    PlaylistExportFormat.XSPF -> file.writeText(PlaylistSerializer.serializeXspf(playlist))
                    PlaylistExportFormat.WPL -> file.writeText(PlaylistSerializer.serializeWpl(playlist))
                }
                file
            }
            
            Log.d(TAG, "Successfully exported playlist '${playlist.name}' to ${outputFile.absolutePath}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting playlist '${playlist.name}'", e)
            Result.failure(e)
        }
    }
    
    /**
     * Exports multiple playlists to a single ZIP file
     */
    fun exportAllPlaylists(
        context: Context,
        playlists: List<Playlist>,
        format: PlaylistExportFormat,
        outputDirectory: File? = null,
        userSelectedDirectoryUri: Uri? = null
    ): Result<File> {
        return try {
            val fileName = "RhythmPlaylists_${getCurrentTimestamp()}.zip"
            
            // Use user-selected directory if provided, otherwise use default
            val zipFile = if (userSelectedDirectoryUri != null) {
                // Use Storage Access Framework for user-selected directory
                exportAllPlaylistsToUserSelectedDirectory(context, userSelectedDirectoryUri, playlists, format, fileName)
            } else {
                // Use traditional file system approach
                val exportDir = outputDirectory ?: getDefaultExportDirectory(context)
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val file = File(exportDir, fileName)
                createPlaylistsZip(playlists, format, file)
                file
            }
            
            Log.d(TAG, "Successfully exported ${playlists.size} playlists to ${zipFile.absolutePath}")
            Result.success(zipFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting all playlists", e)
            Result.failure(e)
        }
    }
    
    /**
     * Returns true when the given JSON string is a Rhythm app backup
     * (i.e. it contains the "backup_version" and "playlists_data" keys).
     */
    fun isRhythmBackupJson(json: String): Boolean {
        return try {
            val map = GsonUtils.gson.fromJson(json, Map::class.java) ?: return false
            map.containsKey("backup_version") && map.containsKey("playlists_data")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Imports ALL playlists from a Rhythm app backup JSON.
     *
     * A Rhythm backup stores playlists as a `List<Playlist>` JSON string under the
     * `playlists_data` key.  Each song's URI is matched against [availableSongs] so that
     * only locally-present tracks are kept.
     */
    fun importPlaylistsFromRhythmBackup(
        context: Context,
        backupJson: String,
        availableSongs: List<Song>
    ): Result<List<Playlist>> {
        return try {
            val backupMap = GsonUtils.gson.fromJson(backupJson, Map::class.java)
                ?: return Result.failure(IllegalArgumentException("Invalid Rhythm backup JSON"))

            val playlistsData = backupMap["playlists_data"] as? String
                ?: return Result.failure(IllegalArgumentException("No playlist data found in backup"))

            val type = object : TypeToken<List<Playlist>>() {}.type
            val rawPlaylists: List<Playlist> = GsonUtils.gson.fromJson(playlistsData, type)
                ?: return Result.failure(IllegalArgumentException("Failed to parse playlists from backup"))

            val (restoredPlaylists, _) = BackupSongResolver.resolvePlaylists(
                playlists = rawPlaylists,
                availableSongs = availableSongs,
                keepUnresolvedStreaming = false
            )

            Log.d(TAG, "Imported ${restoredPlaylists.size} playlists from Rhythm backup")
            Result.success(restoredPlaylists)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing playlists from Rhythm backup", e)
            Result.failure(e)
        }
    }

    /**
     * Imports a playlist from a file.
     *
     * If the selected file is a full Rhythm app backup (detected via [isRhythmBackupJson]),
     * a [RhythmBackupDetectedException] is thrown so that the caller can redirect to
     * [importPlaylistsFromRhythmBackup] for batch import.
     */
    fun importPlaylist(
        context: Context,
        uri: Uri,
        availableSongs: List<Song>
    ): Result<Playlist> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Cannot open file"))
            
            val content = inputStream.bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                return Result.failure(IllegalArgumentException("Selected file is empty"))
            }
            val fileName = getFileName(context, uri)
            
            // Detect Rhythm backup files early so the ViewModel can handle batch import
            val trimmedContent = content.trim()
            if ((fileName.endsWith(".json", true) || (trimmedContent.startsWith("{") && trimmedContent.endsWith("}"))) &&
                isRhythmBackupJson(trimmedContent)) {
                throw RhythmBackupDetectedException(trimmedContent)
            }

            val filePathMap = buildFilePathToSongMap(context, availableSongs)
            val m3uDirectory = getM3uDirectoryPath(context, uri)
            
            Log.d(TAG, "Importing playlist: $fileName, M3U directory: $m3uDirectory")
            
            val resolvedFormat = when {
                fileName.endsWith(".json", true) || (trimmedContent.startsWith("{") && trimmedContent.endsWith("}")) -> "json"
                fileName.endsWith(".m3u", true) || fileName.endsWith(".m3u8", true) || trimmedContent.startsWith("#EXTM3U", true) -> "m3u"
                fileName.endsWith(".pls", true) || trimmedContent.startsWith("[playlist]", true) -> "pls"
                fileName.endsWith(".xspf", true) || trimmedContent.contains("<playlist", true) -> "xspf"
                fileName.endsWith(".wpl", true) || trimmedContent.contains("<smil", true) -> "wpl"
                else -> null
            }

            val defaultName = fileName.substringBeforeLast(".")
            val playlist = when (resolvedFormat) {
                "json" -> importFromJson(content, availableSongs)
                "m3u" -> {
                    val parsed = PlaylistSerializer.parseM3u(content)
                    buildImportedPlaylist(parsed.name ?: defaultName, parsed.entries, availableSongs, filePathMap, m3uDirectory)
                }
                "pls" -> {
                    val parsed = PlaylistSerializer.parsePls(content)
                    buildImportedPlaylist(parsed.name ?: defaultName, parsed.entries, availableSongs, filePathMap, m3uDirectory)
                }
                "xspf" -> {
                    val parsed = PlaylistSerializer.parseXspf(content)
                    buildImportedPlaylist(parsed.name ?: defaultName, parsed.entries, availableSongs, filePathMap, m3uDirectory)
                }
                "wpl" -> {
                    val parsed = PlaylistSerializer.parseWpl(content)
                    buildImportedPlaylist(parsed.name ?: defaultName, parsed.entries, availableSongs, filePathMap, m3uDirectory)
                }
                else -> return Result.failure(IllegalArgumentException("Unsupported or unrecognized file format"))
            }
            
            Log.d(TAG, "Successfully imported playlist '${playlist.name}' with ${playlist.songs.size} songs")
            Result.success(playlist)
        } catch (e: RhythmBackupDetectedException) {
            // Re-throw so the ViewModel can redirect to batch import
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error importing playlist from $uri", e)
            Result.failure(e)
        }
    }
    
    private fun exportToJson(playlist: Playlist, outputFile: File) {
        val exportData = PlaylistExportData(
            name = playlist.name,
            id = playlist.id,
            dateCreated = playlist.dateCreated,
            dateModified = playlist.dateModified,
            songs = playlist.songs.map { song ->
                PlaylistSongEntry(
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    filePath = song.uri.path,
                    uri = song.uri.toString(),
                    trackNumber = song.trackNumber,
                    year = song.year
                )
            }
        )
        
        val json = GsonUtils.gson.toJson(exportData)
        outputFile.writeText(json)
    }

    private fun buildImportedPlaylist(
        name: String,
        entries: List<PlaylistSerializer.ParsedTrackEntry>,
        availableSongs: List<Song>,
        filePathMap: Map<String, Song>,
        m3uDirectory: String?
    ): Playlist {
        val matchedSongs = PlaylistSerializer.resolveEntries(
            entries = entries,
            availableSongs = availableSongs,
            extraFilePaths = filePathMap,
            baseDirectory = m3uDirectory
        )
        return Playlist(
            id = System.currentTimeMillis().toString(),
            name = name,
            songs = matchedSongs,
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis()
        )
    }

    private fun importFromJson(content: String, availableSongs: List<Song>): Playlist {
        val exportData = GsonUtils.gson.fromJson(content, PlaylistExportData::class.java)
            ?: throw IllegalArgumentException("Invalid playlist JSON format")
        
        val songsList = exportData.songs
        val playlistName = exportData.name.takeIf { !it.isNullOrBlank() } ?: "Imported Playlist"
        
        val songMap = availableSongs.associateBy { it.uri.toString() }
        val addedSongUris = mutableSetOf<String>()
        val addedSongKeys = mutableSetOf<String>()
        var skippedInvalid = false
        
        val matchedSongs = songsList.mapNotNull { entry ->
            // Gson may inject LinkedTreeMap at runtime despite compile-time type
            @Suppress("USELESS_IS_CHECK")
            if (entry !is PlaylistSongEntry) {
                if (!skippedInvalid) {
                    Log.w(TAG, "Skipping invalid song entry (wrong type: ${entry.javaClass.simpleName}) — likely a ProGuard/Gson deserialization mismatch")
                    skippedInvalid = true
                }
                return@mapNotNull null
            }
            val title = entry.title.trim()
            val artist = entry.artist.trim()
            val uriStr = entry.uri
            
            val songKey = "${title.lowercase()}_${artist.lowercase()}"
            
            if (addedSongUris.contains(uriStr) || addedSongKeys.contains(songKey)) {
                Log.d(TAG, "Skipping duplicate song: $title by $artist")
                return@mapNotNull null
            }
            
            val matchedSong = songMap[uriStr] ?: 
                availableSongs.find { 
                    it.title.equals(title, ignoreCase = true) && 
                    it.artist.equals(artist, ignoreCase = true) 
                }
            
            matchedSong?.let {
                addedSongUris.add(it.uri.toString())
                addedSongKeys.add("${it.title.trim().lowercase()}_${it.artist.trim().lowercase()}")
            }
            
            matchedSong
        }
        
        return Playlist(
            id = System.currentTimeMillis().toString(),
            name = playlistName,
            songs = matchedSongs,
            dateCreated = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis()
        )
    }

    private fun createPlaylistsZip(playlists: List<Playlist>, format: PlaylistExportFormat, zipFile: File) {
        // Create a temp directory to hold individual playlist files
        val tempDir = File(zipFile.parentFile, "temp_playlists_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Write each playlist to a temp file
            val tempFiles = mutableListOf<File>()
            playlists.forEach { playlist ->
                val fileName = sanitizeFileName("${playlist.name}${format.extension}")
                val tempFile = File(tempDir, fileName)

                when (format) {
                    PlaylistExportFormat.JSON -> exportToJson(playlist, tempFile)
                    PlaylistExportFormat.M3U -> tempFile.writeText(PlaylistSerializer.serializeM3u(playlist, false))
                    PlaylistExportFormat.M3U8 -> tempFile.writeText(PlaylistSerializer.serializeM3u(playlist, true))
                    PlaylistExportFormat.PLS -> tempFile.writeText(PlaylistSerializer.serializePls(playlist))
                    PlaylistExportFormat.XSPF -> tempFile.writeText(PlaylistSerializer.serializeXspf(playlist))
                    PlaylistExportFormat.WPL -> tempFile.writeText(PlaylistSerializer.serializeWpl(playlist))
                }

                tempFiles.add(tempFile)
            }

            // Create zip output stream and add each temp file as an entry
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                tempFiles.forEach { file ->
                    FileInputStream(file).use { fis ->
                        val entry = ZipEntry(file.name)
                        entry.time = file.lastModified()
                        zos.putNextEntry(entry)
                        fis.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }

            Log.d(TAG, "Created zip with ${playlists.size} playlists: ${zipFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating playlists zip", e)
            // If zip creation failed, ensure partial zip is removed
            try { zipFile.delete() } catch (_: Exception) {}
            throw e
        } finally {
            // Clean up temp files and directory
            try {
                tempDir.listFiles()?.forEach { it.delete() }
                tempDir.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean temp files: ${e.message}")
            }
        }
    }
    
    private fun getDefaultExportDirectory(context: Context): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Playlists")
    }
    
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
    
    internal fun sanitizeFileName(fileName: String): String {
        val sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim()
        return sanitized.ifBlank { "playlist" }
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment ?: "unknown"
    }
    
    /**
     * Export playlist to user-selected directory using Storage Access Framework
     */
    private fun exportToUserSelectedDirectory(
        context: Context,
        directoryUri: Uri,
        playlist: Playlist,
        format: PlaylistExportFormat,
        fileName: String
    ): File {
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
            ?: throw IllegalArgumentException("Cannot access selected directory")
        
        val documentFile = directory.createFile(format.mimeType, fileName)
            ?: throw IllegalArgumentException("Cannot create file in selected directory")
        
        context.contentResolver.openOutputStream(documentFile.uri)?.use { outputStream ->
            when (format) {
                PlaylistExportFormat.JSON -> {
                    val exportData = PlaylistExportData(
                        name = playlist.name,
                        id = playlist.id,
                        dateCreated = playlist.dateCreated,
                        dateModified = playlist.dateModified,
                        songs = playlist.songs.map { song ->
                            PlaylistSongEntry(
                                title = song.title,
                                artist = song.artist,
                                album = song.album,
                                duration = song.duration,
                                filePath = song.uri.path,
                                uri = song.uri.toString(),
                                trackNumber = song.trackNumber,
                                year = song.year
                            )
                        }
                    )
                    val json = GsonUtils.gson.toJson(exportData)
                    outputStream.write(json.toByteArray())
                }
                PlaylistExportFormat.M3U -> outputStream.write(PlaylistSerializer.serializeM3u(playlist, false).toByteArray(Charsets.UTF_8))
                PlaylistExportFormat.M3U8 -> outputStream.write(PlaylistSerializer.serializeM3u(playlist, true).toByteArray(Charsets.UTF_8))
                PlaylistExportFormat.PLS -> outputStream.write(PlaylistSerializer.serializePls(playlist).toByteArray(Charsets.UTF_8))
                PlaylistExportFormat.XSPF -> outputStream.write(PlaylistSerializer.serializeXspf(playlist).toByteArray(Charsets.UTF_8))
                PlaylistExportFormat.WPL -> outputStream.write(PlaylistSerializer.serializeWpl(playlist).toByteArray(Charsets.UTF_8))
            }
        } ?: throw IllegalArgumentException("Cannot open output stream for selected file")
        
        // Return a File object for compatibility (though it's not a real file path)
        return File(directory.uri.path ?: "user_selected", fileName)
    }
    
    /**
     * Export all playlists to user-selected directory using Storage Access Framework
     */
    private fun exportAllPlaylistsToUserSelectedDirectory(
        context: Context,
        directoryUri: Uri,
        playlists: List<Playlist>,
        format: PlaylistExportFormat,
        zipFileName: String
    ): File {
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
            ?: throw IllegalArgumentException("Cannot access selected directory")
        
        // For now, export as separate files instead of ZIP to user directory
        // since ZIP creation in SAF is complex
        playlists.forEach { playlist ->
            val fileName = sanitizeFileName("${playlist.name}${format.extension}")
            exportToUserSelectedDirectory(context, directoryUri, playlist, format, fileName)
        }
        
        return File(directory.uri.path ?: "user_selected", "playlists_exported")
    }
}
