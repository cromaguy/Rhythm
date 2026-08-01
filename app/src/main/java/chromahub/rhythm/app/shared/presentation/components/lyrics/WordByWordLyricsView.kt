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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import kotlin.math.abs

/**
 * Represents either a lyrics line or a gap indicator
 */
sealed class LyricsItem {
    data class LyricLine(val line: WordByWordLyricLine, val index: Int) : LyricsItem()
    data class Gap(val duration: Long, val startTime: Long) : LyricsItem()
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
    lastIndex: Int
) {
    val currentIndex = firstVisibleItemIndex
    val delta = abs(currentIndex - targetIndex)

    if (delta >= LARGE_SCROLL_CATCH_UP_DELTA) {
        val prePositionIndex = if (targetIndex > currentIndex) {
            (targetIndex - 1).coerceAtLeast(0)
        } else {
            (targetIndex + 1).coerceAtMost(lastIndex)
        }

        if (prePositionIndex != targetIndex) {
            scrollToItem(index = prePositionIndex, scrollOffset = scrollOffset)
        }
    }

    animateScrollToItem(index = targetIndex, scrollOffset = scrollOffset)
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
    onTapLyricsView: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // TODO: Apply syncOffset to all timestamp comparisons for manual sync adjustment
    val adjustedPlaybackTime = currentPlaybackTime + syncOffset
    
    val appSettings = remember(context) { AppSettings.getInstance(context) }
    val lyricBoldVal by appSettings.lyricBold.collectAsState()
    val lyricNoAnimationVal by appSettings.lyricNoAnimation.collectAsState()
    
    val parsedLyrics = remember(wordByWordLyrics) {
        RhythmLyricsParser.parseWordByWordLyrics(wordByWordLyrics)
    }

    val visibleLyricsLines = remember(parsedLyrics) {
        parsedLyrics
    }

    // Create items list with gaps for instrumental sections
    val lyricsItems = remember(visibleLyricsLines) {
        val vocalLines = visibleLyricsLines.mapIndexedNotNull { i, line ->
            if (line.words.any { it.text.isNotBlank() }) i to line else null
        }

        val intervals = vocalLines.zipWithNext { (_, a), (_, b) -> (b.lineTimestamp - a.lineTimestamp).coerceAtLeast(0L) }
            .filter { it > 0L }
        val medianInterval = if (intervals.isNotEmpty()) {
            val sorted = intervals.sorted()
            sorted[sorted.size / 2]
        } else {
            2000L
        }
        val longGapThreshold = maxOf(5200L, (medianInterval * 2.4f).toLong())

        val items = mutableListOf<LyricsItem>()
        vocalLines.forEachIndexed { idx, (origIdx, line) ->
            items.add(LyricsItem.LyricLine(line, origIdx))
            
            // Check for gap to next line
            if (idx < vocalLines.size - 1) {
                val nextLine = vocalLines[idx + 1].second
                val gapDuration = nextLine.lineTimestamp - line.effectiveLineEndtime()
                val isExplicit = !line.endIsImplicit
                val shouldAddGap = if (isExplicit) {
                    gapDuration >= 3000L
                } else {
                    val intervalToNext = nextLine.lineTimestamp - line.lineTimestamp
                    intervalToNext >= longGapThreshold && gapDuration >= 3000L
                }
                if (shouldAddGap) {
                    items.add(LyricsItem.Gap(gapDuration, line.effectiveLineEndtime()))
                }
            }
        }
        items
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

    // Auto-scroll to current lyric line with elastic spring animation
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && visibleLyricsLines.isNotEmpty()) {
            // Find the corresponding item index in lyricsItems
            val targetItemIndex = lyricsItems.indexOfFirst { item ->
                item is LyricsItem.LyricLine && item.index == currentLineIndex
            }

            if (targetItemIndex >= 0) {
                val offset = listState.layoutInfo.viewportSize.height / 3
                listState.animateToLyricItemWithCatchUp(
                    targetIndex = targetItemIndex,
                    scrollOffset = -offset,
                    lastIndex = lyricsItems.lastIndex
                )
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
            subtitleText = "No vocals detected in this song"
        )
    } else if (visibleLyricsLines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.word_by_word_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
            itemsIndexed(lyricsItems) { _, item ->
                when (item) {
                    is LyricsItem.LyricLine -> {
                        val line = item.line
                        val index = item.index
                        val isCurrentLine = currentLineIndex == index
                        val isUpcomingLine = index > currentLineIndex
                        val linesAhead = index - currentLineIndex
                        
                        val distanceFromCurrent = abs(index - currentLineIndex)
                        
                        // Active word index calculation
                        val activeWordIndex = if (isCurrentLine) {
                            val exactActive = line.words.indexOfLast { word ->
                                adjustedPlaybackTime >= word.timestamp && adjustedPlaybackTime <= word.endtime
                            }

                            if (exactActive >= 0) {
                                exactActive
                            } else {
                                line.words.indexOfLast { word ->
                                    adjustedPlaybackTime >= word.timestamp
                                }
                            }
                        } else {
                            -1
                        }

                        WordByWordLyricLineItem(
                            line = line,
                            index = index,
                            isCurrentLine = isCurrentLine,
                            isUpcomingLine = isUpcomingLine,
                            linesAhead = linesAhead,
                            distanceFromCurrent = distanceFromCurrent,
                            activeWordIndex = activeWordIndex,
                            adjustedPlaybackTime = adjustedPlaybackTime,
                            textSizeMultiplier = textSizeMultiplier,
                            textAlignment = textAlignment,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            onSeek = onSeek,
                            onTapLyricsView = onTapLyricsView,
                            lyricBold = lyricBoldVal,
                            noAnimation = lyricNoAnimationVal
                        )
                    }
                    is LyricsItem.Gap -> {
                        val isCurrentGap = adjustedPlaybackTime >= item.startTime &&
                            adjustedPlaybackTime < item.startTime + item.duration
                        
                        val gapHeight = (item.duration / 1000f).coerceIn(20f, 80f)
                        val iconAlpha by animateFloatAsState(
                            targetValue = if (isCurrentGap) 0.85f else 0.3f,
                            animationSpec = if (lyricNoAnimationVal) snap() else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "gapAlpha"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isCurrentGap) 1.5f else 1f,
                            animationSpec = if (lyricNoAnimationVal) snap() else spring<Float>(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessVeryLow
                            ),
                            label = "iconScale"
                        )
                        
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gapHeight.dp)
                                .padding(horizontal = 32.dp)
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "♪",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha),
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            )
                            Text(
                                text = "Instrumental",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (isCurrentGap) 0.6f else 0.25f
                                )
                            )
                        }
                        
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gapHeight.dp)
                                .padding(horizontal = 32.dp)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
    noAnimation: Boolean
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

    val activeWord = if (isCurrentLine) line.words.getOrNull(activeWordIndex) else null
    val duration = remember(activeWord) {
        activeWord?.let { (it.endtime - it.timestamp).toInt() } ?: 0
    }
    
    val animatedProgress = remember(activeWord) { Animatable(0f) }
    LaunchedEffect(activeWord) {
        if (duration > 0 && !noAnimation) {
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
        } else {
            animatedProgress.snapTo(1f)
        }
    }

    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    val wordRanges = remember(line.words) {
        val ranges = mutableListOf<IntRange>()
        var currentLen = 0
        line.words.forEachIndexed { idx, word ->
            val prefix = if (idx > 0 && !word.isPart) 1 else 0
            val start = currentLen + prefix
            val end = start + word.text.length
            ranges.add(start until end)
            currentLen = end
        }
        ranges
    }

    val annotatedText = buildAnnotatedString {
        line.words.forEachIndexed { wordIndex, word ->
            val isWordActive = isCurrentLine && wordIndex == activeWordIndex
            
            val wordAlpha = when {
                isWordActive -> 1f
                isCurrentLine -> 0.95f
                distanceFromCurrent == 1 -> 0.75f
                distanceFromCurrent == 2 -> 0.60f
                distanceFromCurrent == 3 -> 0.45f
                else -> 0.32f
            }

            val baseColor = when (line.voiceTag) {
                "v2" -> MaterialTheme.colorScheme.secondary
                "v3" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }

            val inactiveWordColor = when (line.voiceTag) {
                "v2" -> if (isCurrentLine) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary
                "v3" -> if (isCurrentLine) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiary
                else -> if (isCurrentLine) baseColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            }

            val isWordPassed = isCurrentLine && wordIndex < activeWordIndex

            val style = if (isWordActive) {
                val range = wordRanges.getOrNull(wordIndex)
                val layout = textLayoutResult
                val sweepProgress = animatedProgress.value
                if (range != null && layout != null && !noAnimation && sweepProgress < 1f) {
                    val startChar = range.first
                    val endChar = (range.last).coerceAtMost(layout.layoutInput.text.length - 1)
                    val startRect = layout.getBoundingBox(startChar)
                    val endRect = layout.getBoundingBox(endChar)
                    
                    val left = startRect.left
                    val right = endRect.right
                    
                    val activeBrush = Brush.linearGradient(
                        colorStops = arrayOf(
                            (sweepProgress - 0.15f).coerceIn(0f, 1f) to baseColor,
                            (sweepProgress + 0.15f).coerceIn(0f, 1f) to inactiveWordColor
                        ),
                        start = Offset(left, 0f),
                        end = Offset(right, 0f)
                    )
                    SpanStyle(
                        brush = activeBrush,
                        fontWeight = if (lyricBold) FontWeight.Black else FontWeight.Bold
                    )
                } else if (sweepProgress >= 1f) {
                    SpanStyle(
                        color = baseColor,
                        fontWeight = if (lyricBold) FontWeight.Black else FontWeight.Bold
                    )
                } else {
                    SpanStyle(
                        color = inactiveWordColor,
                        fontWeight = if (lyricBold) FontWeight.Black else FontWeight.Bold
                    )
                }
            } else {
                SpanStyle(
                    color = if (isWordPassed) baseColor else inactiveWordColor,
                    fontWeight = if (isCurrentLine) {
                        if (lyricBold) FontWeight.ExtraBold else FontWeight.SemiBold
                    } else {
                        if (lyricBold) FontWeight.Bold else FontWeight.Normal
                    }
                )
            }

            withStyle(style) {
                if (wordIndex > 0 && !word.isPart) {
                    append(" ")
                }
                append(word.text)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onTapLyricsView != null) {
                    onTapLyricsView()
                } else {
                    onSeek?.invoke(line.lineTimestamp)
                }
            }
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = opacity
                translationY = animatedTranslationY
            },
        horizontalAlignment = when (textAlignment) {
            TextAlign.Start -> Alignment.Start
            TextAlign.End -> Alignment.End
            else -> Alignment.CenterHorizontally
        }
    ) {
        Text(
            text = annotatedText,
            onTextLayout = { textLayoutResult = it },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize * textSizeMultiplier,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.4f * textSizeMultiplier
            ),
            textAlign = textAlignment,
            modifier = Modifier.fillMaxWidth()
        )

        val displayTranslation = chromahub.rhythm.app.util.LyricsTranslationPolicy.selectLine(
            original = line.words.joinToString(" ") { it.text },
            translation = line.translation
        )
        if (showTranslation && displayTranslation != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayTranslation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = if (isCurrentLine) {
                        if (lyricBold) FontWeight.Bold else FontWeight.SemiBold
                    } else FontWeight.Normal,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * (0.92f * textSizeMultiplier),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.32f * textSizeMultiplier
                ),
                color = MaterialTheme.colorScheme.tertiary.copy(
                    alpha = if (isCurrentLine) 0.86f else 0.62f
                ),
                textAlign = textAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(translationAlpha)
            )
        }

        if (showRomanization && !line.romanization.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.romanization,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (lyricBold) FontWeight.Medium else FontWeight.Normal,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * (0.9f * textSizeMultiplier),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f * textSizeMultiplier,
                    letterSpacing = 0.02.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isCurrentLine) 0.68f else 0.5f
                ),
                textAlign = textAlignment,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
    subtitleText: String = "Enjoy the music"
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
                    color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
