/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.DragDropUtils
import chromahub.rhythm.app.util.ReorderableItemBounds
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun <T> DragDropLazyColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    itemKey: (T) -> Any,
    isReorderableItem: (T) -> Boolean = { true },
    isStickyHeader: (T) -> Boolean = { false },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 0.dp,
    animateItemPlacement: Boolean = false,
    dragTopInset: Dp = 0.dp,
    dragBottomInset: Dp = 0.dp,
    onDragStart: ((startIndex: Int) -> Unit)? = null,
    onDrop: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    itemContent: @Composable (item: T, isDragging: Boolean, index: Int) -> Unit
) {
    val edgeThresholdPx = 84f

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragTopInsetPx = dragTopInset.value * density.density
    val dragBottomInsetPx = dragBottomInset.value * density.density

    val currentItems = rememberUpdatedState(items)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentReorderable = rememberUpdatedState(isReorderableItem)
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDrop = rememberUpdatedState(onDrop)
    val currentOnDragCancel = rememberUpdatedState(onDragCancel)

    var isDragging by remember { mutableStateOf(false) }
    var isDropping by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var initialDragIndex by remember { mutableIntStateOf(-1) }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var dragRowHeightPx by remember { mutableFloatStateOf(0f) }
    var grabOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastPointerY by remember { mutableFloatStateOf(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    var reorderMoved by remember { mutableStateOf(false) }
    var dragCardTopPx by remember { mutableFloatStateOf(0f) }
    var draggedSlotTopPx by remember { mutableFloatStateOf(0f) }
    var autoscrollSwapDistance by remember { mutableFloatStateOf(0f) }
    var lastHopTimeMs by remember { mutableLongStateOf(0L) }

    fun isReorderableAt(index: Int): Boolean {
        val list = currentItems.value
        return index in list.indices && currentReorderable.value(list[index])
    }

    fun visibleInfoAtY(y: Float): LazyListItemInfo? {
        val visible = lazyListState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return null
        val direct = visible.firstOrNull { y >= it.offset && y <= it.offset + it.size }
        if (direct != null) return direct
        return visible.minByOrNull { info ->
            val mid = info.offset + info.size / 2f
            abs(y - mid)
        }
    }

    fun visibleInfoOfIndex(index: Int): LazyListItemInfo? =
        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    fun visibleInfoOfKey(key: Any?): LazyListItemInfo? =
        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    fun reorderableRange(): IntRange? {
        val list = currentItems.value
        var first = -1
        var last = -1
        for (i in list.indices) {
            if (isReorderableAt(i)) {
                if (first == -1) first = i
                last = i
            }
        }
        return if (first == -1) null else first..last
    }

    fun isDragLayoutSynced(): Boolean {
        val info = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }
        if (info != null) {
            return info.index == draggedIndex
        }
        val visible = lazyListState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return true
        val minIdx = visible.minOf { it.index }
        val maxIdx = visible.maxOf { it.index }
        return draggedIndex < minIdx || draggedIndex > maxIdx
    }

    fun finishDrag(cancelled: Boolean = false) {
        val from = initialDragIndex
        val to = draggedIndex
        val moved = reorderMoved
        isDragging = false
        isDropping = false
        draggedIndex = -1
        draggedKey = null
        initialDragIndex = -1
        dragCardTopPx = 0f
        draggedSlotTopPx = 0f
        if (cancelled) {
            reorderMoved = false
            currentOnDragCancel.value?.invoke()
        } else if (moved) {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
            reorderMoved = false
            if (from != -1 && to != -1 && from != to) {
                currentOnDrop.value?.invoke(from, to)
            }
        }
    }

    suspend fun advanceDragFrame() {
        val range = reorderableRange() ?: return
        if (draggedIndex !in range) return

        val topLimit = dragTopInsetPx
        val bottomLimit = (boxHeightPx - dragBottomInsetPx - dragRowHeightPx).coerceAtLeast(topLimit)
        val desiredTop = lastPointerY - grabOffsetPx
        val cardTop = desiredTop.coerceIn(topLimit, bottomLimit)
        dragCardTopPx = cardTop

        if (lazyListState.firstVisibleItemIndex > range.first && (draggedIndex == range.first || cardTop <= topLimit + edgeThresholdPx)) {
            lazyListState.requestScrollToItem(range.first, 0)
            lazyListState.scrollToItem(range.first, 0)
        }

        val pushUp = (topLimit + edgeThresholdPx - desiredTop).coerceAtLeast(0f)
        val pushDown = (desiredTop - (bottomLimit - edgeThresholdPx)).coerceAtLeast(0f)

        val layout = lazyListState.layoutInfo
        val visibleReorderable = layout.visibleItemsInfo.filter { isReorderableAt(it.index) }
        val firstReorderable = visibleReorderable.firstOrNull { it.index == range.first }
        val lastReorderable = visibleReorderable.firstOrNull { it.index == range.last }

        val canScrollUp = DragDropUtils.canScrollUp(
            draggedIndex = draggedIndex,
            rangeFirst = range.first,
            canScrollBackward = lazyListState.canScrollBackward,
            firstItemOffset = firstReorderable?.offset?.toFloat(),
            topLimit = topLimit
        )

        val canScrollDown = DragDropUtils.canScrollDown(
            draggedIndex = draggedIndex,
            rangeLast = range.last,
            canScrollForward = lazyListState.canScrollForward,
            lastItemBottom = lastReorderable?.let { (it.offset + it.size).toFloat() },
            bottomLimit = boxHeightPx - dragBottomInsetPx
        )

        val maxScrollPerFrame = dragRowHeightPx * 0.11f
        val scrollSpeed = when {
            pushUp > 0f && canScrollUp ->
                -maxScrollPerFrame * (pushUp / edgeThresholdPx).coerceIn(0f, 1f)

            pushDown > 0f && canScrollDown ->
                maxScrollPerFrame * (pushDown / edgeThresholdPx).coerceIn(0f, 1f)

            else -> 0f
        }

        if (scrollSpeed != 0f) {
            lazyListState.scrollBy(scrollSpeed)
            autoscrollSwapDistance += abs(scrollSpeed)
        }

        val freshLayout = lazyListState.layoutInfo
        val freshVisibleReorderable = freshLayout.visibleItemsInfo.filter { isReorderableAt(it.index) }
        val draggedInfo = freshLayout.visibleItemsInfo.firstOrNull { it.key == draggedKey }
        if (draggedInfo != null) {
            draggedSlotTopPx = draggedInfo.offset.toFloat()
        }

        val cardCenterY = cardTop + dragRowHeightPx / 2f
        val firstReorderableItem = freshVisibleReorderable.firstOrNull { it.index == range.first }
        val firstItemBottom = firstReorderableItem?.let { (it.offset + it.size).toFloat() } ?: Float.MAX_VALUE

        val targetIndex = if (scrollSpeed != 0f) {
            DragDropUtils.computeAutoscrollHop(
                scrollSpeed = scrollSpeed,
                draggedIndex = draggedIndex,
                range = range,
                autoscrollDistance = autoscrollSwapDistance,
                rowHeight = dragRowHeightPx,
                paceFraction = 0.85f
            )
        } else if (cardTop <= topLimit + 16f * density.density || (draggedIndex == range.first && cardCenterY <= firstItemBottom)) {
            range.first
        } else if (freshVisibleReorderable.isNotEmpty()) {
            val bounds = freshVisibleReorderable.map {
                ReorderableItemBounds(it.index, it.offset.toFloat(), it.size.toFloat())
            }
            DragDropUtils.computeManualTargetIndex(
                cardCenterY = cardCenterY,
                draggedIndex = draggedIndex,
                range = range,
                visibleReorderable = bounds,
                hysteresisBonus = dragRowHeightPx * 0.22f
            )
        } else {
            draggedIndex
        }

        if (isDragLayoutSynced()) {
            val hopReady = scrollSpeed != 0f ||
                SystemClock.elapsedRealtime() - lastHopTimeMs >= 40L
            if (targetIndex != draggedIndex && targetIndex in range &&
                isReorderableAt(targetIndex) && hopReady
            ) {
                val wasAtTop = lazyListState.firstVisibleItemIndex <= range.first &&
                    lazyListState.firstVisibleItemScrollOffset == 0
                reorderMoved = true
                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                autoscrollSwapDistance = 0f
                lastHopTimeMs = SystemClock.elapsedRealtime()
                currentOnMove.value(draggedIndex, targetIndex)
                draggedIndex = targetIndex

                if (targetIndex == range.first || wasAtTop) {
                    lazyListState.requestScrollToItem(range.first, 0)
                    lazyListState.scrollToItem(range.first, 0)
                }
            }
        }
    }

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (isDragging && !isDropping) {
            advanceDragFrame()
            withFrameNanos { }
        }
    }

    fun settleDrop(cancelled: Boolean = false) {
        if (!isDragging || isDropping) return
        isDropping = true
        scope.launch {
            val range = reorderableRange()
            if (range != null && draggedIndex == range.first && lazyListState.firstVisibleItemIndex > range.first) {
                lazyListState.requestScrollToItem(range.first, 0)
                lazyListState.scrollToItem(range.first, 0)
            }
            for (i in 0 until 10) {
                withFrameNanos { }
                if (!lazyListState.isScrollInProgress && isDragLayoutSynced()) break
            }
            val slotTop = visibleInfoOfKey(draggedKey)?.offset?.toFloat()
            if (slotTop != null && abs(dragCardTopPx - slotTop) > 1f) {
                val startTop = dragCardTopPx
                val durationNanos = 160_000_000L
                val startNanos = withFrameNanos { it }
                while (true) {
                    val elapsed = withFrameNanos { it } - startNanos
                    val fraction = (elapsed.toFloat() / durationNanos).coerceIn(0f, 1f)
                    dragCardTopPx = startTop + (slotTop - startTop) * fraction
                    if (fraction >= 1f) break
                }
            }
            finishDrag(cancelled)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (isDragging || isDropping) return@detectDragGesturesAfterLongPress
                        val info = visibleInfoAtY(offset.y) ?: return@detectDragGesturesAfterLongPress
                        if (!isReorderableAt(info.index)) return@detectDragGesturesAfterLongPress
                        draggedIndex = info.index
                        initialDragIndex = info.index
                        draggedKey = itemKey(currentItems.value[info.index])
                        dragRowHeightPx = info.size.toFloat()
                        grabOffsetPx = (offset.y - info.offset).coerceIn(0f, info.size.toFloat())
                        lastPointerY = offset.y
                        reorderMoved = false
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                        dragCardTopPx = info.offset.toFloat()
                        draggedSlotTopPx = info.offset.toFloat()
                        autoscrollSwapDistance = 0f
                        isDragging = true
                        currentOnDragStart.value?.invoke(info.index)
                    },
                    onDrag = { change, _ ->
                        if (isDragging && !isDropping) {
                            change.consume()
                            lastPointerY = change.position.y
                        }
                    },
                    onDragEnd = {
                        settleDrop(cancelled = false)
                    },
                    onDragCancel = {
                        settleDrop(cancelled = true)
                    }
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            state = lazyListState
        ) {
            items.forEachIndexed { index, item ->
                val key = itemKey(item)
                if (isStickyHeader(item)) {
                    stickyHeader(key = key) {
                        itemContent(item, false, index)
                    }
                } else {
                    item(key = key) {
                        val isCurrentlyDragged = isDragging && key == draggedKey

                        val placementModifier = if (animateItemPlacement && !isCurrentlyDragged) {
                            Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 0),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                fadeOutSpec = tween(durationMillis = 0)
                            )
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .zIndex(if (isCurrentlyDragged) 1f else 0f)
                                .then(placementModifier)
                                .graphicsLayer {
                                    alpha = if (isCurrentlyDragged) 0f else 1f
                                }
                        ) {
                            itemContent(item, false, index)
                        }
                    }
                }
            }
        }

        if (isDragging && draggedKey != null) {
            val draggedItem = currentItems.value.firstOrNull { itemKey(it) == draggedKey }
                ?: currentItems.value.getOrNull(draggedIndex)
            if (draggedItem != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(100f)
                        .graphicsLayer {
                            translationY = dragCardTopPx
                        }
                ) {
                    itemContent(draggedItem, true, draggedIndex)
                }
            }
        }

    }
}
