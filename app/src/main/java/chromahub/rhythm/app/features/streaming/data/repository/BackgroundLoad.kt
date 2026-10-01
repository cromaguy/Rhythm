/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starts [load] in [scope] immediately and lets callers suspend until it has finished.
 *
 * Used for the catalog cache, which can be tens of MB for a large library: parsing it where the
 * repository is created (often the main thread) caused input-dispatch ANRs.
 */
internal class BackgroundLoad(scope: CoroutineScope, load: suspend () -> Unit) {

    private val job = scope.launch { load() }

    /** Suspends (without blocking) until the load has finished, successfully or not. */
    suspend fun await() {
        job.join()
    }
}
