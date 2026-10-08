/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import kotlin.math.abs

/**
 * Data class representing the visual bounds of a reorderable item in pixels.
 */
data class ReorderableItemBounds(
    val index: Int,
    val offset: Float,
    val size: Float
) {
    val midpoint: Float get() = offset + size / 2f
    val bottom: Float get() = offset + size
}

/**
 * Pure mathematical utilities for drag-and-drop list reordering and autoscrolling.
 */
object DragDropUtils {

    /**
     * Determines whether the list is allowed to autoscroll upward (backward).
     *
     * Permitted when:
     * 1. The dragged item is not already at the top of the reorderable range.
     * 2. The scroll container can scroll backward.
     * 3. The first reorderable item is either not yet visible or partially scrolled off above [topLimit].
     */
    fun canScrollUp(
        draggedIndex: Int,
        rangeFirst: Int,
        canScrollBackward: Boolean,
        firstItemOffset: Float?,
        topLimit: Float
    ): Boolean {
        if (draggedIndex <= rangeFirst) return false
        if (!canScrollBackward) return false
        if (firstItemOffset == null) return true
        return if (rangeFirst == 0) {
            canScrollBackward
        } else {
            firstItemOffset < topLimit
        }
    }

    /**
     * Determines whether the list is allowed to autoscroll downward (forward).
     *
     * Permitted when:
     * 1. The dragged item is not already at the bottom of the reorderable range.
     * 2. The scroll container can scroll forward.
     * 3. The last reorderable item is either not yet visible or extends below [bottomLimit].
     */
    fun canScrollDown(
        draggedIndex: Int,
        rangeLast: Int,
        canScrollForward: Boolean,
        lastItemBottom: Float?,
        bottomLimit: Float
    ): Boolean {
        if (draggedIndex >= rangeLast) return false
        if (!canScrollForward) return false
        return lastItemBottom == null || lastItemBottom > bottomLimit
    }

    /**
     * Determines the target index during manual drag based on the center Y coordinate of the dragged card.
     *
     * Guarantees:
     * - Dragging at or above the midpoint of the first visible item maps to [range.first] if that item is [range.first].
     * - If [draggedIndex] is already at or above [firstVis.index], it is never pushed down.
     * - Dragging at or below the midpoint of the last visible item maps to [range.last] if that item is [range.last].
     * - If [draggedIndex] is already at or below [lastVis.index], it is never pushed up.
     * - Dragging between visible items selects the item whose center is closest to [cardCenterY],
     *   with [hysteresisBonus] giving the current [draggedIndex] stability against boundary jitter.
     */
    fun computeManualTargetIndex(
        cardCenterY: Float,
        draggedIndex: Int,
        range: IntRange,
        visibleReorderable: List<ReorderableItemBounds>,
        hysteresisBonus: Float = 0f
    ): Int {
        if (visibleReorderable.isEmpty()) return draggedIndex

        val firstVis = visibleReorderable.first()
        val lastVis = visibleReorderable.last()

        return when {
            cardCenterY <= firstVis.midpoint -> {
                when {
                    firstVis.index == range.first -> range.first
                    draggedIndex <= firstVis.index -> draggedIndex
                    else -> firstVis.index
                }
            }
            cardCenterY >= lastVis.midpoint -> {
                when {
                    lastVis.index == range.last -> range.last
                    draggedIndex >= lastVis.index -> draggedIndex
                    else -> lastVis.index
                }
            }
            else -> {
                visibleReorderable.minByOrNull { item ->
                    val dist = abs(cardCenterY - item.midpoint)
                    if (item.index == draggedIndex) dist - hysteresisBonus else dist
                }?.index ?: draggedIndex
            }
        }
    }

    /**
     * Computes the single-slot hop candidate during autoscroll.
     * Hops are paced by [autoscrollDistance] reaching at least [rowHeight] * [paceFraction].
     */
    fun computeAutoscrollHop(
        scrollSpeed: Float,
        draggedIndex: Int,
        range: IntRange,
        autoscrollDistance: Float,
        rowHeight: Float,
        paceFraction: Float = 0.75f
    ): Int {
        if (scrollSpeed == 0f) return draggedIndex
        if (rowHeight <= 0f || autoscrollDistance < rowHeight * paceFraction) return draggedIndex

        val candidate = if (scrollSpeed < 0f) draggedIndex - 1 else draggedIndex + 1
        return if (candidate in range) candidate else draggedIndex
    }
}
