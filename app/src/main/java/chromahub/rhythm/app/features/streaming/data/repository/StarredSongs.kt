/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong

/**
 * Merges provider-starred songs with local liked song collections.
 */
internal object StarredSongs {

    /**
     * The liked ids after a starred-list refresh for the service with [servicePrefix]: ids of other
     * services keep their order, then the service's ids follow the provider's starred order.
     */
    fun mergeLikedIds(
        likedIds: Collection<String>,
        servicePrefix: String,
        starred: List<StreamingSong>
    ): LinkedHashSet<String> {
        val merged = LinkedHashSet<String>()
        likedIds.filterTo(merged) { !it.startsWith(servicePrefix) }
        starred.mapTo(merged) { it.id }
        return merged
    }

    /**
     * The liked songs for display: each liked id resolved from the synced catalog first, then from
     * the provider's starred songs. Ids found in neither are left out.
     */
    fun likedSongs(
        likedIds: Collection<String>,
        catalog: Map<String, StreamingSong>,
        starred: Map<String, StreamingSong>
    ): List<StreamingSong> = likedIds.mapNotNull { id -> catalog[id] ?: starred[id] }
}
