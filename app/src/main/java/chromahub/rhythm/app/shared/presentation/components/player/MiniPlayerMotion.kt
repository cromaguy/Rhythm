/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import chromahub.rhythm.app.shared.data.model.Song

/**
 * Material 3 & Material 3 Expressive motion tokens extracted from
 * androidx.compose.material3:material3-android (v1.5.0-beta01) sources:
 * - MotionTokens.kt
 * - StandardMotionTokens.kt
 * - ExpressiveMotionTokens.kt
 */
object MiniPlayerMotionTokens {
    val EasingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingStandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val EasingStandardAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400
    const val DurationLong1 = 450
    const val DurationLong2 = 500
    const val DurationLong4 = 600

    const val StandardSpatialDamping = 0.9f
    const val StandardSpatialStiffness = 700.0f
    const val StandardFastSpatialDamping = 0.9f
    const val StandardFastSpatialStiffness = 1400.0f
    const val StandardSlowSpatialDamping = 0.9f
    const val StandardSlowSpatialStiffness = 300.0f
    const val StandardEffectsDamping = 1.0f
    const val StandardEffectsStiffness = 1600.0f
    const val StandardFastEffectsDamping = 1.0f
    const val StandardFastEffectsStiffness = 3800.0f

    const val ExpressiveSpatialDamping = 0.8f
    const val ExpressiveSpatialStiffness = 380.0f
    const val ExpressiveFastSpatialDamping = 0.6f
    const val ExpressiveFastSpatialStiffness = 800.0f
    const val ExpressiveSlowSpatialDamping = 0.8f
    const val ExpressiveSlowSpatialStiffness = 200.0f
    const val ExpressiveEffectsDamping = 1.0f
    const val ExpressiveEffectsStiffness = 1600.0f
    const val ExpressiveFastEffectsDamping = 1.0f
    const val ExpressiveFastEffectsStiffness = 3800.0f
}

/**
 * Direction of track navigation for coordinated transitions.
 */
enum class TrackChangeDirection {
    FORWARD,
    BACKWARD,
    NONE
}

/**
 * Unified configuration for miniplayer animations, parameterized by theme.
 */
@Immutable
data class MiniPlayerMotionConfig(
    val isExpressive: Boolean,
    val spatialSpring: FiniteAnimationSpec<Float>,
    val fastSpatialSpring: FiniteAnimationSpec<Float>,
    val slowSpatialSpring: FiniteAnimationSpec<Float>,
    val effectsSpring: FiniteAnimationSpec<Float>,
    val fastEffectsSpring: FiniteAnimationSpec<Float>,
    val pressScale: Float
)

object MiniPlayerMotion {
    @Composable
    fun resolveConfig(themeId: String): MiniPlayerMotionConfig {
        val isExpressive = themeId == "EXPRESSIVE"
        return remember(isExpressive) {
            if (isExpressive) {
                MiniPlayerMotionConfig(
                    isExpressive = true,
                    spatialSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
                        stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
                    ),
                    fastSpatialSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.ExpressiveFastSpatialDamping,
                        stiffness = MiniPlayerMotionTokens.ExpressiveFastSpatialStiffness
                    ),
                    slowSpatialSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.ExpressiveSlowSpatialDamping,
                        stiffness = MiniPlayerMotionTokens.ExpressiveSlowSpatialStiffness
                    ),
                    effectsSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.ExpressiveEffectsDamping,
                        stiffness = MiniPlayerMotionTokens.ExpressiveEffectsStiffness
                    ),
                    fastEffectsSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.ExpressiveFastEffectsDamping,
                        stiffness = MiniPlayerMotionTokens.ExpressiveFastEffectsStiffness
                    ),
                    pressScale = 0.96f
                )
            } else {
                MiniPlayerMotionConfig(
                    isExpressive = false,
                    spatialSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.StandardSpatialDamping,
                        stiffness = MiniPlayerMotionTokens.StandardSpatialStiffness
                    ),
                    fastSpatialSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.StandardFastSpatialDamping,
                        stiffness = MiniPlayerMotionTokens.StandardFastSpatialStiffness
                    ),
                    slowSpatialSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.StandardSlowSpatialDamping,
                        stiffness = MiniPlayerMotionTokens.StandardSlowSpatialStiffness
                    ),
                    effectsSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.StandardEffectsDamping,
                        stiffness = MiniPlayerMotionTokens.StandardEffectsStiffness
                    ),
                    fastEffectsSpring = spring(
                        dampingRatio = MiniPlayerMotionTokens.StandardFastEffectsDamping,
                        stiffness = MiniPlayerMotionTokens.StandardFastEffectsStiffness
                    ),
                    pressScale = 0.985f
                )
            }
        }
    }

    /**
     * Elastic Spring Morph transition for song metadata & artwork when skipping tracks.
     */
    fun createTrackTransition(
        direction: TrackChangeDirection,
        isExpressive: Boolean
    ): ContentTransform {
        val enterSlideFactor = if (direction == TrackChangeDirection.FORWARD) 0.40f else -0.40f
        val exitSlideFactor = if (direction == TrackChangeDirection.FORWARD) -0.35f else 0.35f

        val enterOffsetSpring = if (isExpressive) {
            spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.76f, stiffness = 420f)
        } else {
            spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.90f, stiffness = 650f)
        }

        val enterFloatSpring = if (isExpressive) {
            spring<Float>(dampingRatio = 0.76f, stiffness = 420f)
        } else {
            spring<Float>(dampingRatio = 0.90f, stiffness = 650f)
        }

        val enterSlide = if (direction == TrackChangeDirection.NONE) {
            slideInHorizontally(
                initialOffsetX = { 0 },
                animationSpec = enterOffsetSpring
            )
        } else {
            slideInHorizontally(
                initialOffsetX = { (it * enterSlideFactor).toInt() },
                animationSpec = enterOffsetSpring
            )
        }

        val enterScale = scaleIn(
            initialScale = if (isExpressive) 0.90f else 0.96f,
            animationSpec = enterFloatSpring
        )

        val enterFade = fadeIn(
            animationSpec = tween(
                durationMillis = MiniPlayerMotionTokens.DurationShort4,
                easing = MiniPlayerMotionTokens.EasingEmphasizedDecelerate
            )
        )

        val exitOffsetSpec = tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = MiniPlayerMotionTokens.DurationShort4,
            easing = MiniPlayerMotionTokens.EasingEmphasizedAccelerate
        )

        val exitSlide = if (direction == TrackChangeDirection.NONE) {
            slideOutHorizontally(
                targetOffsetX = { 0 },
                animationSpec = exitOffsetSpec
            )
        } else {
            slideOutHorizontally(
                targetOffsetX = { (it * exitSlideFactor).toInt() },
                animationSpec = exitOffsetSpec
            )
        }

        val exitScale = scaleOut(
            targetScale = if (isExpressive) 0.92f else 0.96f,
            animationSpec = tween(
                durationMillis = MiniPlayerMotionTokens.DurationShort4,
                easing = MiniPlayerMotionTokens.EasingEmphasizedAccelerate
            )
        )

        val exitFade = fadeOut(
            animationSpec = tween(
                durationMillis = MiniPlayerMotionTokens.DurationShort3,
                easing = MiniPlayerMotionTokens.EasingEmphasizedAccelerate
            )
        )

        return (enterSlide + enterScale + enterFade).togetherWith(exitSlide + exitScale + exitFade)
    }
}
