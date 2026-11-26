/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import chromahub.rhythm.app.infrastructure.service.player.AudioCacheManager
import java.io.File

/**
 * Crash-safe wrapper around audio metadata write-backs to MediaStore URIs.
 *
 * Verifies the staged modified audio file retains a valid container header before touching the
 * target file, snapshots the original file into a bounded rollback buffer when possible, and
 * rolls back automatically if writing fails partway through.
 */
object SongMetadataWriteBack {

    private const val TAG = "SongMetadataWriteBack"
    private const val MAX_ROLLBACK_BYTES = 64L * 1024L * 1024L

    fun verifyStagedAudioFile(tempFile: File): Boolean {
        if (!tempFile.exists() || tempFile.length() < 16L) return false
        return runCatching {
            tempFile.inputStream().use { stream ->
                val head = ByteArray(16)
                val read = stream.read(head)
                read >= 4 && AudioHeaderInspector.hasRecognizedContainerMagic(head.copyOf(read))
            }
        }.getOrDefault(false)
    }

    fun writeVerifiedTempFile(
        context: Context,
        contentResolver: ContentResolver,
        targetUri: Uri,
        tempFile: File
    ): Boolean {
        if (!verifyStagedAudioFile(tempFile)) {
            Log.e(TAG, "Refusing metadata write-back: staged temp file failed header verification")
            return false
        }

        val rollbackFile = createRollbackBackup(context, contentResolver, targetUri)
        val written = try {
            MediaUtils.writeTempFileToContentUri(contentResolver, targetUri, tempFile)
        } catch (se: SecurityException) {
            rollbackFile?.delete()
            throw se
        } catch (e: Exception) {
            Log.e(TAG, "Write-back threw exception; attempting rollback", e)
            rollbackFile?.let { restoreRollback(contentResolver, targetUri, it) }
            rollbackFile?.delete()
            return false
        }

        if (!written) {
            rollbackFile?.let { restoreRollback(contentResolver, targetUri, it) }
        } else {
            AudioCacheManager.evict(targetUri)
        }
        rollbackFile?.delete()
        return written
    }

    private fun createRollbackBackup(context: Context, contentResolver: ContentResolver, targetUri: Uri): File? {
        return runCatching {
            val size = contentResolver.openAssetFileDescriptor(targetUri, "r")?.use { it.length } ?: -1L
            if (size <= 0L || size > MAX_ROLLBACK_BYTES) return null
            val rollbackDir = File(context.cacheDir, "tag_writeback_rollback").apply { mkdirs() }
            val backup = File.createTempFile("rollback_", ".bin", rollbackDir)
            contentResolver.openInputStream(targetUri)?.use { input ->
                backup.outputStream().use { output -> input.copyTo(output) }
            }
            if (backup.length() > 0L) backup else null.also { backup.delete() }
        }.getOrNull()
    }

    private fun restoreRollback(contentResolver: ContentResolver, targetUri: Uri, backup: File) {
        if (!backup.exists() || backup.length() == 0L) return
        runCatching {
            MediaUtils.writeTempFileToContentUri(contentResolver, targetUri, backup)
        }.onFailure { e ->
            Log.e(TAG, "Rollback restoration failed for $targetUri", e)
        }
    }
}
