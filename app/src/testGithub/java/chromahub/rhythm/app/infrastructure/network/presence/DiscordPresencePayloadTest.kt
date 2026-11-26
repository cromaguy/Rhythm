/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network.presence

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPresencePayloadTest {

    @Test
    fun `buildSetActivityFrame formats listening state and timestamps`() {
        val state = DiscordPresenceTrackState(
            title = "Everything In Its Right Place",
            artist = "Radiohead",
            album = "Kid A",
            durationMs = 251_000L,
            positionMs = 30_000L,
            isPlaying = true,
            codecLabel = "FLAC",
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        val raw = DiscordPresencePayloadBuilder.buildSetActivityFrame(
            state = state,
            pid = 4242,
            nowWallMs = 1_000_000L,
            nonce = "n-1"
        )
        val root = JSONObject(raw)
        assertEquals("SET_ACTIVITY", root.getString("cmd"))
        val activity = root.getJSONObject("args").getJSONObject("activity")
        assertEquals(2, activity.getInt("type"))
        assertEquals("Everything In Its Right Place", activity.getString("details"))
        assertEquals("Radiohead · FLAC · 24-bit · 96 kHz", activity.getString("state"))
        val ts = activity.getJSONObject("timestamps")
        assertEquals(970_000L, ts.getLong("start"))
        assertEquals(970_000L + 251_000L, ts.getLong("end"))
        assertTrue(DiscordPresencePayloadBuilder.buildClearActivityFrame(4242, "n-2").contains("\"activity\":null"))
    }
}
