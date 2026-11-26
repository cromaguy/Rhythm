/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import java.util.IdentityHashMap

/**
 * Tracks audio decoder names keyed by player instance identity rather than
 * mutable master/auxiliary slot variables so crossfade role swaps never lose or
 * mix up codec telemetry across players.
 */
internal class PlayerTelemetryTracker<P : Any> {
    private val decoderNamesByPlayer = IdentityHashMap<P, String>()

    @Synchronized
    fun recordDecoderInitialized(player: P, decoderName: String) {
        decoderNamesByPlayer[player] = decoderName
    }

    @Synchronized
    fun getDecoderName(player: P): String = decoderNamesByPlayer[player].orEmpty()

    @Synchronized
    fun clearPlayer(player: P) {
        decoderNamesByPlayer.remove(player)
    }

    @Synchronized
    fun clearAll() {
        decoderNamesByPlayer.clear()
    }
}
