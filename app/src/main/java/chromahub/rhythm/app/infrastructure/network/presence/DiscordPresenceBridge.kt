/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network.presence

import android.content.Context

data class DiscordPresenceTrackState(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val codecLabel: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null
)

interface DiscordPresenceController {
    val isSupported: Boolean
    fun onPlaybackUpdated(enabled: Boolean, applicationId: String?, state: DiscordPresenceTrackState?)
    fun clear()
}

object DiscordPresenceFactory {
    fun isSupported(): Boolean = DiscordPresencePlatform.isSupported
    fun create(context: Context): DiscordPresenceController = DiscordPresencePlatform.create(context)
}
