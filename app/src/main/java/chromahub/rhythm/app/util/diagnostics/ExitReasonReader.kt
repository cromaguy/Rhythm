/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import chromahub.rhythm.app.shared.data.model.AppSettings

/**
 * Inspects Android 11+ (`Build.VERSION_CODES.R`) historical process exit reasons so
 * native crashes (`REASON_CRASH_NATIVE` proto tombstones), system ANRs, and OOM/resource
 * terminations missed by JVM `UncaughtExceptionHandler` are captured in Rhythm's
 * crash history cleanly without duplication.
 */
object ExitReasonReader {
    private const val TAG = "ExitReasonReader"
    private const val PREFS_NAME = "rhythm_exit_reason_diag"
    private const val KEY_LAST_EXIT_TS = "last_recorded_exit_ts"
    private const val MAX_TRACE_CHARS = 8_000
    private const val MAX_PROTO_BYTES = 128 * 1024

    fun recordPendingExitReasons(
        context: Context,
        appSettings: AppSettings,
        previousSession: SessionMarker.PreviousSession? = SessionMarker.readPreviousSession(context)
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0

        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val exits = runCatching {
            am.getHistoricalProcessExitReasons(appContext.packageName, 0, 8)
        }.getOrElse {
            Log.w(TAG, "Unable to query historical process exit reasons", it)
            return 0
        }

        if (exits.isEmpty()) return 0

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRecordedTs = prefs.getLong(KEY_LAST_EXIT_TS, 0L)
        var newestRecordedTs = lastRecordedTs
        var recordedCount = 0

        // Process chronologically (oldest unseen first) so AppSettings history retains newest at index 0.
        val unseen = exits
            .filter { it.timestamp > lastRecordedTs && isNotableReason(it.reason) }
            .sortedBy { it.timestamp }

        for (exit in unseen) {
            val formatted = formatExitReport(exit, previousSession)
            appSettings.addCrashLogEntryWithTimestamp(exit.timestamp, formatted)
            if (exit.timestamp > newestRecordedTs) {
                newestRecordedTs = exit.timestamp
            }
            recordedCount++
        }

        val maxObservedTs = exits.maxOfOrNull { it.timestamp } ?: lastRecordedTs
        val watermark = maxOf(newestRecordedTs, maxObservedTs)
        if (watermark > lastRecordedTs) {
            prefs.edit { putLong(KEY_LAST_EXIT_TS, watermark) }
        }
        return recordedCount
    }

    internal fun isNotableReason(reason: Int): Boolean {
        return when (reason) {
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_LOW_MEMORY -> true
            else -> false
        }
    }

    internal fun reasonLabel(reason: Int): String {
        return when (reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH -> "Unhandled Crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native Crash"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Excessive Resource Usage"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Low Memory Kill"
            ApplicationExitInfo.REASON_FREEZER -> "App Freezer"
            else -> "Process Exit ($reason)"
        }
    }

    private fun formatExitReport(
        exit: ApplicationExitInfo,
        previousSession: SessionMarker.PreviousSession?
    ): String {
        val trace = extractTraceSummary(exit)
        return buildString {
            appendLine("=== System Process Exit Report ===")
            appendLine("Reason: ${reasonLabel(exit.reason)}")
            appendLine("TimestampMs: ${exit.timestamp}")
            exit.description?.takeIf { it.isNotBlank() }?.let {
                appendLine("Description: $it")
            }
            if (exit.pss > 0L || exit.rss > 0L) {
                appendLine("Memory: PSS=${exit.pss}KB, RSS=${exit.rss}KB")
            }
            previousSession?.let { prev ->
                appendLine("Previous session: v${prev.versionName} (breadcrumb=${prev.lastBreadcrumb})")
            }
            if (!trace.isNullOrBlank()) {
                appendLine()
                appendLine("--- Trace ---")
                append(trace)
            }
        }.trim()
    }

    private fun extractTraceSummary(exit: ApplicationExitInfo): String? {
        return runCatching {
            val stream = exit.traceInputStream ?: return null
            stream.use { input ->
                if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    val bytes = input.readNBytesCompat(MAX_PROTO_BYTES)
                    TombstoneSummary.format(bytes)
                } else {
                    val text = input.bufferedReader().use { it.readText() }.trim()
                    if (text.length <= MAX_TRACE_CHARS) text
                    else text.take(MAX_TRACE_CHARS) + "\n… trace truncated"
                }
            }
        }.getOrNull()
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < limit) {
            val toRead = minOf(buffer.size, limit - total)
            val n = read(buffer, 0, toRead)
            if (n <= 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
