/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Like `remember(keys) { compute() }`, but runs [compute] on [Dispatchers.Default]. Sorting,
 * grouping or mapping a library of tens of thousands of songs inside composition blocks the
 * main thread for seconds (input-dispatch ANRs). Until the first result is ready the state holds
 * [initial]; after a key change it keeps the previous result until the new one is ready, and a
 * computation for outdated keys is cancelled.
 */
@Composable
fun <T> rememberOffMain(initial: T, vararg keys: Any?, compute: () -> T): State<T> =
    produceState(initial, *keys) {
        value = withContext(Dispatchers.Default) { compute() }
    }

/**
 * Sorts by a case-insensitive text key, computing each key once (a `sortedBy { it.x.lowercase() }`
 * comparator lowercases two strings per comparison, ~n log n allocations). Same order as
 * `sortedBy { key(it).lowercase() }`, including stability.
 */
fun <T> List<T>.sortedByLowercase(descending: Boolean = false, key: (T) -> String): List<T> {
    val keyed = map { key(it).lowercase() to it }
    val sorted = if (descending) keyed.sortedByDescending { it.first } else keyed.sortedBy { it.first }
    return sorted.map { it.second }
}
