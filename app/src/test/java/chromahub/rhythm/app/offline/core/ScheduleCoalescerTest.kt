// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** A burst of "run the worker" requests (the Offline toggle queued it seven times in a second) is one. */
class ScheduleCoalescerTest {

    private class Harness {
        val timers = ArrayList<Pair<Long, () -> Unit>>()
        val runs = ArrayList<Boolean>()
        val coalescer = ScheduleCoalescer(500, { delay, fire -> timers.add(delay to fire) }) { replace -> runs.add(replace) }
        fun elapse() {
            val pending = timers.toList()
            timers.clear()
            pending.forEach { it.second() }
        }
    }

    @Test
    fun sevenRequestsInOneWindowAreOneRun() {
        val h = Harness()
        repeat(7) { h.coalescer.request() }
        assertEquals("one timer for the whole burst", 1, h.timers.size)
        assertEquals(500L, h.timers.single().first)
        assertEquals("nothing runs before the window ends", 0, h.runs.size)
        h.elapse()
        assertEquals(listOf(false), h.runs)
        assertEquals(7, h.coalescer.requests)
    }

    @Test
    fun aReplaceAnywhereInTheBurstWins() {
        val h = Harness()
        h.coalescer.request()
        h.coalescer.request(replace = true)
        h.coalescer.request()
        h.elapse()
        assertEquals(listOf(true), h.runs)
    }

    @Test
    fun theNextBurstAfterTheWindowIsItsOwnRunAndForgetsTheReplace() {
        val h = Harness()
        h.coalescer.request(replace = true)
        h.elapse()
        h.coalescer.request()
        assertEquals(1, h.timers.size)
        h.elapse()
        assertEquals(listOf(true, false), h.runs)
    }

    @Test
    fun aRequestMadeWhileTheActionRunsStartsANewWindow() {
        val timers = ArrayList<() -> Unit>()
        lateinit var coalescer: ScheduleCoalescer
        val runs = ArrayList<Boolean>()
        coalescer = ScheduleCoalescer(100, { _, fire -> timers.add(fire) }) { replace ->
            runs.add(replace)
            if (runs.size == 1) coalescer.request() // e.g. the worker's own continuation
        }
        coalescer.request()
        timers.removeAt(0)()
        assertEquals(1, timers.size)
        timers.removeAt(0)()
        assertEquals(2, runs.size)
    }

    // ---- what a run does to WorkManager's chain ----

    private fun decide(vararg links: WorkLink, replace: Boolean = false) = ScheduleDecision.decide(links.toList(), replace)

    @Test
    fun noLiveLinkMeansEnqueue() {
        assertEquals(ScheduleDecision.Action.ENQUEUE, decide())
        assertEquals(ScheduleDecision.Action.ENQUEUE, decide(WorkLink(LinkState.FINISHED), WorkLink(LinkState.FINISHED)))
    }

    @Test
    fun aRunningWorkerGetsExactlyOneLinkBehindIt() {
        assertEquals(ScheduleDecision.Action.APPEND, decide(WorkLink(LinkState.RUNNING)))
        // ... and once one waits behind it, further requests add nothing: the chain never grows past two.
        assertEquals(ScheduleDecision.Action.KEEP, decide(WorkLink(LinkState.RUNNING), WorkLink(LinkState.BLOCKED)))
    }

    @Test
    fun aLinkWaitingForItsConstraintsIsKept() {
        assertEquals(ScheduleDecision.Action.KEEP, decide(WorkLink(LinkState.WAITING)))
    }

    @Test
    fun aLinkWaitingOutABackoffIsReplacedSoTheUserIsNotMadeToWait() {
        assertEquals(ScheduleDecision.Action.REPLACE, decide(WorkLink(LinkState.WAITING, runAttemptCount = 3)))
    }

    @Test
    fun aRequestForReplacementReplaces() {
        assertEquals(ScheduleDecision.Action.REPLACE, decide(replace = true))
        assertEquals(ScheduleDecision.Action.REPLACE, decide(WorkLink(LinkState.RUNNING), replace = true))
    }

    @Test
    fun theFieldBurstOfEightRequestsMakesAtMostTwoLinks() {
        // 8 requests -> 1 coalesced run -> with a worker running that is 1 appended link; the next burst finds it and keeps.
        var chain = listOf(WorkLink(LinkState.RUNNING))
        repeat(2) {
            when (ScheduleDecision.decide(chain, false)) {
                ScheduleDecision.Action.APPEND -> chain = chain + WorkLink(LinkState.BLOCKED)
                else -> Unit
            }
        }
        assertEquals(2, chain.size)
    }
}
