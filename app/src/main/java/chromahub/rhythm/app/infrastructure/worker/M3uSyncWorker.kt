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
import chromahub.rhythm.app.features.local.data.database.entity.toSong
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.util.PlaylistImportExportUtils
import chromahub.rhythm.app.util.PlaylistSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodically mirrors local playlists as `.m3u8` files to a user-selected SAF directory
 * whenever playlists change and continuous sync is configured in [AppSettings.playlistSyncUri].
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
            if (rootDir == null || !rootDir.canWrite()) {
                Log.w(TAG, "Configured SAF M3U8 sync directory is missing write access")
                return@withContext Result.success()
            }

            val db = RhythmDatabase.getInstance(applicationContext)
            val playlistDao = db.playlistDao()
            val playlists = playlistDao.getAllPlaylists()

            playlists.forEach { entity ->
                val songs = playlistDao.getSongsForPlaylist(entity.id).map { it.toSong() }
                val model = Playlist(
                    id = entity.id,
                    name = entity.name,
                    songs = songs,
                    dateCreated = entity.dateCreated,
                    dateModified = entity.dateModified,
                    artworkUri = entity.artworkUri?.toUri()
                )
                val targetName = "${PlaylistImportExportUtils.sanitizeFileName(entity.name)}.m3u8"
                val content = PlaylistSerializer.serializeM3u(model, extended = true)
                val existingDoc = rootDir.findFile(targetName)
                val docFile = existingDoc ?: rootDir.createFile("application/x-mpegURL", targetName)
                if (docFile != null) {
                    applicationContext.contentResolver.openOutputStream(docFile.uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SAF .m3u8 playlist sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "M3uSyncWorker"
        private const val UNIQUE_WORK_NAME = "rhythm_m3u8_saf_sync"

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
