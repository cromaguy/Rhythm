/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi

/**
 * Helper for transferring playback state between players.
 * Useful for casting, multi-device playback, or switching audio outputs.
 */
@OptIn(UnstableApi::class)
class PlayerStateTransfer {
    
    @Deprecated(
        message = "Use androidx.media3.common.PlayerTransferState directly",
        replaceWith = ReplaceWith("PlayerTransferState", "androidx.media3.common.PlayerTransferState")
    )
    typealias SavedPlayerState = PlayerTransferState
    
    companion object {
        private const val TAG = "PlayerStateTransfer"
        
        /**
         * Save the current player state for transfer.
         * @param player The player to save state from
         * @return PlayerTransferState containing all necessary state info
         */
        fun savePlayerState(player: Player): PlayerTransferState {
            Log.d(TAG, "Saving player state")
            return PlayerTransferState.fromPlayer(player)
        }
        
        /**
         * Restore player state from a saved transfer state.
         * @param player The player to restore state to
         * @param savedState The saved state
         */
        fun restorePlayerState(player: Player, savedState: PlayerTransferState) {
            Log.d(TAG, "Restoring player state")
            savedState.setToPlayer(player)
            player.prepare()
        }
        
        /**
         * Transfer playback from one player to another seamlessly.
         * Example: Switching from local to Cast player
         * @param fromPlayer Source player
         * @param toPlayer Destination player
         */
        fun transferPlayback(fromPlayer: Player, toPlayer: Player) {
            Log.d(TAG, "Transferring playback from ${fromPlayer.javaClass.simpleName} to ${toPlayer.javaClass.simpleName}")
            
            try {
                val savedState = savePlayerState(fromPlayer)
                fromPlayer.pause()
                restorePlayerState(toPlayer, savedState)
                Log.d(TAG, "Playback transfer completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error transferring playback", e)
            }
        }
    }
}
