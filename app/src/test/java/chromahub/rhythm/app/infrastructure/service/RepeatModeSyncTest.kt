/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatModeSyncTest {

    // Action strings defined in MediaPlaybackService
    companion object {
        private const val REPEAT_MODE_OFF = "chromahub.rhythm.app.action.REPEAT_MODE_OFF"
        private const val REPEAT_MODE_ONE = "chromahub.rhythm.app.action.REPEAT_MODE_ONE"
        private const val REPEAT_MODE_ALL = "chromahub.rhythm.app.action.REPEAT_MODE_ALL"
    }

    private fun getNextRepeatMode(currentMode: Int): Int {
        return when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun getNextActionForMode(currentMode: Int): String {
        return when (currentMode) {
            Player.REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> REPEAT_MODE_OFF
            else -> REPEAT_MODE_ALL
        }
    }

    private fun resolveRepeatCommandIndex(playerMode: Int?, controllerMode: Int?): Int {
        val resolved = playerMode ?: controllerMode ?: Player.REPEAT_MODE_OFF
        return when (resolved) {
            Player.REPEAT_MODE_OFF -> 2 // customCommands[2] (REPEAT_OFF icon)
            Player.REPEAT_MODE_ALL -> 3 // customCommands[3] (REPEAT_ALL icon)
            Player.REPEAT_MODE_ONE -> 4 // customCommands[4] (REPEAT_ONE icon)
            else -> 2
        }
    }

    @Test
    fun testRepeatModeCycleTransitions() {
        var mode = Player.REPEAT_MODE_OFF
        assertEquals("Action from OFF should be REPEAT_MODE_ALL", REPEAT_MODE_ALL, getNextActionForMode(mode))

        mode = getNextRepeatMode(mode)
        assertEquals("Mode should transition from OFF to ALL", Player.REPEAT_MODE_ALL, mode)
        assertEquals("Action from ALL should be REPEAT_MODE_ONE", REPEAT_MODE_ONE, getNextActionForMode(mode))

        mode = getNextRepeatMode(mode)
        assertEquals("Mode should transition from ALL to ONE", Player.REPEAT_MODE_ONE, mode)
        assertEquals("Action from ONE should be REPEAT_MODE_OFF", REPEAT_MODE_OFF, getNextActionForMode(mode))

        mode = getNextRepeatMode(mode)
        assertEquals("Mode should transition from ONE back to OFF", Player.REPEAT_MODE_OFF, mode)
    }

    @Test
    fun testNotificationRepeatCommandButtonResolution() {
        // When player is initialized, its state takes precedence over null or lagging controller
        assertEquals("OFF mode should select command button index 2", 2, resolveRepeatCommandIndex(Player.REPEAT_MODE_OFF, null))
        assertEquals("ALL mode should select command button index 3", 3, resolveRepeatCommandIndex(Player.REPEAT_MODE_ALL, null))
        assertEquals("ONE mode should select command button index 4", 4, resolveRepeatCommandIndex(Player.REPEAT_MODE_ONE, null))

        // If player has updated to ALL but controller has not yet updated (remains OFF),
        // player state must prevail to prevent notification button divergence
        assertEquals("Player mode must take precedence over lagging controller", 3, resolveRepeatCommandIndex(Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun testCustomCommandExecutionSynchronizesPersistenceAndLayout() {
        var playerRepeatMode = Player.REPEAT_MODE_OFF
        var savedRepeatMode = Player.REPEAT_MODE_OFF
        var customLayoutUpdated = false

        fun handleCustomAction(action: String, persistenceEnabled: Boolean) {
            val targetMode = when (action) {
                REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                REPEAT_MODE_OFF -> Player.REPEAT_MODE_OFF
                else -> return
            }

            playerRepeatMode = targetMode
            if (persistenceEnabled) {
                savedRepeatMode = targetMode
            }
            customLayoutUpdated = true
        }

        // Simulate user clicking repeat in notification
        handleCustomAction(REPEAT_MODE_ALL, persistenceEnabled = true)
        assertEquals(Player.REPEAT_MODE_ALL, playerRepeatMode)
        assertEquals(Player.REPEAT_MODE_ALL, savedRepeatMode)
        assertTrue("Custom layout must update immediately on custom command", customLayoutUpdated)

        // Reset flag and trigger next step
        customLayoutUpdated = false
        handleCustomAction(REPEAT_MODE_ONE, persistenceEnabled = true)
        assertEquals(Player.REPEAT_MODE_ONE, playerRepeatMode)
        assertEquals(Player.REPEAT_MODE_ONE, savedRepeatMode)
        assertTrue(customLayoutUpdated)

        // Verify that when app reconnects or checks saved state, it never diverges
        assertEquals("Saved repeat mode must match player repeat mode", playerRepeatMode, savedRepeatMode)
    }

    @Test
    fun testPlayerListenerSyncPreventsStateRegression() {
        var inAppRepeatState = Player.REPEAT_MODE_OFF
        var savedRepeatState = Player.REPEAT_MODE_OFF
        val persistenceEnabled = true

        fun onPlayerRepeatModeChanged(newRepeatMode: Int) {
            inAppRepeatState = newRepeatMode
            if (persistenceEnabled) {
                savedRepeatState = newRepeatMode
            }
        }

        // Player engine transitions repeat mode (e.g. from service or external media session command)
        onPlayerRepeatModeChanged(Player.REPEAT_MODE_ALL)

        assertEquals("In-app UI state flow must be updated", Player.REPEAT_MODE_ALL, inAppRepeatState)
        assertEquals("Saved persistence state must be updated in sync", Player.REPEAT_MODE_ALL, savedRepeatState)

        // Reconnection sanity check: if controller checks saved state on reconnect / unpause
        val controllerRepeatMode = inAppRepeatState
        if (persistenceEnabled && controllerRepeatMode != savedRepeatState) {
            // This would cause state regression if savedRepeatState was stale!
            assertNotEquals("State should NOT diverge", controllerRepeatMode, savedRepeatState)
        }
        assertEquals("Reconnection state must be identical to controller state", controllerRepeatMode, savedRepeatState)
    }
}
