/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val SwipeToQueueThreshold = 72.dp
private val DragSelectAutoScrollZone = 56.dp
private val DragSelectMaxSpeed = 960.dp

/**
 * Pure geometry helpers for auto-scroll velocity & inclusive drag-range calculation.
 */
object DragSelectGeometry {

    fun autoScrollFraction(pointerY: Float, viewportTop: Float, viewportBottom: Float, zonePx: Float): Float {
        if (zonePx <= 0f || viewportBottom <= viewportTop) return 0f
        return when {
            pointerY < viewportTop + zonePx ->
                -((viewportTop + zonePx - pointerY) / zonePx).coerceIn(0f, 1f)
            pointerY > viewportBottom - zonePx ->
                ((pointerY - (viewportBottom - zonePx)) / zonePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun rangeIndices(anchor: Int, current: Int): IntProgression =
        if (anchor <= current) anchor..current else anchor downTo current
}

/**
 * Holds an unscrolled list at item 0 when asynchronous metadata updates re-order rows,
 * preventing sudden mid-list scroll jumps while leaving user-scrolled lists untouched.
 */
@Composable
fun KeepUnscrolledListAtTop(listState: LazyListState, rows: List<*>) {
    val tracker = remember(listState) { UnscrolledTracker(rows) }
    SideEffect {
        if (tracker.rows === rows) return@SideEffect
        tracker.rows = rows
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (atTop && !listState.isScrollInProgress) {
            listState.requestScrollToItem(0)
        }
    }
}

private class UnscrolledTracker(var rows: List<*>)

/**
 * Lets a song row slide horizontally past a threshold to enqueue the song immediately.
 * When [onSwipe] is null, zero gesture detector overhead is added.
 */
@Composable
fun SwipeToQueueBox(
    onSwipe: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    if (onSwipe == null) {
        content(modifier)
        return
    }

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { SwipeToQueueThreshold.toPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Box(modifier) {
        Icon(
            imageVector = MaterialSymbolIcon("queue_music"),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .size(22.dp)
                .graphicsLayer {
                    val progress = (offset.value / thresholdPx).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = scaleX
                }
        )
        content(
            Modifier
                .graphicsLayer { translationX = offset.value * direction }
                .pointerInput(direction, thresholdPx) {
                    var dragged = 0f
                    fun settle() {
                        dragged = 0f
                        scope.launch { offset.animateTo(0f) }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            if (dragged >= thresholdPx) {
                                currentOnSwipe()
                            }
                            settle()
                        },
                        onDragCancel = { settle() }
                    ) { change, dragAmount ->
                        val next = (dragged + dragAmount * direction).coerceIn(0f, thresholdPx * 1.35f)
                        if (dragged < thresholdPx && next >= thresholdPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        dragged = next
                        change.consume()
                        scope.launch { offset.snapTo(next) }
                    }
                }
        )
    }
}

/**
 * State coordinator connecting long-pressed item rows with the parent [LazyColumn]'s
 * [dragToSelect] auto-scrolling gesture modifier.
 */
class DragToSelectState<T : Any> {
    internal var isPointerDown = false
    internal var anchorIndex: Int? = null
    internal var anchorItem: T? = null
    internal var targetSelected: Boolean = true

    fun onItemLongPressed(index: Int, item: T, isNowSelected: Boolean) {
        if (isPointerDown) {
            anchorIndex = index
            anchorItem = item
            targetSelected = isNowSelected
        }
    }

    internal fun clear() {
        anchorIndex = null
        anchorItem = null
    }
}

@Composable
fun <T : Any> rememberDragToSelectState(): DragToSelectState<T> = remember { DragToSelectState() }

fun <T : Any> Modifier.dragToSelect(
    listState: LazyListState,
    state: DragToSelectState<T>,
    itemCount: Int,
    itemAt: (Int) -> T?,
    onDragRange: (IntRange, Boolean) -> Unit
): Modifier = this then DragToSelectElement(listState, state, itemCount, itemAt, onDragRange)

private data class DragToSelectElement<T : Any>(
    val listState: LazyListState,
    val state: DragToSelectState<T>,
    val itemCount: Int,
    val itemAt: (Int) -> T?,
    val onDragRange: (IntRange, Boolean) -> Unit
) : ModifierNodeElement<DragToSelectNode<T>>() {
    override fun create() = DragToSelectNode(listState, state, itemCount, itemAt, onDragRange)
    override fun update(node: DragToSelectNode<T>) {
        node.listState = listState
        node.state = state
        node.itemCount = itemCount
        node.itemAt = itemAt
        node.onDragRange = onDragRange
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "dragToSelect"
    }
}

private class DragToSelectNode<T : Any>(
    var listState: LazyListState,
    var state: DragToSelectState<T>,
    var itemCount: Int,
    var itemAt: (Int) -> T?,
    var onDragRange: (IntRange, Boolean) -> Unit
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

    private val nestedDispatcher = NestedScrollDispatcher()
    private val nestedConnection = object : NestedScrollConnection {}
    private var autoScrollJob: Job? = null
    private var lastAppliedIndex = -1

    init {
        delegate(nestedScrollModifierNode(nestedConnection, nestedDispatcher))
        delegate(
            SuspendingPointerInputModifierNode(
                PointerInputEventHandler { handlePointer(this) }
            )
        )
    }

    private suspend fun handlePointer(scope: PointerInputScope) = coroutineScope {
        val haptics = currentValueOf(LocalHapticFeedback)
        val density = requireDensity()
        val zonePx = with(density) { DragSelectAutoScrollZone.toPx() }
        val maxSpeedPx = with(density) { DragSelectMaxSpeed.toPx() }
        var lastPointerY = 0f

        scope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.isPointerDown = true
            state.clear()
            lastAppliedIndex = -1
            lastPointerY = down.position.y

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                    if (pointer == null || !pointer.pressed) break

                    lastPointerY = pointer.position.y
                    val anchorIdx = state.anchorIndex ?: continue

                    // Gesture has armed drag-to-select: consume move events so LazyColumn stops scrolling
                    pointer.consume()
                    val bounds = requireLayoutCoordinates().boundsInWindow()
                    val hovered = findHoveredItemIndex(lastPointerY)
                    if (hovered != null && hovered != lastAppliedIndex) {
                        lastAppliedIndex = hovered
                        val minIdx = minOf(anchorIdx, hovered)
                        val maxIdx = maxOf(anchorIdx, hovered)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDragRange(minIdx..maxIdx, state.targetSelected)
                    }

                    val velocityFraction = DragSelectGeometry.autoScrollFraction(
                        lastPointerY + bounds.top,
                        bounds.top,
                        bounds.bottom,
                        zonePx
                    )
                    if (velocityFraction != 0f) {
                        if (autoScrollJob?.isActive != true) {
                            autoScrollJob = launch {
                                var lastNanos = withFrameNanos { it }
                                while (state.anchorIndex != null) {
                                    val nowNanos = withFrameNanos { it }
                                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                                    lastNanos = nowNanos
                                    val currentBounds = requireLayoutCoordinates().boundsInWindow()
                                    val frac = DragSelectGeometry.autoScrollFraction(
                                        lastPointerY + currentBounds.top,
                                        currentBounds.top,
                                        currentBounds.bottom,
                                        zonePx
                                    )
                                    if (frac == 0f) break
                                    val delta = frac * maxSpeedPx * dt
                                    listState.scroll { scrollBy(delta) }
                                }
                            }
                        }
                    } else {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                    }
                }
            } finally {
                autoScrollJob?.cancel()
                autoScrollJob = null
                state.isPointerDown = false
                state.clear()
                lastAppliedIndex = -1
            }
        }
    }

    private fun findHoveredItemIndex(localY: Float): Int? {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return null
        val yInt = localY.toInt()
        val hit = visible.firstOrNull { info ->
            yInt in info.offset..(info.offset + info.size)
        } ?: if (yInt < visible.first().offset) visible.first() else visible.last()
        return hit.index.takeIf { it in 0 until itemCount && itemAt(it) != null }
    }
}
