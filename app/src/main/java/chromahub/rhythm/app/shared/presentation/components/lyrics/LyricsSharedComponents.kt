/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.util.KaraokeLineSplitter
import chromahub.rhythm.app.util.WordByWordWord
import kotlin.math.abs

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

/**
 * Pooled unit-width LTR/RTL feather gradients updated via matrix transform in draw phase
 * so karaoke gradient wipes trigger zero per-frame object allocation or text recomposition.
 */
private class PooledKaraokeFeatherMask {
    private val ltr = LinearGradient(
        0f, 0f, 1f, 0f,
        AndroidColor.WHITE,
        AndroidColor.TRANSPARENT,
        Shader.TileMode.CLAMP
    )
    private val rtl = LinearGradient(
        0f, 0f, 1f, 0f,
        AndroidColor.TRANSPARENT,
        AndroidColor.WHITE,
        Shader.TileMode.CLAMP
    )
    private val matrix = Matrix()
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun applyMaskRange(rtlDir: Boolean, xStart: Float, xEnd: Float) {
        val shader = if (rtlDir) rtl else ltr
        matrix.reset()
        matrix.setScale((xEnd - xStart).coerceAtLeast(1f), 1f)
        matrix.postTranslate(xStart, 0f)
        shader.setLocalMatrix(matrix)
        maskPaint.shader = shader
    }
}

/**
 * Draw-phase Canvas gradient karaoke text renderer.
 * Eliminates composition-phase `Brush.linearGradient` churn and text relayout on every frame
 * by keeping the text layout immutable and performing feathered masking & syllable lift in draw phase.
 */
@Composable
internal fun DrawPhaseKaraokeText(
    words: List<WordByWordWord>,
    lineTimestamp: Long,
    lineEndtime: Long,
    adjustedPlaybackTime: Long,
    isCurrentLine: Boolean,
    isWordTimed: Boolean,
    lyricBold: Boolean,
    noAnimation: Boolean,
    inactiveColor: Color,
    activeColor: Color,
    baseStyle: TextStyle,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val (fullText, charRanges) = remember(words) {
        KaraokeLineSplitter.buildRenderedTextAndRanges(words)
    }
    if (fullText.isEmpty()) return

    val activeStyle = remember(baseStyle, lyricBold) {
        baseStyle.copy(fontWeight = if (lyricBold) FontWeight.Black else FontWeight.Bold)
    }
    val unlitStyle = remember(baseStyle, lyricBold, isCurrentLine) {
        baseStyle.copy(
            fontWeight = when {
                isCurrentLine -> if (lyricBold) FontWeight.ExtraBold else FontWeight.SemiBold
                lyricBold -> FontWeight.Bold
                else -> FontWeight.Normal
            }
        )
    }

    var textLayout by remember(fullText, baseStyle, textAlign) { mutableStateOf<TextLayoutResult?>(null) }
    val featherMask = remember { PooledKaraokeFeatherMask() }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = fullText,
            style = unlitStyle,
            color = if (isCurrentLine && !isWordTimed) activeColor else inactiveColor,
            textAlign = textAlign,
            onTextLayout = { textLayout = it },
            modifier = Modifier.fillMaxWidth()
        )

        if (isCurrentLine && isWordTimed && !noAnimation) {
            Text(
                text = fullText,
                style = activeStyle,
                color = activeColor,
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        val layout = textLayout ?: return@drawWithContent
                        val textLen = fullText.length
                        if (textLen == 0) return@drawWithContent

                        val (activeIdx, easedP) = KaraokeLineSplitter.computeWordSweepState(
                            words = words,
                            lineEndMs = lineEndtime,
                            positionMs = adjustedPlaybackTime
                        )
                        if (activeIdx < 0) return@drawWithContent

                        val range = charRanges.getOrNull(activeIdx) ?: return@drawWithContent
                        val startIdx = range.first.coerceIn(0, textLen - 1)
                        val endCharIdx = (range.last).coerceIn(startIdx, textLen - 1)
                        val isLastAndComplete = activeIdx == words.lastIndex && easedP >= 1f

                        val liftFrac = KaraokeLineSplitter.computeSyllableLiftFraction(
                            words = words,
                            index = activeIdx,
                            lineEndMs = lineEndtime,
                            positionMs = adjustedPlaybackTime
                        )
                        val liftOffsetY = if (isLastAndComplete) 0f else -liftFrac * 2.dp.toPx()

                        val canvas = drawContext.canvas.nativeCanvas
                        if (isLastAndComplete) {
                            drawContent()
                            return@drawWithContent
                        }

                        val isRtl = layout.getParagraphDirection(startIdx) == ResolvedTextDirection.Rtl
                        val lineIdx = layout.getLineForOffset(startIdx)
                        val endLineIdx = layout.getLineForOffset(endCharIdx)

                        val sweepX: Float
                        val feather: Float
                        if (lineIdx == endLineIdx) {
                            val xs = layout.getHorizontalPosition(startIdx, true)
                            val xe = if (range.last + 1 < textLen && layout.getLineForOffset(range.last + 1) == lineIdx) {
                                layout.getHorizontalPosition(range.last + 1, true)
                            } else if (isRtl) layout.getLineLeft(lineIdx) else layout.getLineRight(lineIdx)
                            sweepX = xs + (xe - xs) * easedP
                            feather = (abs(xe - xs) * 0.35f).coerceIn(8f, 22f)
                        } else {
                            sweepX = if (isRtl) layout.getLineLeft(lineIdx) else layout.getLineRight(lineIdx)
                            feather = 14f
                        }

                        // Prior wrapped visual lines stay completely lit without descender clipping
                        val splitTop = if (lineIdx > 0) {
                            (layout.getLineBottom(lineIdx - 1) + layout.getLineTop(lineIdx)) * 0.5f
                        } else -size.height
                        val splitBottom = if (lineIdx + 1 < layout.lineCount) {
                            (layout.getLineBottom(lineIdx) + layout.getLineTop(lineIdx + 1)) * 0.5f
                        } else size.height * 2f

                        if (lineIdx > 0) {
                            val saved = canvas.save()
                            canvas.clipRect(-size.width, -size.height, size.width * 2f, splitTop)
                            drawContent()
                            canvas.restoreToCount(saved)
                        }

                        val layer = canvas.saveLayer(0f, splitTop, size.width, splitBottom, null)
                        canvas.save()
                        canvas.translate(0f, liftOffsetY)
                        drawContent()
                        canvas.restore()

                        val xStart = (sweepX - feather).coerceAtLeast(0f)
                        val xEnd = (sweepX + feather).coerceAtMost(size.width)
                        if (isRtl) {
                            if (xStart > 0f) canvas.drawRect(0f, splitTop, xStart, splitBottom, featherMask.clearPaint)
                        } else {
                            if (xEnd < size.width) canvas.drawRect(xEnd, splitTop, size.width, splitBottom, featherMask.clearPaint)
                        }
                        if (xEnd > xStart) {
                            featherMask.applyMaskRange(isRtl, xStart, xEnd)
                            canvas.drawRect(xStart, splitTop, xEnd, splitBottom, featherMask.maskPaint)
                        }
                        canvas.restoreToCount(layer)
                    }
            )
        } else if (isCurrentLine && isWordTimed && noAnimation) {
            Text(
                text = fullText,
                style = activeStyle,
                color = activeColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
