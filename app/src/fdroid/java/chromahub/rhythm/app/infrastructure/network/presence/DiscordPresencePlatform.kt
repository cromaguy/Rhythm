/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network.presence

import android.content.Context

internal object DiscordPresencePlatform {
    val isSupported: Boolean = false

    fun create(context: Context): DiscordPresenceController = NoOpController

    private object NoOpController : DiscordPresenceController {
        override val isSupported: Boolean = false
        override fun onPlaybackUpdated(enabled: Boolean, applicationId: String?, state: DiscordPresenceTrackState?) = Unit
        override fun clear() = Unit
    }
}
