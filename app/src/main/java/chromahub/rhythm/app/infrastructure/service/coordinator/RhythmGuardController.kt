/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.coordinator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationCompat
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.shared.data.model.AppSettings

/**
 * Coordinates Rhythm Guard hearing-protection enforcement (volume clamping on headphones/BT
 * vs speaker policy) and manages exposure alert/countdown notifications.
 */
class RhythmGuardController(
    private val context: Context,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "RhythmGuardController"
        const val CHANNEL_ALERTS = "rhythm_guard_alerts"
        const val CHANNEL_TIMERS = "rhythm_guard_timers"
        private const val NOTIF_ID_ALERT = 1301
        private const val NOTIF_ID_TIMER = 1302
    }

    fun checkAndClampVolumeIfEnabled() {
        try {
            val mode = appSettings.rhythmGuardMode.value
            if (mode != AppSettings.RHYTHM_GUARD_MODE_AUTO) return

            val age = appSettings.rhythmGuardAge.value
            val policy = appSettings.getRhythmGuardPolicy(age)
            val activeThreshold = policy.maxVolumeThreshold

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val isSpeaker = isSpeakerOutputActive(audioManager)
            val applyVolumeLimitOnSpeaker = appSettings.rhythmGuardApplyVolumeLimitOnSpeaker.value
            if (!applyVolumeLimitOnSpeaker && isSpeaker) return

            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolumeFraction = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0f

            if (currentVolumeFraction - activeThreshold > 0.01f) {
                val targetVolume = Math.round(activeThreshold * maxVolume).coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                appSettings.rhythmGuardPrefs.recordClampEvent()
                Log.d(TAG, "Rhythm Guard Auto: clamped system volume from $currentVolumeFraction to $activeThreshold")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clamping volume in background", e)
        }
    }

    fun isSpeakerOutputActive(audioManager: AudioManager): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasSpeaker = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val hasExternalSink = devices.any {
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET) && it.isSink
        }
        return hasSpeaker && !hasExternalSink
    }

    fun showAlertNotification(title: String, text: String, riskLevel: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            8101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val priority = when (riskLevel) {
            "SEVERE", "HIGH" -> NotificationCompat.PRIORITY_HIGH
            "MODERATE" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(priority)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIF_ID_ALERT, notification)
    }

    fun showTimerNotification(title: String, text: String, remainingSeconds: Long, totalSeconds: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels(notificationManager)

        val safeTotal = totalSeconds.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val safeRemaining = remainingSeconds.coerceIn(0L, safeTotal.toLong()).toInt()
        val completed = (safeTotal - safeRemaining).coerceIn(0, safeTotal)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            8102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_TIMERS)
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text\n${context.getString(chromahub.rhythm.app.R.string.settings_rhythm_guard_notification_tap_open)}"
                )
            )
            .setProgress(safeTotal, completed, false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIF_ID_TIMER, notification)
    }

    fun ensureChannels(notificationManager: NotificationManager) {
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(chromahub.rhythm.app.R.string.service_rhythm_guard_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(chromahub.rhythm.app.R.string.service_rhythm_guard_alerts_desc)
            enableVibration(true)
        }
        val timerChannel = NotificationChannel(
            CHANNEL_TIMERS,
            context.getString(chromahub.rhythm.app.R.string.service_rhythm_guard_timers),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(chromahub.rhythm.app.R.string.service_rhythm_guard_timers_desc)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(alertChannel)
        notificationManager.createNotificationChannel(timerChannel)
    }
}
