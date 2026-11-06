// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/** What to do with a download job whose transfer ended without a finished file. */
object DownloadRetryPolicy {

    enum class AfterStop {
        /** The job is gone from the ledger (cancelled or removed): nothing to record. */
        REMOVED,

        /** The worker was stopped (constraints lost, app update): requeue, no attempt used. */
        STOPPED,

        /** Offline mode is on: requeue, no attempt used, and leave the server alone. */
        PAUSED,

        /** The device has no network: requeue and retry when it is back. */
        NETWORK_LOST,

        /** The server or the transfer failed with the network up: one attempt used. */
        ATTEMPT_FAILED,
    }

    fun afterUnfinished(stillTracked: Boolean, workerActive: Boolean, offlineMode: Boolean, networkAvailable: Boolean): AfterStop =
        when {
            !stillTracked -> AfterStop.REMOVED
            !workerActive -> AfterStop.STOPPED
            offlineMode -> AfterStop.PAUSED
            !networkAvailable -> AfterStop.NETWORK_LOST
            else -> AfterStop.ATTEMPT_FAILED
        }
}
