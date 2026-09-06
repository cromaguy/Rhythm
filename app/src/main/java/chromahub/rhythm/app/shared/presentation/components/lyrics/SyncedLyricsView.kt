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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.util.LyricLine
import chromahub.rhythm.app.util.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.RhythmApplication
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private sealed class SyncedLyricsItem {
    data class Line(val line: LyricLine, val index: Int) : SyncedLyricsItem()
    data class Gap(val duration: Long, val startTime: Long) : SyncedLyricsItem()
}

private const val MIN_VOCAL_GAP_MS = 5200L
private const val LARGE_SCROLL_CATCH_UP_DELTA = 8

private suspend fun LazyListState.animateToSyncedItemWithCatchUp(
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
        scrollToItem(safeTargetIndex, scrollOffset = scrollOffset)
        return
    }

    if (delta >= LARGE_SCROLL_CATCH_UP_DELTA) {
        val prePositionIndex = if (safeTargetIndex > currentIndex) {
            (safeTargetIndex - 1).coerceAtLeast(0)
        } else {
            (safeTargetIndex + 1).coerceAtMost(lastIndex)
        }

        if (prePositionIndex != safeTargetIndex) {
            scrollToItem(prePositionIndex, scrollOffset = scrollOffset)
        }
    }

    animateScrollToItem(safeTargetIndex, scrollOffset = scrollOffset)
}

private fun buildSyncedLyricsItems(lines: List<LyricLine>): List<SyncedLyricsItem> {
    val filtered = lines.mapIndexedNotNull { i, line ->
        if (line.text.isNotBlank()) i to line else null
    }
    if (filtered.isEmpty()) return emptyList()

    val intervals = filtered.zipWithNext { (_, a), (_, b) -> (b.timestamp - a.timestamp).coerceAtLeast(0L) }
        .filter { it > 0L }

    val medianInterval = if (intervals.isNotEmpty()) {
        val sorted = intervals.sorted()
        sorted[sorted.size / 2]
    } else {
        2000L
    }

    val vocalEstimate = (medianInterval * 0.9f).toLong().coerceIn(900L, 2600L)
    val longGapThreshold = maxOf(MIN_VOCAL_GAP_MS, (medianInterval * 2.4f).toLong())

    return buildList {
        filtered.forEachIndexed { idx, (origIdx, line) ->
            add(SyncedLyricsItem.Line(line, origIdx))

            if (idx < filtered.lastIndex) {
                val nextLine = filtered[idx + 1].second
                val intervalToNext = nextLine.timestamp - line.timestamp

                var explicitGapStart: Long? = line.endTime
                if (explicitGapStart == null) {
                    for (i in origIdx + 1 until filtered[idx + 1].first) {
                        if (lines[i].text.isBlank()) {
                            explicitGapStart = lines[i].timestamp
                            break
                        }
                    }
                }

                val hasExplicitGap = explicitGapStart != null
                val gapStart = explicitGapStart ?: (line.timestamp + vocalEstimate)
                val gapDuration = (nextLine.timestamp - gapStart).coerceAtLeast(0L)

                val shouldAddGap = if (hasExplicitGap) {
                    gapDuration >= 1800L
                } else {
                    intervalToNext >= longGapThreshold && gapDuration >= 1800L
                }

                if (shouldAddGap) {
                    add(
                        SyncedLyricsItem.Gap(
                            duration = gapDuration,
                            startTime = gapStart
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SyncedLyricsView(
    lyrics: String,
    currentPlaybackTime: Long,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    parsedLyricsInput: List<LyricLine>? = null,
    onSeek: ((Long) -> Unit)? = null,
    syncOffset: Long = 0L, // TODO: Add UI controls for adjusting sync offset in real-time
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    lyricsSource: String? = null, // Source of lyrics (e.g., "LRCLib", "Embedded", "Local File")
    textSizeMultiplier: Float = 1.0f, // Scale factor for lyrics text size
    textAlignment: TextAlign = TextAlign.Center, // Alignment of lyrics text
    onTapLyricsView: (() -> Unit)? = null,
    textColor: Color? = null,
    activeColor: Color? = null,
    subtitleColor: Color? = null
) {
    val context = LocalContext.current
    // TODO: Apply syncOffset to all timestamp comparisons for manual sync adjustment
    val adjustedPlaybackTime = currentPlaybackTime + syncOffset
    
    val parsedLyrics by produceState(
        initialValue = parsedLyricsInput ?: emptyList(),
        key1 = lyrics,
        key2 = parsedLyricsInput
    ) {
        value = if (parsedLyricsInput != null) {
            parsedLyricsInput
        } else {
            withContext(Dispatchers.Default) {
                LyricsParser.parseLyrics(lyrics)
            }
        }
    }

    val appSettings = remember(context) { AppSettings.getInstance(context) }
    val lyricBoldVal by appSettings.lyricBold.collectAsState()
    val lyricNoAnimationVal by appSettings.lyricNoAnimation.collectAsState()
    val tapLyricsToSeek by appSettings.tapLyricsToSeek.collectAsState()

    // Track previous line for smooth transitions
    val previousLineIndex = remember { mutableIntStateOf(-1) }

    val lyricsItems = remember(parsedLyrics) {
        buildSyncedLyricsItems(parsedLyrics)
    }

    val lineToItemIndex = remember(lyricsItems) {
        buildMap {
            lyricsItems.forEachIndexed { itemIndex, item ->
                if (item is SyncedLyricsItem.Line) {
                    put(item.index, itemIndex)
                }
            }
        }
    }
    
    // Detect if playback is currently in an instrumental gap
    val isInGap by remember(adjustedPlaybackTime, lyricsItems) {
        derivedStateOf {
            lyricsItems.any { item ->
                item is SyncedLyricsItem.Gap &&
                    adjustedPlaybackTime >= item.startTime &&
                    adjustedPlaybackTime < item.startTime + item.duration
            }
        }
    }

    // Find current line index (using adjustedPlaybackTime for sync offset)
    val currentLineIndex by remember(adjustedPlaybackTime, parsedLyrics) {
        derivedStateOf {
            parsedLyrics.indexOfLast { it.timestamp <= adjustedPlaybackTime }
        }
    }

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
    LaunchedEffect(lyrics, parsedLyricsInput) {
        previousLineIndex.intValue = -1
        userScrolledRecently = false
    }

    // Enhanced auto-scroll with spring animation
    LaunchedEffect(currentLineIndex, userScrolledRecently) {
        if (currentLineIndex >= 0 && parsedLyrics.isNotEmpty()) {
            val isIndexChange = currentLineIndex != previousLineIndex.intValue
            // If user explicitly jumped/seeked (jump > 1 line), override scroll lock
            if (isIndexChange && abs(currentLineIndex - previousLineIndex.intValue) > 1) {
                userScrolledRecently = false
            }

            if (!userScrolledRecently) {
                previousLineIndex.intValue = currentLineIndex
                val offset = listState.layoutInfo.viewportSize.height / 3
                val targetItemIndex = (lineToItemIndex[currentLineIndex] ?: currentLineIndex)
                    .coerceIn(0, lyricsItems.lastIndex.coerceAtLeast(0))
                if (lyricsItems.isNotEmpty()) {
                    listState.animateToSyncedItemWithCatchUp(
                        targetIndex = targetItemIndex,
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

    val isInstrumental = remember(parsedLyrics, lyrics) {
        isInstrumentalOrNoVocals(parsedLyrics, lyrics)
    }

    if (isInstrumental) {
        InstrumentalPlaceholder(
            modifier = modifier,
            titleText = "Instrumental",
            subtitleText = "No vocals detected in this song",
            textColor = textColor,
            activeColor = activeColor
        )
    } else if (parsedLyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.synced_lyrics_unavailable),
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
                        is SyncedLyricsItem.Line -> "synced_line_${item.line.timestamp}_${item.index}"
                        is SyncedLyricsItem.Gap -> "synced_gap_${item.startTime}_${item.duration}_$index"
                    }
                },
                contentType = { _, item ->
                    when (item) {
                        is SyncedLyricsItem.Line -> "synced_line"
                        is SyncedLyricsItem.Gap -> "synced_gap"
                    }
                }
            ) { _, item ->
                when (item) {
                    is SyncedLyricsItem.Line -> {
                        SyncedLyricItem(
                            line = item.line,
                            index = item.index,
                            currentLineIndex = currentLineIndex,
                            currentPlaybackTime = adjustedPlaybackTime,
                            parsedLyrics = parsedLyrics,
                            onSeek = handleSeek,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            textSizeMultiplier = textSizeMultiplier,
                            textAlignment = textAlignment,
                            onTapLyricsView = onTapLyricsView,
                            lyricBold = lyricBoldVal,
                            noAnimation = lyricNoAnimationVal,
                            textColor = textColor,
                            activeColor = activeColor,
                            subtitleColor = subtitleColor
                        )
                    }

                    is SyncedLyricsItem.Gap -> {
                        SyncedVocalGapItem(
                            item = item,
                            currentPlaybackTime = adjustedPlaybackTime,
                            noAnimation = lyricNoAnimationVal,
                            textColor = textColor
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
private fun SyncedVocalGapItem(
    item: SyncedLyricsItem.Gap,
    currentPlaybackTime: Long,
    noAnimation: Boolean,
    textColor: Color? = null
) {
    val isCurrentGap = currentPlaybackTime >= item.startTime &&
        currentPlaybackTime < item.startTime + item.duration

    val gapPadding = (item.duration / 1000f).coerceIn(8f, 20f)

    val iconScale by animateFloatAsState(
        targetValue = if (isCurrentGap) 1.3f else 1f,
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "syncedGapScale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isCurrentGap) 0.82f else 0.3f,
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "syncedGapAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = gapPadding.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "♪",
            style = MaterialTheme.typography.titleMedium,
            color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = iconAlpha),
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )
        Text(
            text = stringResource(R.string.lyrics_instrumental),
            style = MaterialTheme.typography.labelMedium,
            color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(
                alpha = if (isCurrentGap) 0.6f else 0.25f
            )
        )
    }
}

/**
 * Individual synced lyric line with enhanced animations
 */
@Composable
private fun SyncedLyricItem(
    line: chromahub.rhythm.app.util.LyricLine,
    index: Int,
    currentLineIndex: Int,
    currentPlaybackTime: Long,
    parsedLyrics: List<chromahub.rhythm.app.util.LyricLine>,
    onSeek: ((Long) -> Unit)?,
    showTranslation: Boolean,
    showRomanization: Boolean,
    textSizeMultiplier: Float = 1.0f,
    textAlignment: TextAlign = TextAlign.Center,
    onTapLyricsView: (() -> Unit)? = null,
    lyricBold: Boolean = false,
    noAnimation: Boolean = false,
    textColor: Color? = null,
    activeColor: Color? = null,
    subtitleColor: Color? = null
) {
    val isCurrentLine = currentLineIndex == index
    val isPreviousLine = currentLineIndex == index + 1
    val isNextLine = currentLineIndex == index - 1
    
    // Distance-based effects
    val distanceFromCurrent = abs(index - currentLineIndex)
    
    // Calculate progress through current line
    val progressToNextLine = if (isCurrentLine && index + 1 < parsedLyrics.size) {
        val nextLineTimestamp = parsedLyrics[index + 1].timestamp
        val timeDiff = nextLineTimestamp - line.timestamp
        if (timeDiff > 0) {
            ((currentPlaybackTime - line.timestamp).toFloat() / timeDiff).coerceIn(0f, 1f)
        } else 0f
    } else 0f
    
    // Smooth scale animation with spring physics - Rhythm style
    val scale by animateFloatAsState(
        targetValue = when {
            isCurrentLine -> 1.10f
            isNextLine -> 1.03f + (0.07f * progressToNextLine)
            else -> 1f
        },
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lineScale_$index"
    )
    
    // Enhanced alpha with distance-based gradual fade
    val alpha by animateFloatAsState(
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
            stiffness = Spring.StiffnessMedium
        ),
        label = "lineAlpha_$index"
    )
    
    // Vertical translation for flowing effect
    val verticalTranslation by animateFloatAsState(
        targetValue = if (isCurrentLine) 0f else if (isPreviousLine) -8f else 0f,
        animationSpec = if (noAnimation) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lineTranslationY_$index"
    )

    // Color transition for active line with voice-specific colors
    val lineColor = when {
        isCurrentLine -> {
            // Apply different colors based on voice tag
            when (line.voiceTag) {
                "v2" -> activeColor ?: MaterialTheme.colorScheme.secondary // Different color for second voice
                "v3" -> activeColor ?: MaterialTheme.colorScheme.tertiary  // Third voice
                else -> activeColor ?: MaterialTheme.colorScheme.primary   // Default/v1
            }
        }
        else -> {
            // Inactive lines also get subtle voice coloring (alpha applied via modifier)
            when (line.voiceTag) {
                "v2" -> activeColor ?: MaterialTheme.colorScheme.secondary
                "v3" -> activeColor ?: MaterialTheme.colorScheme.tertiary
                else -> textColor ?: MaterialTheme.colorScheme.onSurface
            }
        }
    }
    
    // Dynamic font weight based on position
    val fontWeight = when {
        isCurrentLine -> if (lyricBold) FontWeight.Black else FontWeight.ExtraBold
        distanceFromCurrent <= 1 -> if (lyricBold) FontWeight.ExtraBold else FontWeight.SemiBold
        distanceFromCurrent <= 2 -> if (lyricBold) FontWeight.Bold else FontWeight.Medium
        else -> if (lyricBold) FontWeight.SemiBold else FontWeight.Normal
    }
    
    // Subtle letter spacing for emphasis
    val letterSpacing = if (isCurrentLine) 0.05.sp else 0.sp

    val columnAlignment = when (textAlignment) {
        TextAlign.Start -> Alignment.Start
        TextAlign.End -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onSeek?.invoke(line.timestamp)
                onTapLyricsView?.invoke()
            }
            .padding(vertical = 14.dp, horizontal = 20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = verticalTranslation
            }
            .alpha(alpha),
        horizontalAlignment = columnAlignment
    ) {
        // Main lyrics text
        Text(
            text = line.text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = fontWeight,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize * textSizeMultiplier,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.5f * textSizeMultiplier,
                letterSpacing = letterSpacing
            ),
            color = lineColor,
            textAlign = textAlignment,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Translation text (if available and enabled)
        if (showTranslation && !line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.translation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = if (isCurrentLine) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f
                ),
                color = (subtitleColor ?: MaterialTheme.colorScheme.tertiary).copy(alpha = if (isCurrentLine) 0.84f else 0.62f),
                textAlign = textAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isCurrentLine) 0.9f else 0.7f)
            )
        }
        
        val hasDistinctRomanization = showRomanization &&
            !line.romanization.isNullOrBlank() &&
            (line.translation.isNullOrBlank() || line.romanization.trim().lowercase() != line.translation.trim().lowercase())

        // Romanization text (if available and enabled)
        if (hasDistinctRomanization) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.romanization,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isCurrentLine) FontWeight.Normal else FontWeight.Light,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
                    letterSpacing = 0.02.sp
                ),
                color = (textColor ?: MaterialTheme.colorScheme.onSurface).copy(
                    alpha = if (isCurrentLine) 0.65f else 0.5f
                ),
                textAlign = textAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isCurrentLine) 0.9f else 0.7f)
            )
        }
    }
}

private fun isInstrumentalOrNoVocals(lines: List<LyricLine>, rawLyrics: String): Boolean {
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
            val textCanonical = line.text.trim().lowercase().removeSurrounding("[", "]").removeSurrounding("(", ")").trim()
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
