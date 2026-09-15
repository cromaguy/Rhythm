/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object ServiceStartUtils {

    /**
     * Starts a service command defensively across foreground/background contexts.
     */
    fun startServiceSafely(
        context: Context,
        intent: Intent,
        logTag: String,
        reason: String
    ): Boolean {
        return try {
            context.startService(intent)
            true
        } catch (startException: Exception) {
            Log.w(
                logTag,
                "startService failed for $reason: ${startException.javaClass.simpleName}: ${startException.message}"
            )
            false
        }
    }
}
