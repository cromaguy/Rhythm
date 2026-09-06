/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.derivedStateOf
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.DragDropLazyColumn
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledTonalIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.ImageUtils
import androidx.compose.ui.res.stringResource


private data class QueueItemCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
) {
    fun toShape(): RoundedCornerShape =
        RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

private fun groupedQueueItemCorners(index: Int, totalCount: Int): QueueItemCorners {
    if (totalCount <= 1) return QueueItemCorners(24.dp, 24.dp, 24.dp, 24.dp)

    return when (index) {
        0 -> QueueItemCorners(24.dp, 24.dp, 6.dp, 6.dp)
        totalCount - 1 -> QueueItemCorners(6.dp, 6.dp, 24.dp, 24.dp)
        else -> QueueItemCorners(6.dp, 6.dp, 6.dp, 6.dp)
    }
}


private enum class QueueSectionLabel { PLAYED, UP_NEXT }

private data class QueueSongRow(
    val position: Int,
    val displayNumber: Int,
    val song: Song,
    val isPlayed: Boolean,
    val corners: QueueItemCorners,
    val stableKey: String
)

private sealed interface QueueListRow {
    data class Section(val label: QueueSectionLabel) : QueueListRow
    data class Song(val row: QueueSongRow) : QueueListRow
}

private fun queueEntryKey(queue: List<Song>, position: Int): String {
    val song = queue[position]
    var ordinal = 0
    for (i in 0..position) {
        if (queue[i].id == song.id) ordinal++
    }
    return "${song.id}@$ordinal"
}

private fun queueListRowKey(row: QueueListRow): String = when (row) {
    is QueueListRow.Section -> "section_${row.label}"
    is QueueListRow.Song -> row.row.stableKey
}

private fun buildQueueListRows(
    visibleQueue: List<Pair<Int, Song>>,
    queue: List<Song>,
    currentSongIndex: Int,
    showPlayedSongs: Boolean
): List<QueueListRow> = buildList {
    val played = visibleQueue.filter { it.first < currentSongIndex }
    val upcoming = visibleQueue.filter { it.first > currentSongIndex }

    if (showPlayedSongs && played.isNotEmpty()) {
        add(QueueListRow.Section(QueueSectionLabel.PLAYED))
        played.forEachIndexed { index, (position, song) ->
            add(
                QueueListRow.Song(
                    QueueSongRow(position, 0, song, true, groupedQueueItemCorners(index, played.size), queueEntryKey(queue, position))
                )
            )
        }
    }
    if (upcoming.isNotEmpty()) {
        if (played.isNotEmpty()) add(QueueListRow.Section(QueueSectionLabel.UP_NEXT))
        upcoming.forEachIndexed { index, (position, song) ->
            add(
                QueueListRow.Song(
                    QueueSongRow(position, index + 1, song, false, groupedQueueItemCorners(index, upcoming.size), queueEntryKey(queue, position))
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    currentSong: Song?,
    queue: List<Song>,
    currentQueueIndex: Int = 0,
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onSongClick: (Song) -> Unit,
    onSongClickAtIndex: (Int) -> Unit = { _ -> },
    onDismiss: () -> Unit,
    onRemoveSongAtIndex: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onAddSongsClick: () -> Unit = {},
    onClearQueue: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
) {
    val context = LocalContext.current
    val appSettings = remember(context) { AppSettings.getInstance(context) }
    val hidePlayedQueueSongs by appSettings.hidePlayedQueueSongs.collectAsState()
    val gestureQueueSwipeToRemove by appSettings.gestureQueueSwipeToRemove.collectAsState()
    val showAlreadyPlayedSongsInQueue = !hidePlayedQueueSongs

    var queueEpoch by remember { mutableIntStateOf(0) }
    var clearRequested by remember { mutableStateOf(false) }
    val clearFadeAlpha by animateFloatAsState(
        targetValue = if (clearRequested) 0f else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "queueClearFade"
    )

    val removingQueueKeys = remember { mutableStateMapOf<String, Boolean>() }
    val queueRowScope = rememberCoroutineScope()

    fun dismissQueueRow(rowKey: String, onRemoved: () -> Unit) {
        if (removingQueueKeys[rowKey] == true) return
        removingQueueKeys[rowKey] = true
        val epochAtDismiss = queueEpoch
        queueRowScope.launch {
            delay(300)
            removingQueueKeys.remove(rowKey)
            if (queueEpoch != epochAtDismiss) return@launch
            onRemoved()
        }
    }

    // Use the queue directly for display, create mutable version only for reordering operations
    val displayQueue = queue
    val mutableQueue = remember { mutableStateListOf<Song>() }
    
    // Update mutableQueue when displayQueue changes
    LaunchedEffect(displayQueue) {
        mutableQueue.clear()
        mutableQueue.addAll(displayQueue)
        Log.d("QueueBottomSheet", "Updated displayQueue with ${displayQueue.size} songs")
        Log.d("QueueBottomSheet", "First 5 songs in displayQueue:")
        displayQueue.take(5).forEachIndexed { idx, song ->
            Log.d("QueueBottomSheet", "  $idx: ${song.title} by ${song.artist}")
        }
    }

    fun removeQueueSong(song: Song) {
        val position = mutableQueue.indexOfFirst { it.id == song.id }
        if (position < 0 || position >= mutableQueue.size) return
        mutableQueue.removeAt(position)
        onRemoveSongAtIndex(position)
    }

    LaunchedEffect(clearRequested) {
        if (clearRequested) {
            for (i in displayQueue.indices) {
                removingQueueKeys[queueEntryKey(displayQueue, i)] = true
            }
            if (displayQueue.isNotEmpty()) {
                delay(280)
            }
            removingQueueKeys.clear()
            onClearQueue()
            clearRequested = false
        }
    }

    val lazyListState = rememberLazyListState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        lazyListState = lazyListState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header with title and actions
            QueueHeader(
                queueSize = displayQueue.size,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                onAddSongsClick = onAddSongsClick,
                onClearQueue = if (displayQueue.isNotEmpty()) {
                    {
                        queueEpoch++
                        clearRequested = true
                    }
                } else null,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Queue settings info and warnings
            if (displayQueue.isNotEmpty() && hidePlayedQueueSongs) {
                Column(
                    modifier = Modifier.graphicsLayer { alpha = clearFadeAlpha }
                ) {
                    QueueSettingsInfo(
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        hidePlayedSongs = hidePlayedQueueSongs,
                        queueSize = displayQueue.size
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            if (displayQueue.isEmpty()) {
                EmptyQueueContent()
            } else {
                // Now Playing section - show current song separately
                currentSong?.let { song ->
                    NowPlayingCard(
                        song = song,
                        onClick = { onSongClick(song) },
                        modifier = Modifier.graphicsLayer { alpha = clearFadeAlpha }
                    )
                }
                
                val currentSongIndexInQueue = remember(currentSong, displayQueue, currentQueueIndex) {
                    if (currentSong != null && currentQueueIndex in displayQueue.indices && displayQueue[currentQueueIndex].id == currentSong.id) {
                        currentQueueIndex
                    } else if (currentSong != null) {
                        displayQueue.indexOfFirst { it.id == currentSong.id }.takeIf { it >= 0 } ?: currentQueueIndex
                    } else {
                        currentQueueIndex
                    }
                }.coerceIn(0, displayQueue.lastIndex.coerceAtLeast(0))
                val isRepeatAll = repeatMode == Player.REPEAT_MODE_ALL
                val shouldHidePlayedSongs = !showAlreadyPlayedSongsInQueue && !isShuffleEnabled
                // Build visible queue according to current playback behavior.
                val visibleQueue = if (isShuffleEnabled) {
                    val upcomingInCurrentCycle =
                        ((currentSongIndexInQueue + 1)..displayQueue.lastIndex).map { index ->
                            index to displayQueue[index]
                        }
                    val wrappedForRepeatAll =
                        if (isRepeatAll && currentSongIndexInQueue > 0) {
                            (0 until currentSongIndexInQueue).map { index ->
                                index to displayQueue[index]
                            }
                        } else {
                            emptyList()
                        }
                    upcomingInCurrentCycle + wrappedForRepeatAll
                } else {
                    displayQueue.mapIndexedNotNull { index, song ->
                        if (shouldHidePlayedSongs && index < currentSongIndexInQueue) return@mapIndexedNotNull null
                        if (index == currentSongIndexInQueue) null else index to song
                    }
                }

                val queueListRows = if (isShuffleEnabled) {
                    visibleQueue.mapIndexed { index, queueItem ->
                        QueueListRow.Song(
                            QueueSongRow(
                                position = queueItem.first,
                                displayNumber = index + 1,
                                song = queueItem.second,
                                isPlayed = false,
                                corners = groupedQueueItemCorners(index, visibleQueue.size),
                                stableKey = queueEntryKey(displayQueue, queueItem.first)
                            )
                        )
                    }
                } else {
                    buildQueueListRows(visibleQueue, displayQueue, currentSongIndexInQueue, showAlreadyPlayedSongsInQueue)
                }
                val hasPlayedSection = queueListRows.any {
                    it is QueueListRow.Section && it.label == QueueSectionLabel.PLAYED
                }

                if (visibleQueue.isNotEmpty()) {
                    if (!hasPlayedSection) {
                        Text(
                            text = context.getString(R.string.bottomsheet_up_next),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .graphicsLayer { alpha = clearFadeAlpha }
                                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp)
                        )
                    }
                    
                    var hasScrolledOnOpening by remember { mutableStateOf(false) }

                    LaunchedEffect(queueListRows, currentSongIndexInQueue) {
                        if (!hasScrolledOnOpening && queueListRows.isNotEmpty()) {
                            while (lazyListState.layoutInfo.visibleItemsInfo.isEmpty()) {
                                delay(10)
                            }
                            val firstUpcomingIndex = queueListRows.indexOfFirst {
                                it is QueueListRow.Song && it.row.position > currentSongIndexInQueue
                            }
                            if (firstUpcomingIndex > 0) {
                                lazyListState.scrollToItem(firstUpcomingIndex - 1)
                            }
                            hasScrolledOnOpening = true
                        }
                    }

                    AdaptiveSheetScrollContainer(
                        lazyListState = lazyListState,
                        enableBlend = !hasPlayedSection,
                        blendColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrollBarPaddingEnd = 4.dp,
                        scrollBarPaddingTop = 8.dp,
                        scrollBarPaddingBottom = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .graphicsLayer { alpha = clearFadeAlpha }
                    ) { endPadding ->
                        val rowEndPadding = 16.dp + (endPadding * (20f / 12f))
                        if (isShuffleEnabled) {
                            // When shuffle is enabled, show queue but disable reordering
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(
                                    items = queueListRows,
                                    key = { _, row -> queueListRowKey(row) }
                                ) { index, row ->
                                    QueueListRowContent(
                                        row = row,
                                        isDragging = false,
                                        reorderSupported = false,
                                        endPadding = rowEndPadding,
                                        isRemoving = { key -> removingQueueKeys[key] == true },
                                        enableSwipeToRemove = gestureQueueSwipeToRemove,
                                        onSongClickAtIndex = onSongClickAtIndex,
                                        onRequestRemove = { songRow, itemKey ->
                                            dismissQueueRow(itemKey) { removeQueueSong(songRow.song) }
                                        },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = tween(0),
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            fadeOutSpec = tween(0)
                                        )
                                    )
                                }
                            }
                        } else {
                            // Normal drag and drop when shuffle is disabled
                            DragDropLazyColumn(
                                items = queueListRows,
                                modifier = Modifier.fillMaxWidth(),
                                lazyListState = lazyListState,
                                onMove = { fromIndex, toIndex ->
                                    val fromRow = (queueListRows[fromIndex] as QueueListRow.Song).row
                                    val toRow = (queueListRows[toIndex] as QueueListRow.Song).row
                                    onMoveQueueItem(fromRow.position, toRow.position)
                                },
                                itemKey = { row -> queueListRowKey(row) },
                                isReorderableItem = { row ->
                                    row is QueueListRow.Song && !row.row.isPlayed
                                },
                                isStickyHeader = { row -> row is QueueListRow.Section },
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                                itemSpacing = 4.dp,
                                animateItemPlacement = true,
                                dragTopInset = 24.dp,
                                dragBottomInset = 24.dp
                            ) { row, isDragging, _ ->
                                QueueListRowContent(
                                    row = row,
                                    isDragging = isDragging,
                                    reorderSupported = true,
                                    endPadding = rowEndPadding,
                                    isRemoving = { key -> removingQueueKeys[key] == true },
                                    enableSwipeToRemove = gestureQueueSwipeToRemove,
                                    onSongClickAtIndex = onSongClickAtIndex,
                                    onRequestRemove = { songRow, itemKey ->
                                        dismissQueueRow(itemKey) { removeQueueSong(songRow.song) }
                                    }
                                )
                            }
                        }
                    }
                } else if (currentSong != null) {
                    // Show empty up next content when only current song is in queue
                    // Add more spacing before empty state
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    EmptyUpNextContent()
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(
    queueSize: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onAddSongsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClearQueue: (() -> Unit)? = null,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = context.getString(R.string.bottomsheet_queue),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (queueSize > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        text = if (queueSize == 1) "1 song" else "$queueSize songs",
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Shuffle + repeat connected toggle group (delete stays separate)
            RhythmGroupedButton(
                modifier = Modifier.width(100.dp),
                isFillMaxWidth = false,
                size = RhythmButtonSize.Medium
            ) {
                RhythmButtonWeighted(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onToggleShuffle()
                    },
                    weight = 1f,
                    size = RhythmButtonSize.Medium,
                    type = RhythmButtonType.Tonal,
                    icon = RhythmIcons.Shuffle,
                    iconSize = 20.dp,
                    contentDescription = if (isShuffleEnabled) "Disable shuffle" else "Enable shuffle",
                    isFirst = true,
                    isLast = false,
                    expandSlotWhenSelected = false,
                    containerColor = if (isShuffleEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isShuffleEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                val repeatEnabled = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RhythmIcons.RepeatOne
                    else -> RhythmIcons.Repeat
                }

                RhythmButtonWeighted(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onToggleRepeat()
                    },
                    weight = 1f,
                    size = RhythmButtonSize.Medium,
                    type = RhythmButtonType.Tonal,
                    icon = repeatIcon,
                    iconSize = 20.dp,
                    contentDescription = if (repeatEnabled) "Disable repeat" else "Enable repeat",
                    isFirst = false,
                    isLast = true,
                    expandSlotWhenSelected = false,
                    containerColor = if (repeatEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (repeatEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Clear queue button (only show if queue is not empty)
            onClearQueue?.let { clearAction ->
                FilledTonalIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                        clearAction()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Delete,
                        contentDescription = stringResource(R.string.content_desc_clear_queue),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Subtle pulsing animation for the Now Playing indicator
    val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val cardShape = RoundedCornerShape(
        topStart = 26.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 30.dp
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .apply(ImageUtils.buildImageRequest(
                            song.artworkUri,
                            song.title,
                            LocalContext.current.cacheDir,
                            M3PlaceholderType.TRACK
                        ))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = context.getString(R.string.now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Playing indicator with pulse animation
            Icon(
                imageVector = RhythmIcons.MusicNote,
                contentDescription = stringResource(R.string.content_desc_now_playing),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = pulseAlpha),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QueueItem(
    song: Song,
    displayNumber: Int,
    corners: QueueItemCorners,
    isPlayed: Boolean,
    isDragging: Boolean,
    onSongClick: () -> Unit,
    onRemove: () -> Unit,
    showDragHandle: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Primary drag fill like the Library Songs active row (flips a frame after drag so it animates from rest).
    var dragActive by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        dragActive = isDragging
    }

    val cardColor by animateColorAsState(
        targetValue = when {
            dragActive -> MaterialTheme.colorScheme.primary
            isPlayed -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "queueItemCardColor"
    )

    val titleColor by animateColorAsState(
        targetValue = when {
            dragActive -> MaterialTheme.colorScheme.onPrimary
            isPlayed -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 300),
        label = "queueItemTitleColor"
    )

    val subtitleColor by animateColorAsState(
        targetValue = when {
            dragActive -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            isPlayed -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "queueItemSubtitleColor"
    )

    val liftedCorner = 24.dp
    // Ramp the shadow only after the drag morph settles so it isn't clipped mid-morph.
    var liftShadowReady by remember { mutableStateOf(false) }
    LaunchedEffect(dragActive) {
        if (!dragActive) liftShadowReady = false
    }
    val animatedTopStart by animateDpAsState(
        targetValue = if (dragActive) liftedCorner else corners.topStart,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "queueItemTopStartCorner",
        finishedListener = { if (dragActive) liftShadowReady = true }
    )
    val animatedTopEnd by animateDpAsState(
        targetValue = if (dragActive) liftedCorner else corners.topEnd,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "queueItemTopEndCorner"
    )
    val animatedBottomStart by animateDpAsState(
        targetValue = if (dragActive) liftedCorner else corners.bottomStart,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "queueItemBottomStartCorner"
    )
    val animatedBottomEnd by animateDpAsState(
        targetValue = if (dragActive) liftedCorner else corners.bottomEnd,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "queueItemBottomEndCorner"
    )
    val animatedShape = RoundedCornerShape(
        topStart = animatedTopStart,
        topEnd = animatedTopEnd,
        bottomEnd = animatedBottomEnd,
        bottomStart = animatedBottomStart
    )

    val animatedShadow by animateDpAsState(
        targetValue = if (liftShadowReady) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 400),
        label = "queueItemShadowElevation"
    )

    val songArtShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.SONG_ART,
        fallbackShape = RoundedCornerShape(8.dp)
    )

    Surface(
        onClick = onSongClick,
        modifier = Modifier.fillMaxWidth(),
        shape = animatedShape,
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (dragActive) 0.dp else 1.dp,
        shadowElevation = animatedShadow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isPlayed -> MaterialTheme.colorScheme.tertiaryContainer
                    dragActive -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isPlayed) {
                        Icon(
                            imageVector = RhythmIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "$displayNumber",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (dragActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Album art
            Surface(
                modifier = Modifier.size(48.dp),
                shape = songArtShape,
                tonalElevation = 2.dp,
                border = if (dragActive) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary)
                } else {
                    null
                }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .apply(ImageUtils.buildImageRequest(
                            song.artworkUri,
                            song.title,
                            context.cacheDir,
                            M3PlaceholderType.TRACK
                        ))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (showDragHandle) {
                Icon(
                    imageVector = RhythmIcons.DragHandle,
                    contentDescription = stringResource(R.string.content_desc_drag_reorder),
                    tint = when {
                        dragActive -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        isPlayed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(20.dp)
                )
                        
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Remove button with hover effect
            var isPressed by remember { mutableStateOf(false) }
            val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.9f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "buttonScale"
            )
            
            ExpressiveFilledTonalIconButton(
                onClick = {
                    isPressed = true
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                    onRemove()
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                ) {
                Icon(
                    imageVector = MaterialSymbolIcon("clear"),
                    contentDescription = stringResource(R.string.content_desc_remove_from_queue),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleQueueItem(
    song: Song,
    queuePosition: Int,
    displayNumber: Int,
    isPlayed: Boolean,
    isDragging: Boolean,
    showDragHandle: Boolean,
    itemShape: RoundedCornerShape,
    corners: QueueItemCorners,
    endPadding: Dp,
    isRemoving: Boolean,
    onSongClickAtIndex: (Int) -> Unit,
    onRequestRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeToRemove: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val rowDismissAlpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(280),
        label = "queueRowDismissAlpha"
    )

    Box(
        modifier = modifier
            .padding(
                start = 16.dp,
                end = endPadding
            )
            .graphicsLayer {
                alpha = rowDismissAlpha
            }
    ) {
        val dismissState = rememberSwipeToDismissBoxState()

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = enableSwipeToRemove,
            enableDismissFromEndToStart = enableSwipeToRemove,
            onDismiss = {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                onRequestRemove()
            },
            backgroundContent = {
                if (enableSwipeToRemove && dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(itemShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 20.dp),
                        contentAlignment = alignment
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Delete,
                            contentDescription = stringResource(R.string.content_desc_remove_from_queue),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        ) {
            AnimateIn {
                QueueItem(
                    song = song,
                    displayNumber = displayNumber,
                    corners = corners,
                    isPlayed = isPlayed,
                    isDragging = isDragging,
                    onSongClick = {
                        onSongClickAtIndex(queuePosition)
                    },
                    onRemove = onRequestRemove,
                    showDragHandle = showDragHandle
                )
            }
        }
    }
}

@Composable
private fun QueueListRowContent(
    row: QueueListRow,
    isDragging: Boolean,
    reorderSupported: Boolean,
    endPadding: Dp,
    isRemoving: (String) -> Boolean,
    onSongClickAtIndex: (Int) -> Unit,
    onRequestRemove: (QueueSongRow, String) -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeToRemove: Boolean = true
) {
    when (row) {
        is QueueListRow.Section -> {
            Box(modifier = modifier) {
                QueueSectionHeader(row.label)
            }
        }

        is QueueListRow.Song -> {
            val songRow = row.row
            val itemKey = queueListRowKey(row)
            DismissibleQueueItem(
                song = songRow.song,
                queuePosition = songRow.position,
                displayNumber = songRow.displayNumber,
                isPlayed = songRow.isPlayed,
                isDragging = isDragging,
                showDragHandle = reorderSupported && !songRow.isPlayed,
                itemShape = songRow.corners.toShape(),
                corners = songRow.corners,
                endPadding = endPadding,
                isRemoving = isRemoving(itemKey),
                onSongClickAtIndex = onSongClickAtIndex,
                onRequestRemove = { onRequestRemove(songRow, itemKey) },
                enableSwipeToRemove = enableSwipeToRemove,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun QueueSectionHeader(label: QueueSectionLabel) {
    val context = LocalContext.current
    val text = when (label) {
        QueueSectionLabel.PLAYED -> context.getString(R.string.queue_section_played)
        QueueSectionLabel.UP_NEXT -> context.getString(R.string.bottomsheet_up_next)
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun QueueSettingsInfo(
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    hidePlayedSongs: Boolean,
    queueSize: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hide played songs info
        if (hidePlayedSongs && queueSize > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.queuebottomsheet_played_songs_are_hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimateIn(
    modifier: Modifier = Modifier,
    delay: Int = 50,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = 0),
        label = "alpha"
    )

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )

    val translationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "translationY"
    )

    Box(
        modifier = modifier.graphicsLayer(
            alpha = alpha,
            scaleX = scale,
            scaleY = scale,
            translationY = translationY
        )
    ) {
        content()
    }
}

@Composable
private fun EmptyQueueContent(
    modifier: Modifier = Modifier
) {
    QueueEmptyState(
        title = stringResource(R.string.queue_empty),
        subtitle = stringResource(R.string.queue_add_songs),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    )
}

@Composable
private fun EmptyUpNextContent(
    modifier: Modifier = Modifier
) {
    QueueEmptyState(
        title = stringResource(R.string.queue_no_more),
        subtitle = stringResource(R.string.queue_add_more),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
        height = 260.dp
    )
}

@Composable
private fun QueueEmptyState(
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveCookieEmptyState(
            title = title,
            subtitle = subtitle,
            mainIcon = RhythmIcons.Queue,
            accentIcon = RhythmIcons.MusicNote,
            cornerIcon = RhythmIcons.MusicNote,
            containerColor = containerColor,
            contentColor = contentColor
        )
    }
}
