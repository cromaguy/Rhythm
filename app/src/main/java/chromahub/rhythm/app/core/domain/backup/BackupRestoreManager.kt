package chromahub.rhythm.app.core.domain.backup

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistEntity
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistSongEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.AppSettings.BackupRestoreSections
import chromahub.rhythm.app.shared.data.model.AppSettings.BackupValidationResult
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.util.GsonUtils
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Centralized, atomic Backup and Restore Manager.
 * Operates strictly asynchronously on Dispatchers.IO with full transaction safety.
 */
class BackupRestoreManager(
    private val context: Context,
    private val appSettings: AppSettings,
    private val database: RhythmDatabase
) {
    companion object {
        private const val TAG = "BackupRestoreManager"
        private const val BACKUP_VERSION = 4
    }

    /**
     * Creates a full JSON backup payload according to the selected sections.
     */
    suspend fun createBackupPayload(sections: BackupRestoreSections = BackupRestoreSections()): String = withContext(Dispatchers.IO) {
        val backupData = mutableMapOf<String, Any?>()
        val preferencesTypes = mutableMapOf<String, String>()

        val effectiveSections = if (sections.hasAtLeastOneSectionSelected) sections else BackupRestoreSections()
        val allPrefs = appSettings.prefs.all

        val filteredPrefs = allPrefs.filterKeys { key ->
            appSettings.shouldIncludeKeyInBackupSections(key, effectiveSections)
        }

        filteredPrefs.forEach { (key, value) ->
            preferencesTypes[key] = when (value) {
                is Boolean -> "Boolean"
                is Float -> "Float"
                is Int -> "Int"
                is Long -> "Long"
                is String -> "String"
                is Set<*> -> "StringSet"
                else -> "Unknown"
            }
        }

        backupData["preferences"] = filteredPrefs
        backupData["preferences_types"] = preferencesTypes
        backupData["timestamp"] = System.currentTimeMillis()
        backupData["app_version"] = "1.0.0"
        backupData["backup_version"] = BACKUP_VERSION
        backupData["selected_sections"] = mapOf(
            "general_settings" to effectiveSections.includeGeneralSettings,
            "library_data" to effectiveSections.includeLibraryData,
            "stats_rhythm_guard" to effectiveSections.includeStatsAndRhythmGuard
        )

        if (effectiveSections.includeLibraryData) {
            try {
                val playlistEntities = database.playlistDao().getAllPlaylists()
                val playlistModels = playlistEntities.map { entity ->
                    val songIds = database.playlistDao().getSongIdsForPlaylist(entity.id)
                    val songs = songIds.mapNotNull { songId ->
                        val songEntity = database.songDao().getSongById(songId)
                        if (songEntity != null) {
                            chromahub.rhythm.app.shared.data.model.Song(
                                id = songEntity.id,
                                title = songEntity.title,
                                artist = songEntity.artist,
                                album = songEntity.album,
                                albumId = songEntity.albumId,
                                duration = songEntity.duration,
                                uri = android.net.Uri.parse(songEntity.uri),
                                artworkUri = songEntity.artworkUri?.let { android.net.Uri.parse(it) },
                                trackNumber = songEntity.trackNumber,
                                year = songEntity.year,
                                genre = songEntity.genre,
                                dateAdded = songEntity.dateAdded,
                                dateModified = songEntity.dateModified,
                                albumArtist = songEntity.albumArtist,
                                bitrate = songEntity.bitrate,
                                sampleRate = songEntity.sampleRate,
                                channels = songEntity.channels,
                                codec = songEntity.codec,
                                discNumber = songEntity.discNumber,
                                path = songEntity.path
                            )
                        } else {
                            chromahub.rhythm.app.shared.data.model.Song(
                                id = songId,
                                title = "Unknown Song",
                                artist = "<unknown>",
                                album = "<unknown>",
                                albumId = "",
                                duration = 0L,
                                uri = android.net.Uri.EMPTY,
                                artworkUri = null,
                                trackNumber = 0,
                                year = 0,
                                genre = null,
                                dateAdded = System.currentTimeMillis(),
                                dateModified = System.currentTimeMillis(),
                                albumArtist = null,
                                bitrate = null,
                                sampleRate = null,
                                channels = null,
                                codec = null,
                                discNumber = 1,
                                path = null
                            )
                        }
                    }
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        songs = songs,
                        dateCreated = entity.dateCreated,
                        dateModified = entity.dateModified,
                        artworkUri = entity.artworkUri?.let { android.net.Uri.parse(it) }
                    )
                }
                backupData["playlists_data"] = GsonUtils.gson.toJson(playlistModels)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating playlist snapshot for backup", e)
            }
        }

        if (effectiveSections.includeStatsAndRhythmGuard) {
            val statsData = mutableMapOf<String, Any?>()
            val statsTypes = mutableMapOf<String, String>()
            allPrefs.filterKeys { appSettings.isStatsAndRhythmGuardBackupKey(it) }.forEach { (k, v) ->
                statsData[k] = v
                statsTypes[k] = when (v) {
                    is Boolean -> "Boolean"
                    is Float -> "Float"
                    is Int -> "Int"
                    is Long -> "Long"
                    is String -> "String"
                    is Set<*> -> "StringSet"
                    else -> "Unknown"
                }
            }
            backupData["stats_rhythm_guard_data"] = statsData
            backupData["stats_rhythm_guard_types"] = statsTypes

            try {
                val historyFile = File(context.filesDir, "playback_history.json")
                if (historyFile.exists()) {
                    backupData["playback_history"] = historyFile.readText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up playback_history.json", e)
            }
        }

        GsonUtils.gson.toJson(backupData)
    }

    /**
     * Restores app state, settings, database, and stats atomically from JSON.
     */
    suspend fun restoreFromBackupPayload(
        backupJson: String,
        sections: BackupRestoreSections = BackupRestoreSections()
    ): Boolean = withContext(Dispatchers.IO) {
        val validation = appSettings.validateBackupJson(backupJson, sections)
        if (validation is BackupValidationResult.Invalid) {
            Log.e(TAG, "Restore rejected due to invalid backup payload: ${validation.reason}")
            return@withContext false
        }

        return@withContext try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val backupData = GsonUtils.gson.fromJson<Map<String, Any?>>(backupJson, type) ?: return@withContext false
            val preferences = (backupData["preferences"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
                ?.toMap() ?: return@withContext false
            val preferencesTypes = (backupData["preferences_types"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }
                ?.toMap() ?: emptyMap()

            if (!sections.hasAtLeastOneSectionSelected) {
                Log.w(TAG, "Restore skipped: no backup sections selected")
                return@withContext false
            }

            val editor = appSettings.prefs.edit()

            preferences.forEach { (key, value) ->
                if (!appSettings.shouldIncludeKeyInBackupSections(key, sections) || appSettings.isRhythmGuardTransientRuntimeKey(key)) {
                    return@forEach
                }
                val originalType = preferencesTypes[key]
                appSettings.applyBackupPreferenceValue(editor, key, value, originalType)
            }

            if (sections.includeStatsAndRhythmGuard) {
                val statsData = (backupData["stats_rhythm_guard_data"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
                    ?.toMap() ?: emptyMap()
                val statsTypes = (backupData["stats_rhythm_guard_types"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }
                    ?.toMap() ?: emptyMap()

                statsData.forEach { (key, value) ->
                    if (appSettings.isStatsAndRhythmGuardBackupKey(key) && !appSettings.isRhythmGuardTransientRuntimeKey(key)) {
                        appSettings.applyBackupPreferenceValue(editor, key, value, statsTypes[key] ?: preferencesTypes[key])
                    }
                }

                val playbackHistoryJson = backupData["playback_history"] as? String
                if (!playbackHistoryJson.isNullOrBlank()) {
                    try {
                        val historyFile = File(context.filesDir, "playback_history.json")
                        historyFile.writeText(playbackHistoryJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore playback_history.json", e)
                    }
                }
            }

            editor.apply()

            if (sections.includeLibraryData) {
                val playlistsData = backupData["playlists_data"] as? String
                if (playlistsData != null) {
                    val playlistListType = object : TypeToken<List<Playlist>>() {}.type
                    val restoredPlaylists: List<Playlist> = GsonUtils.gson.fromJson(playlistsData, playlistListType) ?: emptyList()

                    database.withTransaction {
                        database.playlistDao().deleteAllPlaylists()
                        database.playlistDao().deleteAllPlaylistSongs()

                        val playlistEntities = mutableListOf<PlaylistEntity>()
                        val songEntities = mutableListOf<SongEntity>()
                        val playlistSongEntities = mutableListOf<PlaylistSongEntity>()

                        restoredPlaylists.forEach { playlist ->
                            playlistEntities.add(
                                PlaylistEntity(
                                    id = playlist.id,
                                    name = playlist.name,
                                    dateCreated = playlist.dateCreated,
                                    dateModified = playlist.dateModified,
                                    artworkUri = playlist.artworkUri?.toString()
                                )
                            )

                            playlist.songs.forEachIndexed { index, song ->
                                songEntities.add(
                                    SongEntity(
                                        id = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        albumId = song.albumId,
                                        duration = song.duration,
                                        uri = song.uri.toString(),
                                        artworkUri = song.artworkUri?.toString(),
                                        trackNumber = song.trackNumber,
                                        year = song.year,
                                        genre = song.genre,
                                        dateAdded = song.dateAdded,
                                        dateModified = song.dateModified,
                                        albumArtist = song.albumArtist,
                                        bitrate = song.bitrate,
                                        sampleRate = song.sampleRate,
                                        channels = song.channels,
                                        codec = song.codec,
                                        discNumber = song.discNumber,
                                        path = song.path
                                    )
                                )
                                playlistSongEntities.add(
                                    PlaylistSongEntity(
                                        playlistId = playlist.id,
                                        songId = song.id,
                                        orderIndex = index
                                    )
                                )
                            }
                        }

                        database.playlistDao().insertPlaylists(playlistEntities.distinctBy { it.id })
                        database.songDao().upsertAll(songEntities.distinctBy { it.id })
                        database.playlistDao().insertPlaylistSongs(playlistSongEntities.distinctBy { it.playlistId to it.songId })
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing atomic restore", e)
            false
        }
    }
}
