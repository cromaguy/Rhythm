/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.util.LyricsParser
import chromahub.rhythm.app.util.RhythmLyricsParser
import chromahub.rhythm.app.util.WordByWordLyricLine

/**
 * A single lyric line that follows playback, for compact surfaces like the external
 * playback sheet. Word-by-word lyrics highlight each word as it is sung; line-synced
 * lyrics show the active line only, animated when it changes.
 */
@Composable
fun ActiveLyricLine(
    lyrics: LyricsData?,
    playbackTimeMs: Long,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val wordLines = remember(lyrics?.wordByWordLyrics) {
        lyrics?.getWordByWordLyricsOrNull()
            ?.let { RhythmLyricsParser.parseWordByWordLyrics(it) }
            .orEmpty()
    }
    val syncedLines = remember(lyrics?.syncedLyrics) {
        lyrics?.getSyncedLyricsOrNull()
            ?.let { LyricsParser.parseLyrics(it) }
            .orEmpty()
    }

    if (wordLines.isNotEmpty()) {
        val activeLine = wordLines.lastOrNull { it.lineTimestamp <= playbackTimeMs } ?: return

        AnimatedContent(
            targetState = activeLine,
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 })
                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "activeWordLine",
            modifier = modifier.fillMaxWidth()
        ) { line ->
            ActiveWordLineText(
                line = line,
                playbackTimeMs = playbackTimeMs,
                activeColor = activeColor,
                inactiveColor = inactiveColor
            )
        }
    } else if (syncedLines.isNotEmpty()) {
        val activeIndex = syncedLines.indexOfLast { it.timestamp <= playbackTimeMs }
        if (activeIndex < 0) return

        AnimatedContent(
            targetState = activeIndex,
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 })
                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "activeSyncedLine",
            modifier = modifier.fillMaxWidth()
        ) { index ->
            Text(
                text = syncedLines.getOrNull(index)?.text.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = activeColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActiveWordLineText(
    line: WordByWordLyricLine,
    playbackTimeMs: Long,
    activeColor: Color,
    inactiveColor: Color
) {
    val upcomingColor = inactiveColor.copy(alpha = 0.55f)

    val text = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            if (index > 0 && !word.isPart) {
                append(" ")
            }

            val style = when {
                word.endtime <= playbackTimeMs -> SpanStyle(
                    color = activeColor,
                    fontWeight = FontWeight.Bold
                )

                word.timestamp > playbackTimeMs -> SpanStyle(
                    color = upcomingColor,
                    fontWeight = FontWeight.Medium
                )

                else -> {
                    val duration = (word.endtime - word.timestamp).coerceAtLeast(1L)
                    val progress = ((playbackTimeMs - word.timestamp).toFloat() / duration).coerceIn(0f, 1f)
                    SpanStyle(
                        color = lerp(upcomingColor, activeColor, progress),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            withStyle(style) { append(word.text) }
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
