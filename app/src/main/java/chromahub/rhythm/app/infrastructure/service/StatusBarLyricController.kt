/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import chromahub.rhythm.app.R

/**
 * Opt-in OEM status-bar ticker lyric bridge compatible with Flyme, HyperOS/MIUI, and Super
 * Status Bar hooks via [FLAG_ALWAYS_SHOW_TICKER] / [FLAG_ONLY_UPDATE_TICKER] notification flags.
 *
 * Uses rate limiting and string deduplication so Android NotificationManager rate shedding is never triggered.
 */
class StatusBarLyricController(
    private val context: Context,
    private val channelId: String
) {
    companion object {
        private const val TAG = "StatusBarLyric"
        const val NOTIFICATION_ID = 1003
        const val FLAG_ALWAYS_SHOW_TICKER = 0x01000000
        const val FLAG_ONLY_UPDATE_TICKER = 0x02000000
        private const val MIN_UPDATE_INTERVAL_MS = 350L

        internal fun applyTickerFlags(baseFlags: Int): Int =
            baseFlags or FLAG_ALWAYS_SHOW_TICKER or FLAG_ONLY_UPDATE_TICKER

        internal fun shouldEmitUpdate(
            newLine: String?,
            lastLine: String?,
            nowMs: Long,
            lastEmitMs: Long
        ): Boolean {
            val clean = newLine?.trim().orEmpty()
            if (clean.isEmpty() || clean == lastLine) return false
            return (nowMs - lastEmitMs) >= MIN_UPDATE_INTERVAL_MS
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private var lastEmittedLine: String? = null
    private var lastEmitTimeMs: Long = 0L

    fun updateLyric(line: String?, enabled: Boolean, isPlaying: Boolean) {
        if (!enabled || !isPlaying || line.isNullOrBlank()) {
            clear()
            return
        }
        val clean = line.trim()
        val now = SystemClock.elapsedRealtime()
        if (!shouldEmitUpdate(clean, lastEmittedLine, now, lastEmitTimeMs)) return

        lastEmittedLine = clean
        lastEmitTimeMs = now

        runCatching {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(clean)
                .setTicker(clean)
                .setOngoing(false)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            val notification: Notification = builder.build()
            notification.flags = applyTickerFlags(notification.flags)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }.onFailure { e ->
            Log.v(TAG, "Unable to post OEM status-bar lyric ticker", e)
        }
    }

    fun clear() {
        if (lastEmittedLine == null) return
        lastEmittedLine = null
        lastEmitTimeMs = 0L
        runCatching { notificationManager?.cancel(NOTIFICATION_ID) }
    }
}
