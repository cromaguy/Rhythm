/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.room.withTransaction
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.AudioBookmarkEntity
import chromahub.rhythm.app.features.local.data.database.entity.ListeningSpanEntity
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistEntity
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistSongEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEngagementEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import chromahub.rhythm.app.features.local.data.database.entity.TransitionRuleEntity
import chromahub.rhythm.app.features.local.data.database.entity.toSong
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.AppSettings.BackupRestoreSections
import chromahub.rhythm.app.shared.data.model.AppSettings.BackupValidationResult
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.GsonUtils
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Centralized, atomic v5 Backup and Restore Manager.
 *
 * Features:
 * - Backward compatible with v1–v4 plaintext JSON backups
 * - Optional portable AES-256-GCM + PBKDF2-HMAC-SHA256 passphrase encryption via [BackupCrypto]
 * - Path-traversal / Zip-Slip canonical boundary checks for embedded artist & playlist artwork
 * - 4-tier [BackupSongResolver] track matching for seamless cross-device migrations
 * - Automatic pre-restore safety snapshot with automatic & manual rollback support
 * - Modular backup of v10 Room tables (`audio_bookmarks`, `transition_rules`, `listening_spans`, `song_engagements`)
 */
class BackupRestoreManager(
    private val context: Context,
    private val appSettings: AppSettings,
    private val database: RhythmDatabase
) {
    companion object {
        private const val TAG = "BackupRestoreManager"
        private const val BACKUP_VERSION = 5
        private const val ROLLBACK_DIR = "backup_rollback"
        private const val ROLLBACK_FILE = "pre_restore_snapshot.json"
        private const val MAX_SPAN_BACKUP_ROWS = 5000
    }

    private fun rollbackSnapshotFile(): File =
        File(File(context.filesDir, ROLLBACK_DIR), ROLLBACK_FILE)

    fun hasSafetySnapshot(): Boolean = rollbackSnapshotFile().let { it.isFile && it.length() > 0L }

    suspend fun createBackupPayload(
        sections: BackupRestoreSections = BackupRestoreSections(),
        passphrase: String? = null
    ): String = withContext(Dispatchers.IO) {
        val backupData = mutableMapOf<String, Any?>()
        val preferencesTypes = mutableMapOf<String, String>()

        val effectiveSections = if (sections.hasAtLeastOneSectionSelected) sections else BackupRestoreSections()
        val allPrefs = appSettings.prefs.all

        val filteredPrefs = allPrefs.filterKeys { key ->
            appSettings.shouldIncludeKeyInBackupSections(key, effectiveSections)
        }

        filteredPrefs.forEach { (key, value) ->
            preferencesTypes[key] = inferPreferenceType(value)
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
                    val songsById = database.songDao().getSongsByIds(songIds).associateBy { it.id }
                    val songs = songIds.map { songId ->
                        songsById[songId]?.toSong() ?: placeholderSong(songId)
                    }
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        songs = songs,
                        dateCreated = entity.dateCreated,
                        dateModified = entity.dateModified,
                        artworkUri = entity.artworkUri?.let { it.toUri() }
                    )
                }
                backupData["playlists_data"] = GsonUtils.gson.toJson(playlistModels)
                backupData["custom_artist_images"] = encodeImageDirectory(File(context.filesDir, "artist_images"))
                backupData["custom_playlist_images"] = encodeImageDirectory(File(context.filesDir, "playlist_images"))

                runCatching {
                    val bookmarks = database.audioBookmarkDao().getAllBookmarks()
                    if (bookmarks.isNotEmpty()) {
                        backupData["audio_bookmarks_data"] = GsonUtils.gson.toJson(bookmarks)
                    }
                }
                runCatching {
                    val rules = database.transitionRuleDao().getAllRules()
                    if (rules.isNotEmpty()) {
                        backupData["transition_rules_data"] = GsonUtils.gson.toJson(rules)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating library snapshot for backup", e)
            }
        }

        if (effectiveSections.includeStatsAndRhythmGuard) {
            val statsData = mutableMapOf<String, Any?>()
            val statsTypes = mutableMapOf<String, String>()
            allPrefs.filterKeys { appSettings.isStatsAndRhythmGuardBackupKey(it) }.forEach { (k, v) ->
                statsData[k] = v
                statsTypes[k] = inferPreferenceType(v)
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

            runCatching {
                val engagements = database.listeningStatsDao().getAllEngagements()
                if (engagements.isNotEmpty()) {
                    backupData["song_engagements_data"] = GsonUtils.gson.toJson(engagements)
                }
            }
            runCatching {
                val spans = database.listeningStatsDao()
                    .getSpansInRange(0L, Long.MAX_VALUE)
                    .take(MAX_SPAN_BACKUP_ROWS)
                if (spans.isNotEmpty()) {
                    backupData["listening_spans_data"] = GsonUtils.gson.toJson(spans)
                }
            }
        }

        val json = GsonUtils.gson.toJson(backupData)
        if (!passphrase.isNullOrBlank()) {
            BackupCrypto.encryptPayload(json, passphrase)
        } else {
            json
        }
    }

    suspend fun restoreFromBackupPayload(
        backupJson: String,
        sections: BackupRestoreSections = BackupRestoreSections(),
        passphrase: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val decryptedJson = if (BackupCrypto.isEncryptedEnvelope(backupJson)) {
            if (passphrase.isNullOrBlank()) {
                Log.e(TAG, "Encrypted backup provided without passphrase")
                return@withContext false
            }
            try {
                BackupCrypto.decryptPayload(backupJson, passphrase)
            } catch (e: Exception) {
                Log.e(TAG, "Backup decryption failed: ${e.message}")
                return@withContext false
            }
        } else {
            backupJson
        }

        val validation = appSettings.validateBackupJson(decryptedJson, sections)
        if (validation is BackupValidationResult.Invalid) {
            Log.e(TAG, "Restore rejected due to invalid backup payload: ${validation.reason}")
            return@withContext false
        }

        if (!sections.hasAtLeastOneSectionSelected) {
            Log.w(TAG, "Restore skipped: no backup sections selected")
            return@withContext false
        }

        val snapshotTaken = runCatching {
            val snapshot = createBackupPayload(BackupRestoreSections(), passphrase = null)
            val file = rollbackSnapshotFile()
            file.parentFile?.mkdirs()
            file.writeText(snapshot)
            true
        }.getOrDefault(false)

        return@withContext applyPlainBackupJson(decryptedJson, sections).also { succeeded ->
            if (!succeeded && snapshotTaken) {
                Log.w(TAG, "Restore failed mid-flight; rolling back to pre-restore safety snapshot")
                runCatching {
                    val snapshot = rollbackSnapshotFile().readText()
                    applyPlainBackupJson(snapshot, BackupRestoreSections())
                }
            }
        }
    }

    suspend fun restoreLastSafetySnapshot(): Boolean = withContext(Dispatchers.IO) {
        val file = rollbackSnapshotFile()
        if (!file.isFile || file.length() <= 0L) return@withContext false
        val json = runCatching { file.readText() }.getOrNull() ?: return@withContext false
        applyPlainBackupJson(json, BackupRestoreSections())
    }

    private suspend fun applyPlainBackupJson(
        backupJson: String,
        sections: BackupRestoreSections
    ): Boolean {
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val backupData = GsonUtils.gson.fromJson<Map<String, Any?>>(backupJson, type) ?: return false
            val preferences = (backupData["preferences"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
                ?.toMap() ?: return false
            val preferencesTypes = (backupData["preferences_types"] as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }
                ?.toMap() ?: emptyMap()

            appSettings.prefs.edit {
                preferences.forEach { (key, value) ->
                    if (!appSettings.shouldIncludeKeyInBackupSections(key, sections) || appSettings.isRhythmGuardTransientRuntimeKey(key)) {
                        return@forEach
                    }
                    val originalType = preferencesTypes[key]
                    appSettings.applyBackupPreferenceValue(this, key, value, originalType)
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
                            appSettings.applyBackupPreferenceValue(this, key, value, statsTypes[key] ?: preferencesTypes[key])
                        }
                    }

                    val playbackHistoryJson = backupData["playback_history"] as? String
                    if (!playbackHistoryJson.isNullOrBlank()) {
                        try {
                            File(context.filesDir, "playback_history.json").writeText(playbackHistoryJson)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to restore playback_history.json", e)
                        }
                    }
                }
            }

            if (sections.includeLibraryData) {
                restoreImageDirectory(
                    File(context.filesDir, "artist_images"),
                    backupData["custom_artist_images"] as? Map<*, *>
                )
                restoreImageDirectory(
                    File(context.filesDir, "playlist_images"),
                    backupData["custom_playlist_images"] as? Map<*, *>
                )

                val playlistsData = backupData["playlists_data"] as? String
                val parsedPlaylists: List<Playlist> = if (!playlistsData.isNullOrBlank()) {
                    val playlistListType = object : TypeToken<List<Playlist>>() {}.type
                    GsonUtils.gson.fromJson(playlistsData, playlistListType) ?: emptyList()
                } else emptyList()

                val existingLibrarySongs = database.songDao().getAllSongs().map { it.toSong() }
                val (resolvedPlaylists, stats) = BackupSongResolver.resolvePlaylists(parsedPlaylists, existingLibrarySongs)
                if (parsedPlaylists.isNotEmpty()) {
                    Log.d(TAG, "4-tier restore song resolution: matched=${stats.matchedCount}, unresolved=${stats.unresolvedCount}")
                }

                val bookmarks = parseListJson<AudioBookmarkEntity>(backupData["audio_bookmarks_data"] as? String)
                val transitionRules = parseListJson<TransitionRuleEntity>(backupData["transition_rules_data"] as? String)

                database.withTransaction {
                    if (playlistsData != null) {
                        database.playlistDao().deleteAllPlaylists()
                        database.playlistDao().deleteAllPlaylistSongs()

                        val playlistEntities = mutableListOf<PlaylistEntity>()
                        val songEntities = mutableListOf<SongEntity>()
                        val playlistSongEntities = mutableListOf<PlaylistSongEntity>()

                        resolvedPlaylists.forEach { playlist ->
                            val entityArtworkUri = playlist.artworkUri?.toString()?.let { uriStr ->
                                if (uriStr.startsWith("file://")) {
                                    val cleanFileName = BackupCrypto.sanitizeSingleSegmentFilename(
                                        uriStr.substringAfterLast("/").substringBefore("?"),
                                        fallback = "playlist_${playlist.id}.jpg"
                                    )
                                    val query = if (uriStr.contains("?")) "?" + uriStr.substringAfter("?") else ""
                                    "file://" + File(context.filesDir, "playlist_images/$cleanFileName").absolutePath + query
                                } else {
                                    uriStr
                                }
                            }
                            playlistEntities.add(
                                PlaylistEntity(
                                    id = playlist.id,
                                    name = playlist.name,
                                    dateCreated = playlist.dateCreated,
                                    dateModified = playlist.dateModified,
                                    artworkUri = entityArtworkUri
                                )
                            )

                            playlist.songs.forEachIndexed { index, song ->
                                songEntities.add(song.toEntity())
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

                    bookmarks.forEach { bookmark ->
                        database.audioBookmarkDao().upsert(bookmark.copy(id = 0L))
                    }
                    transitionRules.forEach { rule ->
                        database.transitionRuleDao().upsert(rule)
                    }
                }
            }

            if (sections.includeStatsAndRhythmGuard) {
                val engagements = parseListJson<SongEngagementEntity>(backupData["song_engagements_data"] as? String)
                val spans = parseListJson<ListeningSpanEntity>(backupData["listening_spans_data"] as? String)
                if (engagements.isNotEmpty() || spans.isNotEmpty()) {
                    database.withTransaction {
                        if (engagements.isNotEmpty()) {
                            database.listeningStatsDao().upsertEngagements(engagements)
                        }
                        if (spans.isNotEmpty()) {
                            database.listeningStatsDao().insertSpans(spans.map { it.copy(id = 0L) })
                        }
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing atomic restore", e)
            false
        }
    }

    private fun inferPreferenceType(value: Any?): String = when (value) {
        is Boolean -> "Boolean"
        is Float -> "Float"
        is Int -> "Int"
        is Long -> "Long"
        is String -> "String"
        is Set<*> -> "StringSet"
        else -> "Unknown"
    }

    private fun encodeImageDirectory(dir: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!dir.exists() || !dir.isDirectory) return result
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jpg", ignoreCase = true)) {
                runCatching {
                    val bytes = file.readBytes()
                    result[file.name] = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }.onFailure { e ->
                    Log.e(TAG, "Error encoding image ${file.name}", e)
                }
            }
        }
        return result
    }

    private fun restoreImageDirectory(dir: File, encodedMap: Map<*, *>?) {
        if (encodedMap == null) return
        if (!dir.exists()) dir.mkdirs()
        encodedMap.forEach { (nameKey, base64Value) ->
            val filename = nameKey as? String ?: return@forEach
            val base64 = base64Value as? String ?: return@forEach
            val safeTarget = BackupCrypto.sanitizeChildPath(dir, filename)
            if (safeTarget == null) {
                Log.w(TAG, "Blocked unsafe image path during restore: $filename")
                return@forEach
            }
            runCatching {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                safeTarget.writeBytes(bytes)
            }.onFailure { e ->
                Log.e(TAG, "Error decoding image $filename", e)
            }
        }
    }

    private inline fun <reified T> parseListJson(raw: String?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<T>>() {}.type
            GsonUtils.gson.fromJson<List<T>>(raw, type) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private fun placeholderSong(songId: String): Song = Song(
        id = songId,
        title = "Unknown song",
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

    private fun Song.toEntity(): SongEntity = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        uri = uri.toString(),
        artworkUri = artworkUri?.toString(),
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        dateAdded = dateAdded,
        dateModified = dateModified,
        albumArtist = albumArtist,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        codec = codec,
        discNumber = discNumber,
        path = path
    )
}
