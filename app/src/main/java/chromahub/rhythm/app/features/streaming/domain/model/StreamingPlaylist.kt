/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.domain.model

import chromahub.rhythm.app.core.domain.model.PlayableItem
import chromahub.rhythm.app.core.domain.model.PlaylistItem
import chromahub.rhythm.app.core.domain.model.SourceType

/**
 * Represents a playlist from a streaming service.
 */
data class StreamingPlaylist(
    override val id: String,
    override val name: String,
    override val description: String?,
    override val artworkUri: String?,
    override val songCount: Int,
    override val isEditable: Boolean,
    override val sourceType: SourceType,
    val externalId: String? = null,
    val owner: PlaylistOwner? = null,
    val isPublic: Boolean = true,
    val isCollaborative: Boolean = false,
    val followers: Long? = null,
    val snapshotId: String? = null, // For Spotify change tracking
    private val tracks: List<StreamingSong> = emptyList()
) : PlaylistItem {
    
    override suspend fun getSongs(): List<PlayableItem> = tracks
    
    /**
     * Get tracks if already loaded.
     */
    fun getTracks(): List<StreamingSong> = tracks

    fun withTracks(newTracks: List<StreamingSong>): StreamingPlaylist {
        return copy(tracks = newTracks, songCount = if (newTracks.isNotEmpty()) newTracks.size else songCount)
    }
}

/**
 * Represents the owner of a playlist.
 */
data class PlaylistOwner(
    val id: String,
    val displayName: String,
    val imageUrl: String? = null,
    val isCurrentUser: Boolean = false
)
