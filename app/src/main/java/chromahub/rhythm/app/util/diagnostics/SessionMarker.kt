/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Tracks lightweight session launch metadata and runtime breadcrumbs so post-mortem
 * process exit diagnostics can correlate unexpected crashes/ANRs with the active flow.
 */
object SessionMarker {
    private const val PREFS_NAME = "rhythm_session_marker"
    private const val KEY_ACTIVE_LAUNCH_MS = "active_launch_ms"
    private const val KEY_ACTIVE_VERSION = "active_version"
    private const val KEY_ACTIVE_BREADCRUMB = "active_breadcrumb"
    private const val KEY_PREV_LAUNCH_MS = "prev_launch_ms"
    private const val KEY_PREV_VERSION = "prev_version"
    private const val KEY_PREV_BREADCRUMB = "prev_breadcrumb"

    data class PreviousSession(
        val launchedAtMs: Long,
        val versionName: String,
        val lastBreadcrumb: String
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun markLaunch(context: Context, versionName: String, nowMs: Long = System.currentTimeMillis()): PreviousSession? {
        val store = prefs(context)
        val prevLaunchMs = store.getLong(KEY_ACTIVE_LAUNCH_MS, 0L)
        val prevVersion = store.getString(KEY_ACTIVE_VERSION, "").orEmpty()
        val prevBreadcrumb = store.getString(KEY_ACTIVE_BREADCRUMB, "").orEmpty()

        val previous = if (prevLaunchMs > 0L) {
            PreviousSession(prevLaunchMs, prevVersion, prevBreadcrumb)
        } else null

        store.edit(commit = true) {
            putLong(KEY_PREV_LAUNCH_MS, prevLaunchMs)
            putString(KEY_PREV_VERSION, prevVersion)
            putString(KEY_PREV_BREADCRUMB, prevBreadcrumb)
            putLong(KEY_ACTIVE_LAUNCH_MS, nowMs)
            putString(KEY_ACTIVE_VERSION, versionName)
            putString(KEY_ACTIVE_BREADCRUMB, "launch")
        }
        return previous
    }

    fun updateBreadcrumb(context: Context, breadcrumb: String) {
        prefs(context).edit {
            putString(KEY_ACTIVE_BREADCRUMB, breadcrumb.take(120))
        }
    }

    fun readPreviousSession(context: Context): PreviousSession? {
        val store = prefs(context)
        val launchedAtMs = store.getLong(KEY_PREV_LAUNCH_MS, 0L)
        if (launchedAtMs <= 0L) return null
        return PreviousSession(
            launchedAtMs = launchedAtMs,
            versionName = store.getString(KEY_PREV_VERSION, "").orEmpty(),
            lastBreadcrumb = store.getString(KEY_PREV_BREADCRUMB, "").orEmpty()
        )
    }
}
