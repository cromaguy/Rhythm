/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R

/**
 * Three-dot countdown pill rendered during long instrumental breaks in both word-by-word
 * and line-synced lyrics views.
 */
@Composable
internal fun InstrumentalBreakIndicator(
    startTimeMs: Long,
    durationMs: Long,
    currentPlaybackTime: Long,
    textColor: Color?,
    activeColor: Color?,
    noAnimation: Boolean,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isCurrentGap = currentPlaybackTime >= startTimeMs &&
        currentPlaybackTime < startTimeMs + durationMs

    val breakProgress = if (isCurrentGap && durationMs > 0L) {
        ((currentPlaybackTime - startTimeMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val pillAlpha by animateFloatAsState(
        targetValue = if (isCurrentGap) 0.96f else 0.34f,
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gapPillAlpha"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isCurrentGap) 1.06f else 0.95f,
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "gapPillScale"
    )

    val breathPhase = if (isCurrentGap && !noAnimation) {
        val transition = rememberInfiniteTransition(label = "breakBreath")
        val v by transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breakBreathPhase"
        )
        v
    } else 1f

    val baseInk = activeColor ?: textColor ?: MaterialTheme.colorScheme.primary
    val subtleInk = (textColor ?: MaterialTheme.colorScheme.onSurface)
    val gapPadding = (durationMs / 1000f).coerceIn(10f, 20f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onSeek != null) Modifier.clickable { onSeek(startTimeMs) } else Modifier
            )
            .padding(vertical = gapPadding.dp)
            .graphicsLayer {
                alpha = pillAlpha
                scaleX = pillScale
                scaleY = pillScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = subtleInk.copy(alpha = if (isCurrentGap) 0.10f else 0.05f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (dotIndex in 0..2) {
                    val threshold = dotIndex / 3f
                    val dotFill = if (!isCurrentGap) {
                        0.35f
                    } else {
                        ((breakProgress - threshold) * 3f).coerceIn(0.28f, 1f) * breathPhase
                    }
                    val dotScale = if (isCurrentGap && dotFill > 0.65f) 1.22f else 1.0f
                    Box(
                        modifier = Modifier
                            .size((8f * dotScale).dp)
                            .clip(CircleShape)
                            .background(baseInk.copy(alpha = dotFill))
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.lyrics_instrumental),
            style = MaterialTheme.typography.labelMedium,
            color = subtleInk.copy(alpha = if (isCurrentGap) 0.68f else 0.30f)
        )
    }
}

/**
 * Compact multi-voice speaker or backing vocal chip displayed only on duet,
 * group chorus, or backing vocal lines.
 */
@Composable
internal fun SpeakerBadgeRow(
    voiceTag: String?,
    isBackground: Boolean,
    isCurrentLine: Boolean,
    accentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    val label = when {
        isBackground -> "Backing"
        voiceTag in listOf("v2", "voice2") -> "Vocal 2"
        voiceTag in listOf("v3", "voice3", "group") -> "Chorus"
        else -> null
    } ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        contentAlignment = when (horizontalAlignment) {
            Alignment.Start -> Alignment.CenterStart
            Alignment.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = accentColor.copy(alpha = if (isCurrentLine) 0.18f else 0.08f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                ),
                color = accentColor.copy(alpha = if (isCurrentLine) 0.95f else 0.68f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
