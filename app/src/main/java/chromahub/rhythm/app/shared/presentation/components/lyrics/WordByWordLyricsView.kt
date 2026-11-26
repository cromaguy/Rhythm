/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.util.RhythmLyricsParser
import chromahub.rhythm.app.util.WordByWordLyricLine
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.RhythmApplication
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import kotlin.math.abs

/**
 * Represents either a lyrics line or a gap indicator
 */
sealed class LyricsItem {
    data class LyricLine(
        val line: WordByWordLyricLine,
        val index: Int,
        val backgroundLines: List<Pair<Int, WordByWordLyricLine>> = emptyList()
    ) : LyricsItem()
    data class Gap(val duration: Long, val startTime: Long) : LyricsItem()
}

internal fun buildGroupedWordByWordItems(
    visibleLyricsLines: List<WordByWordLyricLine>
): List<LyricsItem> {
    val vocalLines = visibleLyricsLines.mapIndexedNotNull { i, line ->
        if (line.words.any { it.text.isNotBlank() }) i to line else null
    }
    if (vocalLines.isEmpty()) return emptyList()

    val intervals = vocalLines.zipWithNext { (_, a), (_, b) -> (b.lineTimestamp - a.lineTimestamp).coerceAtLeast(0L) }
        .filter { it > 0L }
    val medianInterval = if (intervals.isNotEmpty()) {
        val sorted = intervals.sorted()
        sorted[sorted.size / 2]
    } else {
        2000L
    }
    val longGapThreshold = maxOf(5200L, (medianInterval * 2.4f).toLong())

    // Nest backing vocal lines directly under their lead line so they do not hijack vertical scroll focus.
    val grouped = mutableListOf<LyricsItem.LyricLine>()
    vocalLines.forEach { (origIdx, line) ->
        if (line.background && grouped.isNotEmpty()) {
            val prev = grouped.removeAt(grouped.lastIndex)
            grouped.add(prev.copy(backgroundLines = prev.backgroundLines + (origIdx to line)))
        } else {
            grouped.add(LyricsItem.LyricLine(line = line, index = origIdx))
        }
    }

    val items = mutableListOf<LyricsItem>()
    grouped.forEachIndexed { idx, group ->
        items.add(group)
        if (idx < grouped.size - 1) {
            val nextGroup = grouped[idx + 1]
            val groupEndMs = maxOf(
                group.line.effectiveLineEndtime(),
                group.backgroundLines.maxOfOrNull { it.second.effectiveLineEndtime() } ?: 0L
            )
            val gapDuration = nextGroup.line.lineTimestamp - groupEndMs
            val isExplicit = !group.line.endIsImplicit
            val shouldAddGap = if (isExplicit) {
                gapDuration >= 4500L
            } else {
                val intervalToNext = nextGroup.line.lineTimestamp - group.line.lineTimestamp
                intervalToNext >= longGapThreshold && gapDuration >= 3500L
            }
            if (shouldAddGap) {
                items.add(LyricsItem.Gap(gapDuration, groupEndMs))
            }
        }
    }
    return items
}

private const val LARGE_SCROLL_CATCH_UP_DELTA = 8

private fun WordByWordLyricLine.effectiveLineEndtime(): Long {
    val maxWordEnd = words.maxOfOrNull { it.endtime } ?: lineEndtime
    return maxOf(lineEndtime, maxWordEnd, lineTimestamp)
}

private fun WordByWordLyricLine.timingRichnessScore(): Int {
    if (words.isEmpty()) return 0

    val distinctWordStarts = words.map { it.timestamp }.distinct().size
    val advancingStarts = words.zipWithNext().count { (first, second) ->
        second.timestamp > first.timestamp
    }
    val partWords = words.count { it.isPart }
    val positiveDurations = words.count { it.endtime > it.timestamp }

    return (distinctWordStarts * 32) + (advancingStarts * 24) + (partWords * 16) + (positiveDurations * 8)
}

private suspend fun LazyListState.animateToLyricItemWithCatchUp(
    targetIndex: Int,
    scrollOffset: Int,
    lastIndex: Int,
    noAnimation: Boolean = false
) {
    if (lastIndex < 0) return
    val safeTargetIndex = targetIndex.coerceIn(0, lastIndex)
    val currentIndex = firstVisibleItemIndex
    val delta = abs(currentIndex - safeTargetIndex)

    if (noAnimation) {
        scrollToItem(index = safeTargetIndex, scrollOffset = scrollOffset)
        return
    }

    if (delta >= LARGE_SCROLL_CATCH_UP_DELTA) {
        val prePositionIndex = if (safeTargetIndex > currentIndex) {
            (safeTargetIndex - 1).coerceAtLeast(0)
        } else {
            (safeTargetIndex + 1).coerceAtMost(lastIndex)
        }

        if (prePositionIndex != safeTargetIndex) {
            scrollToItem(index = prePositionIndex, scrollOffset = scrollOffset)
        }
    }

    animateScrollToItem(index = safeTargetIndex, scrollOffset = scrollOffset)
}

/**
 * Animation presets for word-by-word highlighting
 * TODO: Implement different animation styles for word transitions
 */
enum class WordAnimationPreset {
    DEFAULT,      // Standard fade and scale
    BOUNCE,       // Bouncy spring animation (TODO: implement)
    SLIDE,        // Slide-in from sides (TODO: implement)
    GLOW,         // Glowing highlight effect (TODO: implement)
    KARAOKE,      // Filling bar effect (TODO: implement)
    MINIMAL       // Subtle color change only (TODO: implement)
}

/**
 * Composable for displaying Rhythm word-by-word synchronized lyrics
 * TODO: Add animation preset system for different word highlighting styles
 */
@Composable
fun WordByWordLyricsView(
    wordByWordLyrics: String,
    currentPlaybackTime: Long,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onSeek: ((Long) -> Unit)? = null,
    syncOffset: Long = 0L, // TODO: Add UI controls for adjusting sync offset in real-time
    animationPreset: WordAnimationPreset = WordAnimationPreset.DEFAULT, // TODO: Implement animation presets
    lyricsSource: String? = null, // Source of lyrics
    textSizeMultiplier: Float = 1.0f, // Scale factor for lyrics text size
    textAlignment: TextAlign = TextAlign.Center, // Alignment of lyrics text
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    onTapLyricsView: (() -> Unit)? = null,
    textColor: Color? = null,
    activeColor: Color? = null,
    subtitleColor: Color? = null
) {
    val context = LocalContext.current
    var maxSeenPlaybackTime by remember(wordByWordLyrics) { mutableLongStateOf(0L) }
    val effectivePlaybackTime = remember(currentPlaybackTime) {
        if (abs(currentPlaybackTime - maxSeenPlaybackTime) > 1500L) {
            maxSeenPlaybackTime = currentPlaybackTime
            currentPlaybackTime
        } else {
            maxSeenPlaybackTime = maxOf(maxSeenPlaybackTime, currentPlaybackTime)
            maxSeenPlaybackTime
        }
    }
    val adjustedPlaybackTime = effectivePlaybackTime + syncOffset
    
    val appSettings = remember(context) { AppSettings.getInstance(context) }
    val lyricBoldVal by appSettings.lyricBold.collectAsState()
    val lyricNoAnimationVal by appSettings.lyricNoAnimation.collectAsState()
    val tapLyricsToSeek by appSettings.tapLyricsToSeek.collectAsState()
    
    val parsedLyrics = remember(wordByWordLyrics) {
        RhythmLyricsParser.parseWordByWordLyrics(wordByWordLyrics)
    }

    val visibleLyricsLines = remember(parsedLyrics) {
        parsedLyrics
    }

    // Create items list with nested backing vocals and gaps for instrumental sections
    val lyricsItems = remember(visibleLyricsLines) {
        buildGroupedWordByWordItems(visibleLyricsLines)
    }

    // Detect if playback is currently in an instrumental gap
    val isInGap by remember(adjustedPlaybackTime, lyricsItems) {
        derivedStateOf {
            lyricsItems.any { item ->
                item is LyricsItem.Gap &&
                    adjustedPlaybackTime >= item.startTime &&
                    adjustedPlaybackTime < item.startTime + item.duration
            }
        }
    }

    // Find current line index (among lyric lines only) - using adjustedPlaybackTime for sync offset
    val currentLineIndex by remember(adjustedPlaybackTime, visibleLyricsLines) {
        derivedStateOf {
            val lastIndexAtPlayback = visibleLyricsLines.indexOfLast { line ->
                adjustedPlaybackTime >= line.lineTimestamp
            }

            if (lastIndexAtPlayback < 0) {
                -1
            } else {
                val activeTimestamp = visibleLyricsLines[lastIndexAtPlayback].lineTimestamp
                var firstIndexAtTimestamp = lastIndexAtPlayback
                while (firstIndexAtTimestamp > 0 && visibleLyricsLines[firstIndexAtTimestamp - 1].lineTimestamp == activeTimestamp) {
                    firstIndexAtTimestamp--
                }

                var bestIndex = firstIndexAtTimestamp
                var bestScore = visibleLyricsLines[firstIndexAtTimestamp].timingRichnessScore()

                for (index in (firstIndexAtTimestamp + 1)..lastIndexAtPlayback) {
                    val candidate = visibleLyricsLines[index]
                    if (candidate.lineTimestamp != activeTimestamp) continue

                    val candidateScore = candidate.timingRichnessScore()
                    if (candidateScore > bestScore) {
                        bestScore = candidateScore
                        bestIndex = index
                    }
                }

                bestIndex
            }
        }
    }

    // Track previous line for smooth transitions
    val previousLineIndex = remember { mutableIntStateOf(-1) }

    // Pause auto-scrolling when user manually scrolls
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledRecently by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolledRecently = true
        } else if (userScrolledRecently) {
            // Wait for any active fling deceleration to complete
            snapshotFlow { listState.isScrollInProgress }
                .first { !it }
            delay(3500L)
            userScrolledRecently = false
        }
    }

    // Reset user scroll lock and tracked line index on lyrics or song change
    LaunchedEffect(wordByWordLyrics) {
        previousLineIndex.intValue = -1
        userScrolledRecently = false
    }

    // Auto-scroll to current lyric line with elastic spring animation
    LaunchedEffect(currentLineIndex, userScrolledRecently) {
        if (currentLineIndex >= 0 && visibleLyricsLines.isNotEmpty()) {
            val isIndexChange = currentLineIndex != previousLineIndex.intValue
            // If user explicitly jumped/seeked (jump > 1 line), override scroll lock
            if (isIndexChange && abs(currentLineIndex - previousLineIndex.intValue) > 1) {
                userScrolledRecently = false
            }

            if (!userScrolledRecently) {
                previousLineIndex.intValue = currentLineIndex
                // Find the corresponding lead item index (including nested backing harmonies)
                val targetItemIndex = lyricsItems.indexOfFirst { item ->
                    item is LyricsItem.LyricLine && (
                        item.index == currentLineIndex ||
                            item.backgroundLines.any { it.first == currentLineIndex }
                        )
                }

                if (targetItemIndex >= 0 && lyricsItems.isNotEmpty()) {
                    val offset = listState.layoutInfo.viewportSize.height / 3
                    listState.animateToLyricItemWithCatchUp(
                        targetIndex = targetItemIndex.coerceIn(0, lyricsItems.lastIndex),
                        scrollOffset = -offset,
                        lastIndex = lyricsItems.lastIndex,
                        noAnimation = lyricNoAnimationVal
                    )
                }
            }
        }
    }

    val handleSeek: (Long) -> Unit = remember(onSeek, tapLyricsToSeek) {
        { timestamp ->
            userScrolledRecently = false
            previousLineIndex.intValue = -1
            if (tapLyricsToSeek) {
                onSeek?.invoke(timestamp)
            }
        }
    }

    val isInstrumental = remember(visibleLyricsLines, wordByWordLyrics) {
        isWordByWordInstrumental(visibleLyricsLines, wordByWordLyrics)
    }

    if (isInstrumental) {
        InstrumentalPlaceholder(
            modifier = modifier,
            titleText = "Instrumental",
            subtitleText = "No vocals detected in this song",
            textColor = textColor,
            activeColor = activeColor
        )
    } else if (visibleLyricsLines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.word_by_word_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = when (textAlignment) {
                TextAlign.Start -> Alignment.Start
                TextAlign.End -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
            contentPadding = PaddingValues(vertical = 30.dp)
        ) {
            itemsIndexed(
                items = lyricsItems,
                key = { index, item ->
                    when (item) {
                        is LyricsItem.LyricLine -> "lyric_line_${item.line.lineTimestamp}_${item.index}"
                        is LyricsItem.Gap -> "lyric_gap_${item.startTime}_${item.duration}_$index"
                    }
                },
                contentType = { _, item ->
                    when (item) {
                        is LyricsItem.LyricLine -> "lyric_line"
                        is LyricsItem.Gap -> "lyric_gap"
                    }
                }
            ) { _, item ->
                when (item) {
                    is LyricsItem.LyricLine -> {
                        val line = item.line
                        val index = item.index
                        val isBackgroundActive = item.backgroundLines.any { it.first == currentLineIndex }
                        val isCurrentLine = currentLineIndex == index || isBackgroundActive
                        val isUpcomingLine = index > currentLineIndex
                        val linesAhead = index - currentLineIndex
                        val distanceFromCurrent = abs(index - currentLineIndex)

                        val activeWordIndex = computeActiveWordIndex(
                            line = line,
                            isActive = currentLineIndex == index,
                            adjustedPlaybackTime = adjustedPlaybackTime
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            WordByWordLyricLineItem(
                                line = line,
                                index = index,
                                isCurrentLine = currentLineIndex == index,
                                isUpcomingLine = isUpcomingLine,
                                linesAhead = linesAhead,
                                distanceFromCurrent = if (isCurrentLine) 0 else distanceFromCurrent,
                                activeWordIndex = activeWordIndex,
                                adjustedPlaybackTime = adjustedPlaybackTime,
                                textSizeMultiplier = textSizeMultiplier,
                                textAlignment = textAlignment,
                                showTranslation = showTranslation,
                                showRomanization = showRomanization,
                                onSeek = handleSeek,
                                onTapLyricsView = onTapLyricsView,
                                lyricBold = lyricBoldVal,
                                noAnimation = lyricNoAnimationVal,
                                textColor = textColor,
                                activeColor = activeColor,
                                subtitleColor = subtitleColor
                            )
                            item.backgroundLines.forEach { (bgIdx, bgLine) ->
                                val bgActive = currentLineIndex == bgIdx ||
                                    (adjustedPlaybackTime >= bgLine.lineTimestamp && adjustedPlaybackTime <= bgLine.effectiveLineEndtime())
                                WordByWordLyricLineItem(
                                    line = bgLine,
                                    index = bgIdx,
                                    isCurrentLine = bgActive,
                                    isUpcomingLine = bgIdx > currentLineIndex,
                                    linesAhead = bgIdx - currentLineIndex,
                                    distanceFromCurrent = if (bgActive || isCurrentLine) 0 else abs(bgIdx - currentLineIndex),
                                    activeWordIndex = computeActiveWordIndex(bgLine, bgActive, adjustedPlaybackTime),
                                    adjustedPlaybackTime = adjustedPlaybackTime,
                                    textSizeMultiplier = textSizeMultiplier,
                                    textAlignment = textAlignment,
                                    showTranslation = showTranslation,
                                    showRomanization = showRomanization,
                                    onSeek = handleSeek,
                                    onTapLyricsView = onTapLyricsView,
                                    lyricBold = lyricBoldVal,
                                    noAnimation = lyricNoAnimationVal,
                                    textColor = textColor,
                                    activeColor = activeColor,
                                    subtitleColor = subtitleColor
                                )
                            }
                        }
                    }
                    is LyricsItem.Gap -> {
                        InstrumentalBreakIndicator(
                            startTimeMs = item.startTime,
                            durationMs = item.duration,
                            currentPlaybackTime = adjustedPlaybackTime,
                            textColor = textColor,
                            activeColor = activeColor,
                            noAnimation = lyricNoAnimationVal,
                            onSeek = handleSeek
                        )
                    }
                }
            }
            
            // Display lyrics source at the bottom
            if (!lyricsSource.isNullOrBlank()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.lyrics_source_attribution, lyricsSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WordByWordLyricLineItem(
    line: WordByWordLyricLine,
    index: Int,
    isCurrentLine: Boolean,
    isUpcomingLine: Boolean,
    linesAhead: Int,
    distanceFromCurrent: Int,
    activeWordIndex: Int,
    adjustedPlaybackTime: Long,
    textSizeMultiplier: Float,
    textAlignment: TextAlign,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onSeek: ((Long) -> Unit)?,
    onTapLyricsView: (() -> Unit)?,
    lyricBold: Boolean,
    noAnimation: Boolean,
    textColor: Color? = null,
    activeColor: Color? = null,
    subtitleColor: Color? = null
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isCurrentLine -> 1.08f
            isUpcomingLine && linesAhead == 1 -> 1.02f
            else -> 1f
        },
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "lineScale"
    )

    val opacity by animateFloatAsState(
        targetValue = when {
            isCurrentLine -> 1f
            distanceFromCurrent == 1 -> 0.75f
            distanceFromCurrent == 2 -> 0.55f
            distanceFromCurrent == 3 -> 0.40f
            distanceFromCurrent == 4 -> 0.30f
            else -> 0.22f
        },
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lineOpacity"
    )

    val animatedTranslationY by animateFloatAsState(
        targetValue = when {
            isUpcomingLine && linesAhead <= 3 -> (linesAhead * 6f)
            else -> 0f
        },
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "lineTranslation"
    )

    val translationAlpha by animateFloatAsState(
        targetValue = if (isCurrentLine) 0.95f else 0.72f,
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "translationAlpha"
    )

    val isWordTimed = remember(line) {
        RhythmLyricsParser.isLineWordTimed(line)
    }

    val isVoice2 = line.voiceTag in listOf("v2", "voice2")
    val isVoice3 = line.voiceTag in listOf("v3", "voice3", "group")
    val highlightInk = when {
        isVoice2 -> activeColor ?: MaterialTheme.colorScheme.secondary
        isVoice3 -> activeColor ?: MaterialTheme.colorScheme.tertiary
        else -> activeColor ?: MaterialTheme.colorScheme.primary
    }
    val unlitInk = when {
        isVoice2 || isVoice3 -> if (isCurrentLine) highlightInk.copy(alpha = 0.48f) else highlightInk
        isCurrentLine -> highlightInk.copy(alpha = 0.45f)
        else -> textColor ?: MaterialTheme.colorScheme.onSurface
    }

    val lineTextAlignment = if (textAlignment == TextAlign.Start && isVoice2) {
        TextAlign.End
    } else {
        textAlignment
    }
    val columnAlignment = when (lineTextAlignment) {
        TextAlign.Start -> Alignment.Start
        TextAlign.End -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val backingScale = if (line.background) 0.84f else 1.0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onSeek?.invoke(line.lineTimestamp)
                onTapLyricsView?.invoke()
            }
            .padding(
                vertical = if (line.background) 6.dp else 12.dp,
                horizontal = if (line.background) 24.dp else 16.dp
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (line.background && !isCurrentLine) opacity * 0.82f else opacity
                translationY = animatedTranslationY
            },
        horizontalAlignment = columnAlignment
    ) {
        SpeakerBadgeRow(
            voiceTag = line.voiceTag,
            isBackground = line.background,
            isCurrentLine = isCurrentLine,
            accentColor = highlightInk,
            horizontalAlignment = columnAlignment
        )

        DrawPhaseKaraokeText(
            words = line.words,
            lineTimestamp = line.lineTimestamp,
            lineEndtime = line.lineEndtime,
            adjustedPlaybackTime = adjustedPlaybackTime,
            isCurrentLine = isCurrentLine,
            isWordTimed = isWordTimed,
            lyricBold = lyricBold,
            noAnimation = noAnimation,
            inactiveColor = unlitInk,
            activeColor = highlightInk,
            baseStyle = MaterialTheme.typography.headlineSmall.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize * textSizeMultiplier * backingScale,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.4f * textSizeMultiplier * backingScale
            ),
            textAlign = lineTextAlignment,
            modifier = Modifier.fillMaxWidth()
        )

        if (showTranslation && !line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.translation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = if (isCurrentLine) {
                        if (lyricBold) FontWeight.Bold else FontWeight.SemiBold
                    } else FontWeight.Normal,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * (0.92f * textSizeMultiplier),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.32f * textSizeMultiplier
                ),
                color = (subtitleColor ?: MaterialTheme.colorScheme.tertiary).copy(
                    alpha = if (isCurrentLine) 0.86f else 0.62f
                ),
                textAlign = lineTextAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(translationAlpha)
            )
        }

        val hasDistinctRomanization = showRomanization &&
            !line.romanization.isNullOrBlank() &&
            (line.translation.isNullOrBlank() || line.romanization.trim().lowercase() != line.translation.trim().lowercase())

        if (hasDistinctRomanization) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.romanization,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (lyricBold) FontWeight.Medium else FontWeight.Normal,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * (0.9f * textSizeMultiplier),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f * textSizeMultiplier,
                    letterSpacing = 0.02.sp
                ),
                color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(
                    alpha = if (isCurrentLine) 0.68f else 0.5f
                ),
                textAlign = lineTextAlignment,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun computeActiveWordIndex(
    line: WordByWordLyricLine,
    isActive: Boolean,
    adjustedPlaybackTime: Long
): Int {
    if (!isActive) return -1
    val exactActive = line.words.indexOfLast { word ->
        adjustedPlaybackTime >= word.timestamp && adjustedPlaybackTime <= word.endtime
    }
    return if (exactActive >= 0) {
        exactActive
    } else {
        line.words.indexOfLast { word -> adjustedPlaybackTime >= word.timestamp }
    }
}

private fun isWordByWordInstrumental(lines: List<WordByWordLyricLine>, rawLyrics: String): Boolean {
    val canonical = rawLyrics.trim().lowercase().removeSurrounding("[", "]").removeSurrounding("(", ")").trim()
    if (lines.isEmpty()) {
        return canonical.isNotEmpty() && (
            canonical == "instrumental" ||
            canonical == "no vocals" ||
            canonical == "music" ||
            canonical == "instrumental track" ||
            canonical == "pure instrumental" ||
            canonical == "no lyrics"
        )
    }
    
    if (lines.size <= 2) {
        return lines.all { line ->
            val text = line.words.joinToString(" ") { it.text }
            val textCanonical = text.trim().lowercase().removeSurrounding("[", "]").removeSurrounding("(", ")").trim()
            textCanonical.isEmpty() ||
            textCanonical == "instrumental" ||
            textCanonical == "no vocals" ||
            textCanonical == "music" ||
            textCanonical == "instrumental break" ||
            textCanonical == "instrumental track" ||
            textCanonical == "pure instrumental" ||
            textCanonical == "no lyrics" ||
            textCanonical == "♪" ||
            textCanonical == "♫"
        }
    }
    return false
}

@Composable
private fun InstrumentalPlaceholder(
    modifier: Modifier = Modifier,
    titleText: String = "Instrumental",
    subtitleText: String = "Enjoy the music",
    textColor: Color? = null,
    activeColor: Color? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "instrumentalPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♪ ♫ ♪",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    ),
                    color = activeColor ?: MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = textColor ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
