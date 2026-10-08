/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueReorderTest {

    private data class DummySong(val id: String, val title: String)

    @Test
    fun testStableEntryIdsAcrossReorder() {
        val tracker = QueueEntryKeyTracker<DummySong> { it.id }
        val song1 = DummySong("s1", "Song 1")
        val song2 = DummySong("s2", "Song 2")
        val song3 = DummySong("s3", "Song 3")

        val initialEntries = tracker.sync(listOf(song1, song2, song3))
        assertEquals(3, initialEntries.size)

        val id1 = initialEntries[0].entryId
        val id2 = initialEntries[1].entryId
        val id3 = initialEntries[2].entryId

        // All IDs must be distinct
        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)

        // Move song 1 to index 2: [song2, song3, song1]
        val mutable = initialEntries.toMutableList()
        val moved = mutable.removeAt(0)
        mutable.add(2, moved)
        tracker.updateEntries(mutable)

        val current = tracker.currentEntries()
        assertEquals(3, current.size)
        assertEquals("s2", current[0].item.id)
        assertEquals(id2, current[0].entryId)

        assertEquals("s3", current[1].item.id)
        assertEquals(id3, current[1].entryId)

        assertEquals("s1", current[2].item.id)
        assertEquals(id1, current[2].entryId)
    }

    @Test
    fun testDuplicateSongIdsHandledIndependently() {
        val tracker = QueueEntryKeyTracker<DummySong> { it.id }
        // Duplicate song ID "dup" at indices 0 and 2
        val songA1 = DummySong("dup", "Song Duplicate First")
        val songB = DummySong("unique", "Song Middle")
        val songA2 = DummySong("dup", "Song Duplicate Second")

        val initialEntries = tracker.sync(listOf(songA1, songB, songA2))
        assertEquals(3, initialEntries.size)

        val idFirstDup = initialEntries[0].entryId
        val idMiddle = initialEntries[1].entryId
        val idSecondDup = initialEntries[2].entryId

        // Even with the same song ID, entry IDs must be completely unique
        assertNotEquals(idFirstDup, idSecondDup)
        assertNotEquals(idFirstDup, idMiddle)
        assertNotEquals(idSecondDup, idMiddle)

        // Move the second duplicate to index 0: [songA2, songA1, songB]
        val mutable = initialEntries.toMutableList()
        val moved = mutable.removeAt(2)
        mutable.add(0, moved)
        tracker.updateEntries(mutable)

        val afterReorder = tracker.currentEntries()
        // Item at index 0 must retain idSecondDup
        assertEquals(idSecondDup, afterReorder[0].entryId)
        assertEquals("Song Duplicate Second", afterReorder[0].item.title)

        // Item at index 1 must retain idFirstDup
        assertEquals(idFirstDup, afterReorder[1].entryId)
        assertEquals("Song Duplicate First", afterReorder[1].item.title)

        // Item at index 2 must retain idMiddle
        assertEquals(idMiddle, afterReorder[2].entryId)
    }

    @Test
    fun testPreserveIdsAcrossExternalQueueUpdates() {
        val tracker = QueueEntryKeyTracker<DummySong> { it.id }
        val song1 = DummySong("1", "Song 1")
        val song2 = DummySong("2", "Song 2")
        val song3 = DummySong("3", "Song 3")

        val initial = tracker.sync(listOf(song1, song2, song3))
        val id1 = initial[0].entryId
        val id2 = initial[1].entryId
        val id3 = initial[2].entryId

        // External update: song2 removed, song4 added
        val song4 = DummySong("4", "Song 4")
        val updated = tracker.sync(listOf(song1, song3, song4))

        assertEquals(3, updated.size)
        // Song 1 and Song 3 retain their original IDs
        assertEquals(id1, updated[0].entryId)
        assertEquals("1", updated[0].item.id)

        assertEquals(id3, updated[1].entryId)
        assertEquals("3", updated[1].item.id)

        // Song 4 gets a fresh ID
        assertEquals("4", updated[2].item.id)
        assertNotEquals(id1, updated[2].entryId)
        assertNotEquals(id2, updated[2].entryId)
        assertNotEquals(id3, updated[2].entryId)
    }

    @Test
    fun testDragSnapshotReversionOnCancel() {
        val tracker = QueueEntryKeyTracker<DummySong> { it.id }
        val songs = listOf(
            DummySong("1", "Song 1"),
            DummySong("2", "Song 2"),
            DummySong("3", "Song 3")
        )
        val initial = tracker.sync(songs)
        val snapshot = initial.toList()

        // User starts dragging and intermediate hops happen
        val dragged = initial.toMutableList()
        val item = dragged.removeAt(0)
        dragged.add(2, item)
        tracker.updateEntries(dragged)

        // Drag is cancelled -> restore snapshot
        tracker.updateEntries(snapshot)

        val restored = tracker.currentEntries()
        assertEquals(initial.map { it.item.id }, restored.map { it.item.id })
        assertEquals(initial.map { it.entryId }, restored.map { it.entryId })
    }

    @Test
    fun testQueueIndexMappingAndUpcomingBounds() {
        // Queue: [Played0, Played1, Current, Upcoming0, Upcoming1, Upcoming2]
        // Size = 6. Current = 2. Upcoming songs are at queue indices 3, 4, 5.
        val currentQueueIndex = 2
        val totalQueueSize = 6
        val upcomingCount = totalQueueSize - (currentQueueIndex + 1)
        assertEquals(3, upcomingCount)

        // Upcoming index 0 is queue index 3
        fun upcomingToQueueIndex(upcomingIdx: Int): Int = (currentQueueIndex + 1) + upcomingIdx

        assertEquals(3, upcomingToQueueIndex(0))
        assertEquals(4, upcomingToQueueIndex(1))
        assertEquals(5, upcomingToQueueIndex(2))

        // Moving upcoming 0 to upcoming 2:
        val fromQueue = upcomingToQueueIndex(0)
        val toQueue = upcomingToQueueIndex(2)

        assertEquals(3, fromQueue)
        assertEquals(5, toQueue)
        assertNotEquals(fromQueue, toQueue)

        // Reordering at same position:
        val sameFrom = upcomingToQueueIndex(1)
        val sameTo = upcomingToQueueIndex(1)
        assertEquals(sameFrom, sameTo) // No-op guard works
    }

    @Test
    fun testManualTargetIndexClampingToTop() {
        val range = 2..8
        // Visible items: items 2, 3, 4 with heights of 60px
        val visibleItems = listOf(
            ReorderableItemBounds(index = 2, offset = 100f, size = 60f), // mid = 130
            ReorderableItemBounds(index = 3, offset = 164f, size = 60f), // mid = 194
            ReorderableItemBounds(index = 4, offset = 228f, size = 60f)  // mid = 258
        )

        // Dragging above item 2 (over header or padding, e.g. Y = 50) must pin to range.first (2)
        val targetAbove = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 50f,
            draggedIndex = 4,
            range = range,
            visibleReorderable = visibleItems
        )
        assertEquals(2, targetAbove)

        // Dragging right at midpoint of item 2 (Y = 130) must pin to range.first (2)
        val targetAtMid = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 130f,
            draggedIndex = 4,
            range = range,
            visibleReorderable = visibleItems
        )
        assertEquals(2, targetAtMid)

        // Dragging between item 2 and 3, closer to item 3 (e.g. Y = 170) -> target is 3
        val targetBetween = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 170f,
            draggedIndex = 4,
            range = range,
            visibleReorderable = visibleItems
        )
        assertEquals(3, targetBetween)
    }

    @Test
    fun testManualTargetIndexClampingWhenFirstVisibleIsNotRangeFirst() {
        val range = 0..10
        // List is scrolled down: items 5, 6, 7 are visible (item 0 is offscreen)
        val visibleItems = listOf(
            ReorderableItemBounds(index = 5, offset = 100f, size = 60f), // mid = 130
            ReorderableItemBounds(index = 6, offset = 164f, size = 60f), // mid = 194
            ReorderableItemBounds(index = 7, offset = 228f, size = 60f)  // mid = 258
        )

        // Dragging above item 5 must target index 5 (top of visible), awaiting autoscroll to reach index 0
        val targetAbove = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 50f,
            draggedIndex = 7,
            range = range,
            visibleReorderable = visibleItems
        )
        assertEquals(5, targetAbove)
    }

    @Test
    fun testManualTargetIndexClampingToBottom() {
        val range = 0..5
        val visibleItems = listOf(
            ReorderableItemBounds(index = 3, offset = 100f, size = 60f), // mid = 130
            ReorderableItemBounds(index = 4, offset = 164f, size = 60f), // mid = 194
            ReorderableItemBounds(index = 5, offset = 228f, size = 60f)  // mid = 258
        )

        // Dragging below item 5 (e.g. Y = 350) must clamp to range.last (5)
        val targetBelow = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 350f,
            draggedIndex = 3,
            range = range,
            visibleReorderable = visibleItems
        )
        assertEquals(5, targetBelow)
    }

    @Test
    fun testCanScrollUpConditions() {
        val topLimit = 60f
        val rangeFirst = 2

        // Dragged item is at range.first -> cannot scroll up
        assertFalse(
            DragDropUtils.canScrollUp(
                draggedIndex = 2,
                rangeFirst = rangeFirst,
                canScrollBackward = true,
                firstItemOffset = -10f,
                topLimit = topLimit
            )
        )

        // Cannot scroll backward in lazy list -> false
        assertFalse(
            DragDropUtils.canScrollUp(
                draggedIndex = 5,
                rangeFirst = rangeFirst,
                canScrollBackward = false,
                firstItemOffset = null,
                topLimit = topLimit
            )
        )

        // First item offscreen (null) and can scroll backward -> true
        assertTrue(
            DragDropUtils.canScrollUp(
                draggedIndex = 5,
                rangeFirst = rangeFirst,
                canScrollBackward = true,
                firstItemOffset = null,
                topLimit = topLimit
            )
        )

        // First item partially scrolled off (offset < topLimit) -> true
        assertTrue(
            DragDropUtils.canScrollUp(
                draggedIndex = 5,
                rangeFirst = rangeFirst,
                canScrollBackward = true,
                firstItemOffset = 20f,
                topLimit = topLimit
            )
        )

        // First item fully visible below topLimit -> false (no need to autoscroll)
        assertFalse(
            DragDropUtils.canScrollUp(
                draggedIndex = 5,
                rangeFirst = rangeFirst,
                canScrollBackward = true,
                firstItemOffset = 80f,
                topLimit = topLimit
            )
        )
    }

    @Test
    fun testCanScrollDownConditions() {
        val bottomLimit = 700f
        val rangeLast = 10

        // Dragged item is already at range.last -> false
        assertFalse(
            DragDropUtils.canScrollDown(
                draggedIndex = 10,
                rangeLast = rangeLast,
                canScrollForward = true,
                lastItemBottom = 800f,
                bottomLimit = bottomLimit
            )
        )

        // Cannot scroll forward -> false
        assertFalse(
            DragDropUtils.canScrollDown(
                draggedIndex = 5,
                rangeLast = rangeLast,
                canScrollForward = false,
                lastItemBottom = null,
                bottomLimit = bottomLimit
            )
        )

        // Last item offscreen -> true
        assertTrue(
            DragDropUtils.canScrollDown(
                draggedIndex = 5,
                rangeLast = rangeLast,
                canScrollForward = true,
                lastItemBottom = null,
                bottomLimit = bottomLimit
            )
        )

        // Last item extends past bottom limit -> true
        assertTrue(
            DragDropUtils.canScrollDown(
                draggedIndex = 5,
                rangeLast = rangeLast,
                canScrollForward = true,
                lastItemBottom = 750f,
                bottomLimit = bottomLimit
            )
        )

        // Last item fully visible above bottom limit -> false
        assertFalse(
            DragDropUtils.canScrollDown(
                draggedIndex = 5,
                rangeLast = rangeLast,
                canScrollForward = true,
                lastItemBottom = 650f,
                bottomLimit = bottomLimit
            )
        )
    }

    @Test
    fun testComputeAutoscrollHop() {
        val range = 2..10
        val rowHeight = 60f

        // Distance not yet reached threshold (0.75 * 60 = 45px)
        val noHop = DragDropUtils.computeAutoscrollHop(
            scrollSpeed = -5f,
            draggedIndex = 5,
            range = range,
            autoscrollDistance = 30f,
            rowHeight = rowHeight
        )
        assertEquals(5, noHop)

        // Distance reached threshold when scrolling UP (-5f) -> hops to 4
        val hopUp = DragDropUtils.computeAutoscrollHop(
            scrollSpeed = -5f,
            draggedIndex = 5,
            range = range,
            autoscrollDistance = 46f,
            rowHeight = rowHeight
        )
        assertEquals(4, hopUp)

        // Distance reached threshold when scrolling DOWN (+5f) -> hops to 6
        val hopDown = DragDropUtils.computeAutoscrollHop(
            scrollSpeed = 5f,
            draggedIndex = 5,
            range = range,
            autoscrollDistance = 46f,
            rowHeight = rowHeight
        )
        assertEquals(6, hopDown)

        // At top boundary (draggedIndex = 2), scrolling UP cannot go below range.first
        val boundedTop = DragDropUtils.computeAutoscrollHop(
            scrollSpeed = -5f,
            draggedIndex = 2,
            range = range,
            autoscrollDistance = 50f,
            rowHeight = rowHeight
        )
        assertEquals(2, boundedTop)

        // At bottom boundary (draggedIndex = 10), scrolling DOWN cannot exceed range.last
        val boundedBottom = DragDropUtils.computeAutoscrollHop(
            scrollSpeed = 5f,
            draggedIndex = 10,
            range = range,
            autoscrollDistance = 50f,
            rowHeight = rowHeight
        )
        assertEquals(10, boundedBottom)
    }

    @Test
    fun testEntryIdResolutionOnDrop() {
        val tracker = QueueEntryKeyTracker<DummySong> { it.id }
        val s1 = DummySong("1", "Song 1")
        val s2 = DummySong("2", "Song 2")
        val s3 = DummySong("3", "Song 3")
        val s4 = DummySong("4", "Song 4")
        val entries = tracker.sync(listOf(s1, s2, s3, s4)).toMutableList()

        // Suppose user starts dragging s4 (entryId = entries[3].entryId, initial queue index = 3)
        val draggedEntryId = entries[3].entryId

        // Moves happen during drag: s4 moved to index 0
        val moved = entries.removeAt(3)
        entries.add(0, moved)
        tracker.updateEntries(entries)

        // On drop: resolve finalIdx via entryId
        val finalIdx = entries.indexOfFirst { it.entryId == draggedEntryId }
        assertEquals(0, finalIdx)
        assertEquals("4", entries[finalIdx].item.id)
    }

    @Test
    fun testCanScrollUpWhenRangeFirstIsZero() {
        // When rangeFirst is 0 and canScrollBackward is true, autoscroll up must be allowed
        // even if firstItemOffset >= topLimit (e.g. contentPadding.top)
        assertTrue(
            DragDropUtils.canScrollUp(
                draggedIndex = 1,
                rangeFirst = 0,
                canScrollBackward = true,
                firstItemOffset = 11f,
                topLimit = 0f
            )
        )

        // When already at rangeFirst = 0, cannot scroll up
        assertFalse(
            DragDropUtils.canScrollUp(
                draggedIndex = 0,
                rangeFirst = 0,
                canScrollBackward = true,
                firstItemOffset = 11f,
                topLimit = 0f
            )
        )

        // When canScrollBackward is false, cannot scroll up
        assertFalse(
            DragDropUtils.canScrollUp(
                draggedIndex = 1,
                rangeFirst = 0,
                canScrollBackward = false,
                firstItemOffset = 11f,
                topLimit = 0f
            )
        )
    }

    @Test
    fun testManualTargetIndexWhenDraggedIsRangeFirstNeverPushedDown() {
        val range = 0..10
        // Viewport starts at index 1 because Compose shifted scroll position
        val visibleItems = listOf(
            ReorderableItemBounds(index = 1, offset = 10f, size = 60f), // mid = 40
            ReorderableItemBounds(index = 2, offset = 74f, size = 60f)  // mid = 104
        )

        // When draggedIndex is 0 and cardCenterY is at top (<= firstVis.midpoint = 40),
        // it must stay at 0, NOT be forced down to 1!
        val target = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 20f,
            draggedIndex = 0,
            range = range,
            visibleReorderable = visibleItems
        )
        assertEquals(0, target)
    }

    @Test
    fun testHysteresisPreventsBoundaryJitter() {
        val range = 0..5
        val visibleItems = listOf(
            ReorderableItemBounds(index = 0, offset = 0f, size = 100f),   // mid = 50
            ReorderableItemBounds(index = 1, offset = 100f, size = 100f)  // mid = 150
        )
        // Midpoint between item 0 and item 1 is 100f.
        val bonus = 20f

        // 1. While draggedIndex = 1, moving up to Y = 95f (slight waver across 100 boundary)
        // Without bonus, 95 would switch to 0. But with bonus = 20f:
        // dist to 1: abs(95 - 150) - 20 = 55 - 20 = 35.
        // dist to 0: abs(95 - 50) = 45.
        // 35 < 45, so it remains at 1!
        val targetStayingAt1 = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 95f,
            draggedIndex = 1,
            range = range,
            visibleReorderable = visibleItems,
            hysteresisBonus = bonus
        )
        assertEquals(1, targetStayingAt1)

        // Moving further up to Y = 70f:
        // dist to 1: abs(70 - 150) - 20 = 80 - 20 = 60.
        // dist to 0: abs(70 - 50) = 20.
        // 20 < 60, so it transitions to 0.
        val targetMovingTo0 = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 70f,
            draggedIndex = 1,
            range = range,
            visibleReorderable = visibleItems,
            hysteresisBonus = bonus
        )
        assertEquals(0, targetMovingTo0)

        // 2. Now that draggedIndex = 0, wavering back down to Y = 105f:
        // Without bonus, 105 would flip back to 1. But with bonus:
        // dist to 0: abs(105 - 50) - 20 = 55 - 20 = 35.
        // dist to 1: abs(105 - 150) = 45.
        // 35 < 45, so it stays at 0!
        val targetStayingAt0 = DragDropUtils.computeManualTargetIndex(
            cardCenterY = 105f,
            draggedIndex = 0,
            range = range,
            visibleReorderable = visibleItems,
            hysteresisBonus = bonus
        )
        assertEquals(0, targetStayingAt0)
    }
}
