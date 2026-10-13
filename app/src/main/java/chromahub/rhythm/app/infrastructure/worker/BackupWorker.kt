/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.worker

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import chromahub.rhythm.app.shared.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Worker that performs automatic weekly backups of app settings and data
 */
class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "BackupWorker"
        const val WORK_NAME = "auto_backup_work"
        private const val BACKUP_FOLDER = "RhythmBackups"
        private const val MAX_AUTO_BACKUPS = 4 // Keep last 4 weekly backups
        private const val MAX_BACKUP_AGE_DAYS = 30 // Delete backups older than 30 days
    }
    
    /**
     * Get the public backup directory in Documents
     */
    private fun getBackupDirectory(): File {
        // Use public Documents directory so users can access backups
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documentsDir, BACKUP_FOLDER)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting automatic backup...")
            
            val appSettings = AppSettings.getInstance(applicationContext)
            
            // Check if auto-backup is still enabled
            if (!appSettings.autoBackupEnabled.value) {
                Log.d(TAG, "Auto-backup is disabled, skipping...")
                return@withContext Result.success()
            }

            // Create backup JSON
            val backupJson = appSettings.createBackup()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val directory = File(applicationContext.filesDir, "music_metadata/settings").apply { mkdirs() }
            val file = File(directory, "auto_backup_$timestamp.json")
            chromahub.rhythm.app.features.local.data.metadata.PersonalArchive.atomicWrite(file, backupJson.toByteArray())
            val archive = chromahub.rhythm.app.features.local.data.metadata.PersonalArchive(applicationContext)
            archive.sync()
            appSettings.setLastBackupTimestamp(System.currentTimeMillis())
            appSettings.setBackupLocation(archive.tree ?: "Documents/RhythmBackups/T2Rhythm/music_metadata/settings/${file.name}")
            // Personal metadata and listening journals are never subject to the legacy 30-day cleanup.

            Log.d(TAG, "Automatic backup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed: ${e.message}", e)
            e.printStackTrace()
            Result.retry()
        }
    }
    
    /**
     * Remove old auto-backups based on count and age
     */
    private fun cleanupOldBackups(backupDir: File) {
        try {
            val backupFiles = backupDir.listFiles { file ->
                file.name.startsWith("auto_backup_") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() } ?: return
            
            val currentTime = System.currentTimeMillis()
            val maxAgeMillis = MAX_BACKUP_AGE_DAYS * 24 * 60 * 60 * 1000L
            
            var deletedCount = 0
            
            // Delete backups older than MAX_BACKUP_AGE_DAYS
            backupFiles.forEach { file ->
                val fileAge = currentTime - file.lastModified()
                if (fileAge > maxAgeMillis) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old backup (>$MAX_BACKUP_AGE_DAYS days): ${file.name}")
                        deletedCount++
                    }
                }
            }
            
            // Also delete backups beyond the count limit (keep most recent ones)
            val remainingFiles = backupDir.listFiles { file ->
                file.name.startsWith("auto_backup_") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() } ?: return
            
            if (remainingFiles.size > MAX_AUTO_BACKUPS) {
                remainingFiles.drop(MAX_AUTO_BACKUPS).forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old backup (beyond count limit): ${file.name}")
                        deletedCount++
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "Cleanup complete: deleted $deletedCount old backup(s)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old backups: ${e.message}", e)
        }
    }
}
