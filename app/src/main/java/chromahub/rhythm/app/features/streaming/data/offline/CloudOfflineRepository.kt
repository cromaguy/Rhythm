/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.OfflineTrackEntity
import chromahub.rhythm.app.features.streaming.di.StreamingMusicModule
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.concurrent.TimeUnit

object OfflineDownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}

/**
 * Persistent WorkManager + Room [OfflineTrackEntity] coordinator for cloud offline
 * downloads (Subsonic / Jellyfin), surviving process termination and flaky networks.
 */
class CloudOfflineRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val dao by lazy { RhythmDatabase.getInstance(appContext).offlineTrackDao() }

    fun observeAll(): Flow<List<OfflineTrackEntity>> = dao.getAllFlow()

    suspend fun enqueueDownload(song: StreamingSong): Boolean {
        val entity = OfflineTrackEntity(
            songId = song.id,
            provider = song.sourceType.name,
            serverUrl = "",
            localPath = "",
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkPath = song.artworkUri,
            fileSizeBytes = 0L,
            status = OfflineDownloadStatus.QUEUED,
            progress = 0f,
            downloadedAtMs = System.currentTimeMillis(),
            errorMessage = null
        )
        dao.upsert(entity)
        return enqueueWorker(song.id)
    }

    suspend fun enqueueById(songId: String, providerName: String = ""): Boolean {
        val existing = dao.getById(songId)
        val entity = (existing ?: OfflineTrackEntity(
            songId = songId,
            provider = providerName,
            serverUrl = "",
            localPath = "",
            title = songId,
            artist = "",
            album = "",
            artworkPath = null,
            fileSizeBytes = 0L,
            status = OfflineDownloadStatus.QUEUED,
            progress = 0f,
            downloadedAtMs = System.currentTimeMillis(),
            errorMessage = null
        )).copy(
            status = OfflineDownloadStatus.QUEUED,
            progress = 0f,
            errorMessage = null
        )
        dao.upsert(entity)
        return enqueueWorker(songId)
    }

    suspend fun enqueueDownloads(songs: List<StreamingSong>) {
        if (songs.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = songs.map { song ->
            OfflineTrackEntity(
                songId = song.id,
                provider = song.sourceType.name,
                serverUrl = "",
                localPath = "",
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkPath = song.artworkUri,
                fileSizeBytes = 0L,
                status = OfflineDownloadStatus.QUEUED,
                progress = 0f,
                downloadedAtMs = now,
                errorMessage = null
            )
        }
        dao.upsertAll(entities)
        songs.forEach { enqueueWorker(it.id) }
    }

    suspend fun markFailed(songId: String, reason: String?) {
        dao.updateProgress(songId, OfflineDownloadStatus.FAILED, 0f, reason)
    }

    suspend fun markCompleted(song: StreamingSong, localFilePath: String, fileSizeBytes: Long) {
        dao.upsert(
            OfflineTrackEntity(
                songId = song.id,
                provider = song.sourceType.name,
                serverUrl = "",
                localPath = localFilePath,
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkPath = song.artworkUri,
                fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
                status = OfflineDownloadStatus.COMPLETED,
                progress = 1f,
                downloadedAtMs = System.currentTimeMillis(),
                errorMessage = null
            )
        )
    }

    suspend fun remove(songId: String) {
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName(songId))
        }
        dao.deleteById(songId)
    }

    suspend fun syncCompletedIndex(downloadedSongs: List<StreamingSong>) {
        val now = System.currentTimeMillis()
        val existingById = dao.getAll().associateBy { it.songId }
        val toUpsert = computeSyncDelta(existingById, downloadedSongs, now)
        if (toUpsert.isNotEmpty()) {
            dao.upsertAll(toUpsert)
        }
    }

    private fun enqueueWorker(songId: String): Boolean {
        return try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudOfflineDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(CloudOfflineDownloadWorker.KEY_SONG_ID to songId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG_WORK)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                uniqueWorkName(songId),
                ExistingWorkPolicy.KEEP,
                request
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager unavailable for $songId; caller may run in-process fallback", e)
            false
        }
    }

    companion object {
        private const val TAG = "CloudOfflineRepository"
        private const val TAG_WORK = "rhythm_cloud_offline_download"

        fun uniqueWorkName(songId: String): String = "cloud_dl_$songId"

        fun computeSyncDelta(
            existingById: Map<String, OfflineTrackEntity>,
            downloadedSongs: List<StreamingSong>,
            nowMs: Long = System.currentTimeMillis()
        ): List<OfflineTrackEntity> {
            return downloadedSongs.mapNotNull { song ->
                val path = song.streamingUrl.orEmpty()
                val fileSize = if (path.startsWith("file://")) {
                    runCatching { File(path.removePrefix("file://")).length() }.getOrDefault(0L)
                } else 0L
                val existing = existingById[song.id]
                if (existing?.status == OfflineDownloadStatus.COMPLETED && existing.localPath == path) {
                    null
                } else {
                    OfflineTrackEntity(
                        songId = song.id,
                        provider = song.sourceType.name,
                        serverUrl = "",
                        localPath = path,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        artworkPath = song.artworkUri,
                        fileSizeBytes = fileSize,
                        status = OfflineDownloadStatus.COMPLETED,
                        progress = 1f,
                        downloadedAtMs = existing?.downloadedAtMs ?: nowMs,
                        errorMessage = null
                    )
                }
            }
        }

        @Volatile
        private var instance: CloudOfflineRepository? = null

        fun getInstance(context: Context): CloudOfflineRepository {
            return instance ?: synchronized(this) {
                instance ?: CloudOfflineRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

class CloudOfflineDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val songId = inputData.getString(KEY_SONG_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        val dao = RhythmDatabase.getInstance(applicationContext).offlineTrackDao()
        return try {
            dao.updateProgress(songId, OfflineDownloadStatus.DOWNLOADING, 0.1f, null)
            val repository = StreamingMusicModule.provideStreamingMusicRepository(applicationContext)
            val success = repository.downloadSong(songId)
            if (success) {
                dao.updateProgress(songId, OfflineDownloadStatus.COMPLETED, 1f, null)
                Result.success()
            } else if (runAttemptCount < 2) {
                dao.updateProgress(songId, OfflineDownloadStatus.QUEUED, 0f, "Retrying download")
                Result.retry()
            } else {
                dao.updateProgress(songId, OfflineDownloadStatus.FAILED, 0f, "Download failed")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker error downloading $songId", e)
            if (runAttemptCount < 2) {
                dao.updateProgress(songId, OfflineDownloadStatus.QUEUED, 0f, e.message)
                Result.retry()
            } else {
                dao.updateProgress(songId, OfflineDownloadStatus.FAILED, 0f, e.message)
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "CloudOfflineDownloadWorker"
        const val KEY_SONG_ID = "song_id"
    }
}
