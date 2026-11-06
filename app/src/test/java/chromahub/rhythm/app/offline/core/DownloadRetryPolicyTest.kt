// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import chromahub.rhythm.app.offline.core.DownloadRetryPolicy.AfterStop.ATTEMPT_FAILED
import chromahub.rhythm.app.offline.core.DownloadRetryPolicy.AfterStop.NETWORK_LOST
import chromahub.rhythm.app.offline.core.DownloadRetryPolicy.AfterStop.PAUSED
import chromahub.rhythm.app.offline.core.DownloadRetryPolicy.AfterStop.REMOVED
import chromahub.rhythm.app.offline.core.DownloadRetryPolicy.AfterStop.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Review 2026-10-07: a download that did not finish used to count as a failed attempt whenever the
 * network looked fine, including when WorkManager had stopped the worker (Wi-Fi lost with cellular
 * not allowed, an app update) or the user had switched Offline mode on. Three of those and the job
 * was FAILED although nothing was wrong with it.
 */
class DownloadRetryPolicyTest {

    private fun after(tracked: Boolean = true, workerActive: Boolean = true, offlineMode: Boolean = false, network: Boolean = true) =
        DownloadRetryPolicy.afterUnfinished(tracked, workerActive, offlineMode, network)

    @Test
    fun aJobTheUserRemovedIsJustDropped() = assertEquals(REMOVED, after(tracked = false, workerActive = false))

    @Test
    fun aStoppedWorkerRequeuesWithoutCountingAnAttempt() = assertEquals(STOPPED, after(workerActive = false))

    @Test
    fun offlineModePausesTheJob() = assertEquals(PAUSED, after(offlineMode = true))

    @Test
    fun noNetworkRequeuesAndRetriesWhenItIsBack() = assertEquals(NETWORK_LOST, after(network = false))

    @Test
    fun onlyARealFailureCountsAsAnAttempt() = assertEquals(ATTEMPT_FAILED, after())

    @Test
    fun stoppedBeatsPausedBeatsNetworkLoss() {
        assertEquals(STOPPED, after(workerActive = false, offlineMode = true, network = false))
        assertEquals(PAUSED, after(offlineMode = true, network = false))
    }
}
