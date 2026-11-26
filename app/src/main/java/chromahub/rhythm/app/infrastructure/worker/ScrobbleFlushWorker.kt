/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import chromahub.rhythm.app.infrastructure.network.scrobble.MultiScrobblerManager

/**
 * WorkManager worker draining queued offline ListenBrainz, Libre.fm, and Maloja
 * scrobbles stored in Room v10 `pending_scrobbles`.
 */
class ScrobbleFlushWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val allDelivered = MultiScrobblerManager.getInstance(applicationContext).flushPending()
        return if (allDelivered) Result.success() else Result.retry()
    }
}
