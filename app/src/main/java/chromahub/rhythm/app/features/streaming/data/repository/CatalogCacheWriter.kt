/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.io.Writer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Writes catalog cache files atomically and sequentially via a temporary file.
 */
internal class CatalogCacheWriter {

    private val mutex = Mutex()

    /**
     * Takes a [snapshot] and streams it to [file] with [serialize]; writes nothing if the
     * snapshot is null.
     * @return true if the file was written.
     */
    suspend fun <T : Any> write(
        file: File,
        snapshot: () -> T?,
        serialize: (T, Writer) -> Unit
    ): Boolean = mutex.withLock {
        val content = snapshot() ?: return@withLock false
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            tempFile.bufferedWriter().use { serialize(content, it) }
            moveReplacing(tempFile, file)
        } catch (e: IOException) {
            tempFile.delete()
            throw e
        }
        true
    }

    /** Deletes [file], after any save in progress, so a queued save cannot race with it. */
    suspend fun delete(file: File): Boolean = mutex.withLock { file.delete() }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Merges bursts of save requests: the first request for a key schedules one save after
 * [delayMs], and requests for the same key made until it runs are folded into it. The save
 * snapshots the state when it runs, so it writes the latest state.
 */
internal class SaveCoalescer(private val scope: CoroutineScope, private val delayMs: Long) {

    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun request(key: String, save: suspend () -> Unit) {
        if (!pending.add(key)) return
        scope.launch {
            delay(delayMs)
            pending.remove(key)
            save()
        }
    }
}
