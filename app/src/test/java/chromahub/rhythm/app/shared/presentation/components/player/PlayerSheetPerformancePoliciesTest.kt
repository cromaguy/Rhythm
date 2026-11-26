/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetPerformancePoliciesTest {

    @Test
    fun compositionPolicy_staysIdleWhenNeverExpandedAndCollapsed() {
        assertFalse(
            FullPlayerCompositionPolicy.shouldComposeExpandedContent(
                expansionFraction = 0f,
                isExpanded = false,
                wasPreviouslyExpanded = false
            )
        )
        assertTrue(
            FullPlayerCompositionPolicy.shouldComposeExpandedContent(
                expansionFraction = 0.05f,
                isExpanded = false,
                wasPreviouslyExpanded = false
            )
        )
        assertTrue(
            FullPlayerCompositionPolicy.shouldComposeExpandedContent(
                expansionFraction = 0f,
                isExpanded = true,
                wasPreviouslyExpanded = false
            )
        )
    }

    @Test
    fun runtimePolicy_disablesAmbientBackdropEffectsWhileCollapsed() {
        assertFalse(
            FullPlayerRuntimePolicy.shouldRunAmbientEffects(
                backdropEnabled = true,
                isSheetVisible = false,
                swipeToDismissEnabled = false
            )
        )
        assertTrue(
            FullPlayerRuntimePolicy.shouldRunAmbientEffects(
                backdropEnabled = true,
                isSheetVisible = true,
                swipeToDismissEnabled = false
            )
        )
    }

    @Test
    fun sheetAlphaAndStaggerCurves_remainMonotonicWithinUnitInterval() {
        assertEquals(1f, FullPlayerCompositionPolicy.miniPlayerAlpha(0f), 0.0001f)
        assertEquals(0f, FullPlayerCompositionPolicy.miniPlayerAlpha(0.25f), 0.0001f)
        assertEquals(0f, FullPlayerCompositionPolicy.expandedPlayerAlpha(0.20f), 0.0001f)
        assertEquals(1f, FullPlayerCompositionPolicy.expandedPlayerAlpha(1f), 0.0001f)
        assertEquals(0f, FullPlayerCompositionPolicy.staggeredFraction(0.1f, startOffset = 0.2f), 0.0001f)
        assertEquals(1f, FullPlayerCompositionPolicy.staggeredFraction(1f, startOffset = 0.5f), 0.0001f)
    }
}
