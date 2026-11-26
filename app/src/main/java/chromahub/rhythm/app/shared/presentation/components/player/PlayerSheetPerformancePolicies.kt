/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import kotlin.math.roundToInt

/**
 * Pure policies governing full-player composition gating, sheet geometry transitions,
 * and zero-recomposition staggered layer calculations.
 */
object FullPlayerCompositionPolicy {

    const val SHEET_VISIBLE_THRESHOLD = 0.015f
    const val MINI_FADE_END_FRACTION = 0.25f
    const val EXPANDED_FADE_START_FRACTION = 0.20f
    const val BACK_HANDLER_ACTIVE_FRACTION = 0.50f

    /**
     * Returns true when the expanded full-player subtree should participate in rendering
     * or stay warm once expanded at least once for the current session.
     */
    fun shouldComposeExpandedContent(
        expansionFraction: Float,
        isExpanded: Boolean,
        wasPreviouslyExpanded: Boolean
    ): Boolean {
        return isExpanded || wasPreviouslyExpanded || expansionFraction > SHEET_VISIBLE_THRESHOLD
    }

    /**
     * Computes staggered entrance progress in [0f, 1f] cleanly inside a graphics layer
     * without triggering Compose recompositions on each animation frame.
     */
    fun staggeredFraction(fraction: Float, startOffset: Float, span: Float = 0.5f): Float {
        if (span <= 0f) return if (fraction >= startOffset) 1f else 0f
        val normalized = ((fraction - startOffset) / span).coerceIn(0f, 1f)
        return FastOutSlowInEasing.transform(normalized)
    }

    fun miniPlayerAlpha(expansionFraction: Float): Float {
        return (1f - (expansionFraction.coerceIn(0f, 1f) / MINI_FADE_END_FRACTION)).coerceIn(0f, 1f)
    }

    fun expandedPlayerAlpha(expansionFraction: Float): Float {
        return ((expansionFraction.coerceIn(0f, 1f) - EXPANDED_FADE_START_FRACTION) /
            (1f - EXPANDED_FADE_START_FRACTION)).coerceIn(0f, 1f)
    }

    fun cornerBlendProgress(expansionFraction: Float): Float {
        return ((expansionFraction.coerceIn(0f, 1f) - 0.75f) / 0.25f).coerceIn(0f, 1f)
    }
}

/**
 * Gates expensive continuous background/backdrop animations while the full player sheet
 * is completely collapsed so scrolling the library stays free of background frame churn.
 */
object FullPlayerRuntimePolicy {

    fun shouldRunAmbientEffects(
        backdropEnabled: Boolean,
        isSheetVisible: Boolean,
        swipeToDismissEnabled: Boolean
    ): Boolean {
        return backdropEnabled && (swipeToDismissEnabled || isSheetVisible)
    }
}

/**
 * Bounded LRU cache of Compose [ComposeRenderEffect] blur instances keyed by quantized
 * blur radius in pixels, avoiding native RenderEffect allocations during sheet drags.
 */
object BlurEffectCache {
    private val cache = object : LruCache<Int, ComposeRenderEffect>(16) {}

    fun getBlur(radiusPx: Float): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val quantized = radiusPx.coerceAtLeast(0.5f).roundToInt()
        cache.get(quantized)?.let { return it }
        val native = RenderEffect.createBlurEffect(
            quantized.toFloat(),
            quantized.toFloat(),
            Shader.TileMode.CLAMP
        )
        val compose = native.asComposeRenderEffect()
        cache.put(quantized, compose)
        return compose
    }
}

/**
 * Suppresses Android stretch overscroll inside full-bleed player sheets where stretch
 * distorts edge-to-edge artwork and backdrop shaders.
 */
@Composable
fun WithoutPlatformOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        content()
    }
}
