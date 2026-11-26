/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistEntity
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistSongEntity
import chromahub.rhythm.app.features.local.data.database.entity.toSong
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.PlaylistImportExportUtils
import chromahub.rhythm.app.util.PlaylistSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Periodically mirrors local playlists as `.m3u8` files in a user-selected SAF directory and
 * imports external `.m3u`/`.m3u8` additions or newer external edits back into Room when
 * continuous sync is configured in [AppSettings.playlistSyncUri].
 */
class M3uSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val syncUriRaw = AppSettings.getInstance(applicationContext).playlistSyncUri.value
            ?.takeIf { it.isNotBlank() } ?: return@withContext Result.success()

        return@withContext try {
            val treeUri = Uri.parse(syncUriRaw)
            val rootDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
            if (rootDir == null || !rootDir.canRead() || !rootDir.canWrite()) {
                Log.w(TAG, "Configured SAF M3U8 sync directory is missing read/write access")
                return@withContext Result.success()
            }

            val db = RhythmDatabase.getInstance(applicationContext)
            val playlistDao = db.playlistDao()
            val playlists = playlistDao.getAllPlaylists()
            var cachedSongs: List<Song>? = null
            suspend fun getAvailableSongs(): List<Song> {
                return cachedSongs ?: db.songDao().getAllSongs().map { it.toSong() }.also {
                    cachedSongs = it
                }
            }

            val remoteDocsByKey = HashMap<String, DocumentFile>()
            rootDir.listFiles().forEach { doc ->
                val rawName = doc.name ?: return@forEach
                if (!doc.isFile) return@forEach
                if (rawName.endsWith(".m3u8", ignoreCase = true) || rawName.endsWith(".m3u", ignoreCase = true)) {
                    val stem = rawName.substringBeforeLast('.')
                    val key = normalizeSyncKey(stem)
                    val existing = remoteDocsByKey[key]
                    if (existing == null || rawName.endsWith(".m3u8", ignoreCase = true)) {
                        remoteDocsByKey[key] = doc
                    }
                }
            }

            val matchedRemoteKeys = HashSet<String>()

            playlists.forEach { entity ->
                val sanitizedBase = PlaylistImportExportUtils.sanitizeFileName(entity.name)
                val targetName = "$sanitizedBase.m3u8"
                val key = normalizeSyncKey(sanitizedBase)
                val existingDoc = rootDir.findFile(targetName) ?: remoteDocsByKey[key]
                matchedRemoteKeys += key

                val action = decideSyncAction(
                    localModifiedMs = entity.dateModified,
                    remoteModifiedMs = existingDoc?.lastModified() ?: 0L,
                    remoteExists = existingDoc != null
                )

                when (action) {
                    M3uSyncAction.IMPORT_FROM_DOCUMENT -> {
                        val doc = existingDoc ?: return@forEach
                        val content = readDocumentText(doc) ?: return@forEach
                        val parsed = PlaylistSerializer.parseM3u(content)
                        val resolved = PlaylistSerializer.resolveEntries(parsed.entries, getAvailableSongs())
                        if (resolved.isNotEmpty() || parsed.entries.isEmpty()) {
                            val entries = resolved.mapIndexed { idx, song ->
                                PlaylistSongEntity(
                                    playlistId = entity.id,
                                    songId = song.id,
                                    orderIndex = idx
                                )
                            }
                            playlistDao.updatePlaylistSongs(entity.id, entries)
                            playlistDao.insertPlaylist(
                                entity.copy(
                                    dateModified = doc.lastModified().coerceAtLeast(entity.dateModified)
                                )
                            )
                        }
                    }

                    M3uSyncAction.EXPORT_TO_DOCUMENT -> {
                        val songs = playlistDao.getSongsForPlaylist(entity.id).map { it.toSong() }
                        val model = Playlist(
                            id = entity.id,
                            name = entity.name,
                            songs = songs,
                            dateCreated = entity.dateCreated,
                            dateModified = entity.dateModified,
                            artworkUri = entity.artworkUri?.toUri()
                        )
                        val content = buildString {
                            append("#EXTM3U\n")
                            append("#PLAYLIST:${model.name}\n")
                            model.songs.forEach { song ->
                                append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
                                append("${song.path?.takeIf { it.isNotBlank() } ?: song.uri}\n")
                            }
                        }
                        val docFile = existingDoc ?: rootDir.createFile("application/x-mpegURL", targetName)
                        if (docFile != null) {
                            applicationContext.contentResolver.openOutputStream(docFile.uri, "wt")?.use { out ->
                                out.write(content.toByteArray(Charsets.UTF_8))
                                out.flush()
                            }
                        }
                    }

                    M3uSyncAction.IN_SYNC -> Unit
                }
            }

            // Import brand-new external .m3u / .m3u8 files not yet tracked in Room.
            remoteDocsByKey.forEach { (key, doc) ->
                if (key in matchedRemoteKeys) return@forEach
                val rawName = doc.name ?: return@forEach
                val content = readDocumentText(doc) ?: return@forEach
                val parsed = PlaylistSerializer.parseM3u(content)
                if (parsed.entries.isEmpty()) return@forEach
                val resolved = PlaylistSerializer.resolveEntries(parsed.entries, getAvailableSongs())
                if (resolved.isEmpty()) return@forEach

                val now = doc.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                val playlistId = UUID.randomUUID().toString()
                val playlistName = parsed.name?.takeIf { it.isNotBlank() }
                    ?: rawName.substringBeforeLast('.')
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId,
                        name = playlistName,
                        dateCreated = now,
                        dateModified = now,
                        artworkUri = null
                    )
                )
                playlistDao.updatePlaylistSongs(
                    playlistId = playlistId,
                    playlistSongs = resolved.mapIndexed { idx, song ->
                        PlaylistSongEntity(
                            playlistId = playlistId,
                            songId = song.id,
                            orderIndex = idx
                        )
                    }
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SAF .m3u8 playlist sync failed", e)
            Result.retry()
        }
    }

    private fun readDocumentText(doc: DocumentFile): String? {
        return runCatching {
            applicationContext.contentResolver.openInputStream(doc.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
    }

    enum class M3uSyncAction {
        EXPORT_TO_DOCUMENT,
        IMPORT_FROM_DOCUMENT,
        IN_SYNC
    }

    companion object {
        private const val TAG = "M3uSyncWorker"
        private const val UNIQUE_WORK_NAME = "rhythm_m3u8_saf_sync"
        internal const val CLOCK_SKEW_TOLERANCE_MS = 2000L

        internal fun normalizeSyncKey(name: String): String {
            return PlaylistImportExportUtils.sanitizeFileName(name).trim().lowercase()
        }

        internal fun decideSyncAction(
            localModifiedMs: Long,
            remoteModifiedMs: Long,
            remoteExists: Boolean
        ): M3uSyncAction {
            if (!remoteExists || remoteModifiedMs <= 0L) {
                return M3uSyncAction.EXPORT_TO_DOCUMENT
            }
            return when {
                remoteModifiedMs > localModifiedMs + CLOCK_SKEW_TOLERANCE_MS -> M3uSyncAction.IMPORT_FROM_DOCUMENT
                localModifiedMs > remoteModifiedMs + CLOCK_SKEW_TOLERANCE_MS -> M3uSyncAction.EXPORT_TO_DOCUMENT
                else -> M3uSyncAction.IN_SYNC
            }
        }

        fun enqueue(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<M3uSyncWorker>().build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }.onFailure { e ->
                Log.w(TAG, "Unable to enqueue M3uSyncWorker", e)
            }
        }
    }
}
