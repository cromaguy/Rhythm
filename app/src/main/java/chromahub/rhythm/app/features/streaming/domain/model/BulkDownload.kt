/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.domain.model

/**
 * Progress of a "Download all" run over one playlist or album.
 *
 * [targetId] is the playlist or album id the run belongs to, so a detail screen shows progress only
 * for its own item. [completed] and [failed] count finished tracks; the run ends when they add up
 * to [total].
 */
data class BulkDownloadProgress(
    val targetId: String,
    val label: String,
    val total: Int,
    val completed: Int = 0,
    val failed: Int = 0
) {
    val finished: Int get() = completed + failed
    val isDone: Boolean get() = finished >= total
}

/**
 * Decides which tracks a bulk download still has to fetch. Pure, so it can be unit tested without
 * the ViewModel: duplicates inside the list are collapsed, and tracks that are already downloaded
 * or currently downloading are left out, in the playlist's own order.
 */
object BulkDownloadPlanner {
    fun pending(
        songs: List<StreamingSong>,
        downloadedIds: Set<String>,
        downloadingIds: Set<String>
    ): List<StreamingSong> =
        songs.distinctBy { it.id }
            .filter { it.id !in downloadedIds && it.id !in downloadingIds }
}
