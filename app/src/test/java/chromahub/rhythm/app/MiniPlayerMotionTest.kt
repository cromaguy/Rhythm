/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app

import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayerMotion
import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayerMotionTokens
import chromahub.rhythm.app.shared.presentation.components.player.TrackChangeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MiniPlayerMotionTest {

    @Test
    fun testMaterial3MotionTokensMatchSpec() {
        assertEquals(0.9f, MiniPlayerMotionTokens.StandardSpatialDamping, 0.001f)
        assertEquals(700.0f, MiniPlayerMotionTokens.StandardSpatialStiffness, 0.001f)
        assertEquals(0.9f, MiniPlayerMotionTokens.StandardFastSpatialDamping, 0.001f)
        assertEquals(1400.0f, MiniPlayerMotionTokens.StandardFastSpatialStiffness, 0.001f)
        assertEquals(0.9f, MiniPlayerMotionTokens.StandardSlowSpatialDamping, 0.001f)
        assertEquals(300.0f, MiniPlayerMotionTokens.StandardSlowSpatialStiffness, 0.001f)

        assertEquals(0.8f, MiniPlayerMotionTokens.ExpressiveSpatialDamping, 0.001f)
        assertEquals(380.0f, MiniPlayerMotionTokens.ExpressiveSpatialStiffness, 0.001f)
        assertEquals(0.6f, MiniPlayerMotionTokens.ExpressiveFastSpatialDamping, 0.001f)
        assertEquals(800.0f, MiniPlayerMotionTokens.ExpressiveFastSpatialStiffness, 0.001f)
        assertEquals(0.8f, MiniPlayerMotionTokens.ExpressiveSlowSpatialDamping, 0.001f)
        assertEquals(200.0f, MiniPlayerMotionTokens.ExpressiveSlowSpatialStiffness, 0.001f)

        assertEquals(50, MiniPlayerMotionTokens.DurationShort1)
        assertEquals(100, MiniPlayerMotionTokens.DurationShort2)
        assertEquals(150, MiniPlayerMotionTokens.DurationShort3)
        assertEquals(200, MiniPlayerMotionTokens.DurationShort4)
        assertEquals(250, MiniPlayerMotionTokens.DurationMedium1)
        assertEquals(300, MiniPlayerMotionTokens.DurationMedium2)
        assertEquals(500, MiniPlayerMotionTokens.DurationLong2)
    }

    @Test
    fun testTrackChangeDirectionEnum() {
        assertEquals(TrackChangeDirection.FORWARD, TrackChangeDirection.valueOf("FORWARD"))
        assertEquals(TrackChangeDirection.BACKWARD, TrackChangeDirection.valueOf("BACKWARD"))
        assertEquals(TrackChangeDirection.NONE, TrackChangeDirection.valueOf("NONE"))
    }

    @Test
    fun testCreateTrackTransitions() {
        val forwardExpressive = MiniPlayerMotion.createTrackTransition(TrackChangeDirection.FORWARD, isExpressive = true)
        val backwardExpressive = MiniPlayerMotion.createTrackTransition(TrackChangeDirection.BACKWARD, isExpressive = true)
        val forwardStandard = MiniPlayerMotion.createTrackTransition(TrackChangeDirection.FORWARD, isExpressive = false)
        val noneStandard = MiniPlayerMotion.createTrackTransition(TrackChangeDirection.NONE, isExpressive = false)

        assertNotNull(forwardExpressive)
        assertNotNull(backwardExpressive)
        assertNotNull(forwardStandard)
        assertNotNull(noneStandard)
    }
}
