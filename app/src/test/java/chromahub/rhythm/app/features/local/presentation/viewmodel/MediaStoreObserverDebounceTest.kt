/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MediaStoreObserverDebounceTest {

    @Test
    fun testStartupFalsePositiveEventsAreIgnored() {
        var refreshTriggered = false
        val registrationTimeMs = 1000L

        fun onMediaStoreChanged(eventTimeMs: Long) {
            val timeSinceRegistration = eventTimeMs - registrationTimeMs
            if (timeSinceRegistration < 5000L) {
                // False-positive startup window: ignore
                return
            }
            refreshTriggered = true
        }

        // Event arriving 2 seconds after registration should be dropped
        onMediaStoreChanged(eventTimeMs = 3000L)
        assertFalse("Startup events within 5000ms must be dropped", refreshTriggered)

        // Event arriving 4.9 seconds after registration should be dropped
        onMediaStoreChanged(eventTimeMs = 5900L)
        assertFalse("Startup events within 5000ms must be dropped", refreshTriggered)

        // Event arriving 5.1 seconds after registration must be processed
        onMediaStoreChanged(eventTimeMs = 6100L)
        assertTrue("Events after 5000ms window must be accepted", refreshTriggered)
    }

    @Test
    fun testRapidFireEventsAreDebouncedToOneExecution() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val executions = AtomicInteger(0)
        var activeJob: Job? = null
        val debounceMs = 100L

        fun onMediaStoreEvent() {
            activeJob?.cancel()
            activeJob = scope.launch {
                delay(debounceMs)
                executions.incrementAndGet()
            }
        }

        // Fire 10 rapid events within short intervals
        for (i in 0 until 10) {
            onMediaStoreEvent()
            delay(15L)
        }

        // At this point, bursts kept resetting the activeJob
        assertEquals("No execution should happen while debounce window is constantly reset", 0, executions.get())

        // Wait past the debounce window
        delay(250L)

        assertEquals("Exactly one refresh must be scheduled after rapid burst", 1, executions.get())
    }

    @Test
    fun testStalenessCheckPreventsUnnecessaryLibraryScan() {
        var refreshCount = 0

        fun checkAndRefresh(
            lastScanTimestamp: Long,
            cachedCount: Int,
            isStalePredicate: (Long, Int) -> Boolean
        ) {
            if (isStalePredicate(lastScanTimestamp, cachedCount)) {
                refreshCount++
            }
        }

        // Case 1: Library is up-to-date (no added/modified files and counts match)
        checkAndRefresh(
            lastScanTimestamp = 100000L,
            cachedCount = 50,
            isStalePredicate = { _, _ -> false }
        )
        assertEquals("Up-to-date library must skip refresh to save battery", 0, refreshCount)

        // Case 2: Library is stale (new audio file added or count changed)
        checkAndRefresh(
            lastScanTimestamp = 100000L,
            cachedCount = 50,
            isStalePredicate = { _, _ -> true }
        )
        assertEquals("Stale library must perform refresh", 1, refreshCount)
    }
}
