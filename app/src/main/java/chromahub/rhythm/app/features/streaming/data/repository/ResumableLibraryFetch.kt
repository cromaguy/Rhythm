/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import android.util.Log
import chromahub.rhythm.app.features.streaming.data.provider.ProviderSong
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Index of an interrupted full library fetch: every album page before [nextAlbumOffset] is
 * stored in its own page file next to it.
 *
 * @param lastModified server library `lastModified` when the fetch started; the checkpoint is
 *        only resumed while the server still reports the same value.
 * @param nextAlbumOffset album-list offset of the first page not fetched yet.
 */
data class LibraryFetchCheckpoint(
    val lastModified: Long,
    val nextAlbumOffset: Int
)

/**
 * Executes a resumable paged library fetch that persists progress across process death.
 * Writes fetched album pages sequentially and cleans up checkpoints upon completion.
 */
internal class ResumableLibraryFetch(
    private val checkpointDir: File,
    private val writer: CatalogCacheWriter,
    private val gson: Gson = Gson(),
    private val minProgressIntervalMs: Long = 500L,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** One paged fetch starting at `startAlbumOffset`, reporting each page to `onPage`. */
    fun interface PagedFetch {
        suspend fun fetch(
            startAlbumOffset: Int,
            limit: Int,
            onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)?,
            onPage: suspend (pageSongs: List<ProviderSong>, nextAlbumOffset: Int) -> Unit
        ): Result<List<ProviderSong>>
    }

    private val indexFile = File(checkpointDir, "index.json")

    /**
     * @param lastModified the server's current library `lastModified`, or null if unknown; then
     *        nothing is checkpointed or resumed and this is a plain full fetch.
     */
    suspend fun fetch(
        lastModified: Long?,
        limit: Int,
        onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)?,
        pagedFetch: PagedFetch
    ): Result<List<ProviderSong>> {
        val fetched = LinkedHashMap<String, ProviderSong>()
        val checkpoint = lastModified?.let { readCheckpoint(it, fetched) }
        if (checkpoint != null) {
            Log.d(TAG, "Resuming library fetch at album ${checkpoint.nextAlbumOffset} with ${fetched.size} songs")
        } else {
            fetched.clear()
            discard(checkpointDir)
        }
        val resumedCount = fetched.size
        if (resumedCount >= limit) {
            discard(checkpointDir)
            return Result.success(fetched.values.take(limit))
        }

        var pageStart = checkpoint?.nextAlbumOffset ?: 0
        var lastProgressAt = Long.MIN_VALUE
        val result = pagedFetch.fetch(
            startAlbumOffset = pageStart,
            limit = limit - resumedCount,
            onProgress = onProgress?.let { report ->
                { current, total, songsCount ->
                    // Progress drives UI state and a notification; at most a few updates a second.
                    val now = clock()
                    if (lastProgressAt == Long.MIN_VALUE || now - lastProgressAt >= minProgressIntervalMs) {
                        lastProgressAt = now
                        report(current, total, songsCount + resumedCount)
                    }
                }
            }
        ) { pageSongs, nextAlbumOffset ->
            pageSongs.forEach { fetched.putIfAbsent(it.providerId, it) }
            if (lastModified != null) {
                checkpointDir.mkdirs()
                // The page first, then the index that makes it count: a kill in between only
                // leaves a page that is fetched (and overwritten) again.
                writer.write(pageFile(pageStart), { pageSongs }) { songs, out -> gson.toJson(songs, out) }
                val index = LibraryFetchCheckpoint(lastModified, nextAlbumOffset)
                writer.write(indexFile, { index }) { value, out -> gson.toJson(value, out) }
            }
            pageStart = nextAlbumOffset
        }

        // On failure the checkpoint stays, so the next sync continues from the last page.
        return result.map { songs ->
            songs.forEach { fetched.putIfAbsent(it.providerId, it) }
            discard(checkpointDir)
            fetched.values.take(limit)
        }
    }

    private fun pageFile(albumOffset: Int) = File(checkpointDir, "page-%08d.json".format(albumOffset))

    /** Reads a matching checkpoint's pages into [into]; null (and [into] unusable) otherwise. */
    private fun readCheckpoint(lastModified: Long, into: LinkedHashMap<String, ProviderSong>): LibraryFetchCheckpoint? {
        if (!indexFile.exists()) return null
        return try {
            val index = indexFile.bufferedReader().use { gson.fromJson(it, LibraryFetchCheckpoint::class.java) }
                ?.takeIf { it.lastModified == lastModified && it.nextAlbumOffset > 0 }
                ?: return null
            val songListType = object : TypeToken<List<ProviderSong>>() {}.type
            val pages = checkpointDir.listFiles { file -> PAGE_NAME.matches(file.name) }.orEmpty()
                .map { PAGE_NAME.matchEntire(it.name)!!.groupValues[1].toInt() to it }
                .filter { (offset, _) -> offset < index.nextAlbumOffset }
                .sortedBy { (offset, _) -> offset }
            for ((_, file) in pages) {
                val songs: List<ProviderSong> = file.bufferedReader().use { gson.fromJson(it, songListType) }
                // Gson leaves missing fields null despite the Kotlin types; treat that as corrupt.
                songs.forEach { song -> into.putIfAbsent(song.providerId.also { id -> require(id.isNotEmpty()) }, song) }
            }
            index
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unreadable library fetch checkpoint", e)
            null
        }
    }

    companion object {
        private const val TAG = "ResumableLibraryFetch"
        private val PAGE_NAME = Regex("""page-(\d+)\.json""")

        /** Deletes a checkpoint (e.g. on logout or a new login). */
        fun discard(checkpointDir: File) {
            if (checkpointDir.exists()) checkpointDir.deleteRecursively()
        }
    }
}
