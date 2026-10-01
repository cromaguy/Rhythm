/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackgroundLoadTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun loadRunsOffTheCallingThreadAndDoesNotBlockIt() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        var loadThread: Thread? = null
        var finished = false

        // A slow load (like parsing a large cache) that only ends when released.
        val load = BackgroundLoad(scope) {
            loadThread = Thread.currentThread()
            started.countDown()
            release.await()
            finished = true
        }

        // The constructor returned while the load is still running.
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertFalse(finished)
        assertNotSame(Thread.currentThread(), loadThread)

        release.countDown()
        runBlocking { load.await() }
        assertTrue(finished)
    }

    @Test
    fun awaitReturnsAfterAFailedLoad() {
        val load = BackgroundLoad(scope) { throw IllegalStateException("corrupt cache") }

        // Must return (the caller then sees an empty catalog) rather than hang or throw.
        runBlocking { load.await() }
    }
}
