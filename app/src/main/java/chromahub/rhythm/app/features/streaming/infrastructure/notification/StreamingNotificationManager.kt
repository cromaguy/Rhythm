/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.infrastructure.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages notifications for streaming service operations.
 * Handles authentication, syncing, and interaction notifications with consistent styling.
 */
class StreamingNotificationManager(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        const val STREAMING_CHANNEL_ID = "streaming_notifications"
        const val STREAMING_AUTH_CHANNEL_ID = "streaming_auth_notifications"
        
        const val NOTIFICATION_ID_SYNC = 2001
        const val NOTIFICATION_ID_AUTH = 2002
        const val NOTIFICATION_ID_LIKE = 2003
        const val NOTIFICATION_ID_PLAYLIST = 2004
        const val NOTIFICATION_ID_QUALITY = 2005
        const val NOTIFICATION_ID_ERROR = 2006
        const val NOTIFICATION_ID_OFFLINE = 2007
    }
    
    init {
        ensureNotificationChannels()
    }
    
    private fun ensureNotificationChannels() {
        // Main streaming notifications channel
        val streamingChannel = NotificationChannel(
            STREAMING_CHANNEL_ID,
            context.getString(R.string.notification_streaming_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_streaming_channel_desc)
            enableLights(true)
            lightColor = 0xFF5B21B6.toInt() // Purple accent
            setShowBadge(true)
        }

        // Auth-specific channel for immediate notifications
        val authChannel = NotificationChannel(
            STREAMING_AUTH_CHANNEL_ID,
            context.getString(R.string.notification_streaming_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_streaming_auth_channel_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(streamingChannel)
        notificationManager.createNotificationChannel(authChannel)
    }
    
    // ========== Authentication Notifications ==========
    
    private fun isNotificationsEnabled(): Boolean {
        return chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context).streamingNotificationsEnabled.value
    }

    /**
     * Show notification when streaming service is successfully connected
     */
    fun notifyAuthenticationSuccess(serviceName: String) {
        if (!isNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "streaming")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, STREAMING_AUTH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_auth_title))
            .setContentText(context.getString(R.string.notification_streaming_auth_connected, serviceName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                context.getString(R.string.notification_streaming_auth_connected, serviceName)
            ))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_AUTH, notification)
        
        // Auto-dismiss after 5 seconds
        scope.launch {
            delay(5000)
            notificationManager.cancel(NOTIFICATION_ID_AUTH)
        }
    }
    
    /**
     * Show notification when authentication fails
     */
    fun notifyAuthenticationFailed(serviceName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "streaming_setup")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, STREAMING_AUTH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Connection Failed")
            .setContentText(context.getString(R.string.notification_streaming_auth_failed, serviceName))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_AUTH, notification)
    }
    
    /**
     * Show notification when session has expired
     */
    fun notifySessionExpired(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_AUTH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Session Expired")
            .setContentText(context.getString(R.string.notification_streaming_auth_expired, serviceName))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_AUTH, notification)
    }
    
    // ========== Library Sync Notifications ==========
    
    /**
     * Show notification when library sync starts
     */
    fun notifySyncStarted(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_sync_title))
            .setContentText(context.getString(R.string.notification_streaming_sync_started, serviceName))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
    }
    
    /**
     * Update sync progress notification
     */
    fun updateSyncProgress(
        songCount: Int,
        albumCount: Int,
        artistCount: Int,
        current: Int = 0,
        total: Int = 0
    ) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_sync_title))
            .setContentText(context.resources.getQuantityString(
                R.plurals.notification_streaming_sync_progress,
                songCount, songCount, albumCount, artistCount
            ))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                context.resources.getQuantityString(
                    R.plurals.notification_streaming_sync_progress,
                    songCount, songCount, albumCount, artistCount
                )
            ))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(
                if (total > 0) total else 100,
                if (total > 0) current else 0,
                total <= 0
            )
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
    }
    
    /**
     * Show notification when sync completes successfully
     */
    fun notifySyncComplete(songCount: Int, serviceName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "streaming_library")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2201, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_sync_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notification_streaming_sync_complete, songCount, songCount, serviceName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                context.resources.getQuantityString(R.plurals.notification_streaming_sync_complete, songCount, songCount, serviceName)
            ))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
        
        // Auto-dismiss after 6 seconds
        scope.launch {
            delay(6000)
            notificationManager.cancel(NOTIFICATION_ID_SYNC)
        }
    }
    
    /**
     * Show notification when sync fails
     */
    fun notifySyncFailed(error: String? = null) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_sync_title))
            .setContentText(context.getString(R.string.notification_streaming_sync_failed))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                error ?: context.getString(R.string.notification_streaming_sync_failed)
            ))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
    }
    
    // ========== Liked Songs Notifications ==========
    
    /**
     * Show notification when song is liked
     */
    fun notifyLikeSong(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_liked_title))
            .setContentText(context.getString(R.string.notification_streaming_liked_added, serviceName))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_LIKE, notification)
        
        // Auto-dismiss after 2 seconds
        scope.launch {
            delay(2000)
            notificationManager.cancel(NOTIFICATION_ID_LIKE)
        }
    }
    
    /**
     * Show notification when song is unliked
     */
    fun notifyUnlikeSong(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_liked_title))
            .setContentText(context.getString(R.string.notification_streaming_liked_removed, serviceName))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_LIKE, notification)
        
        // Auto-dismiss after 2 seconds
        scope.launch {
            delay(2000)
            notificationManager.cancel(NOTIFICATION_ID_LIKE)
        }
    }
    
    // ========== Playlist Notifications ==========
    
    /**
     * Show notification for playlist creation
     */
    fun notifyPlaylistCreated(playlistName: String, serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_playlist_title))
            .setContentText(context.getString(R.string.notification_streaming_playlist_created, playlistName, serviceName))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_PLAYLIST, notification)
        
        scope.launch {
            delay(3000)
            notificationManager.cancel(NOTIFICATION_ID_PLAYLIST)
        }
    }
    
    /**
     * Show notification for playlist update
     */
    fun notifyPlaylistUpdated(playlistName: String, serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_playlist_title))
            .setContentText(context.getString(R.string.notification_streaming_playlist_updated, playlistName, serviceName))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_PLAYLIST, notification)
        
        scope.launch {
            delay(3000)
            notificationManager.cancel(NOTIFICATION_ID_PLAYLIST)
        }
    }
    
    /**
     * Show notification for playlist deletion
     */
    fun notifyPlaylistDeleted(playlistName: String, serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_playlist_title))
            .setContentText(context.getString(R.string.notification_streaming_playlist_deleted, playlistName, serviceName))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_PLAYLIST, notification)
        
        scope.launch {
            delay(3000)
            notificationManager.cancel(NOTIFICATION_ID_PLAYLIST)
        }
    }
    
    // ========== Quality & Network Notifications ==========
    
    /**
     * Show notification when quality is switched
     */
    fun notifyQualitySwitched(qualityName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_quality_title))
            .setContentText(context.getString(R.string.notification_streaming_quality_switched, qualityName))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_QUALITY, notification)
        
        scope.launch {
            delay(3000)
            notificationManager.cancel(NOTIFICATION_ID_QUALITY)
        }
    }
    
    /**
     * Show notification during buffering
     */
    fun notifyBuffering(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_quality_title))
            .setContentText(context.getString(R.string.notification_streaming_buffering, serviceName))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_QUALITY, notification)
    }
    
    /**
     * Dismiss buffering notification
     */
    fun notifyBufferingComplete() {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_quality_title))
            .setContentText(context.getString(R.string.notification_streaming_buffering_complete))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_QUALITY, notification)
        
        scope.launch {
            delay(2000)
            notificationManager.cancel(NOTIFICATION_ID_QUALITY)
        }
    }
    
    // ========== Error Notifications ==========
    
    /**
     * Show notification for streaming errors
     */
    fun notifyStreamingError(serviceName: String, errorMessage: String? = null) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_error_title))
            .setContentText(context.getString(R.string.notification_streaming_error_generic, serviceName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                errorMessage ?: context.getString(R.string.notification_streaming_error_generic, serviceName)
            ))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
    
    /**
     * Show notification for no internet connection
     */
    fun notifyNoInternet() {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_error_title))
            .setContentText(context.getString(R.string.notification_streaming_error_no_internet))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
    
    /**
     * Show notification for account issues
     */
    fun notifyAccountIssue(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_error_title))
            .setContentText(context.getString(R.string.notification_streaming_error_account_issue, serviceName))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
    
    // ========== Offline Notifications ==========
    
    /**
     * Show notification when offline cache is syncing
     */
    fun notifyOfflineSyncStarted(serviceName: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_offline_title))
            .setContentText(context.getString(R.string.notification_streaming_offline_sync, serviceName))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_OFFLINE, notification)
    }
    
    /**
     * Show notification when offline cache sync completes
     */
    fun notifyOfflineSyncComplete(songCount: Int) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_offline_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notification_streaming_offline_sync_complete, songCount, songCount))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_OFFLINE, notification)
        
        scope.launch {
            delay(4000)
            notificationManager.cancel(NOTIFICATION_ID_OFFLINE)
        }
    }
    
    /**
     * Show notification when cache is cleared
     */
    fun notifyCacheCleared(spaceSaved: String) {
        val notification = NotificationCompat.Builder(context, STREAMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_streaming_offline_title))
            .setContentText(context.getString(R.string.notification_streaming_offline_cache_cleared, spaceSaved))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_OFFLINE, notification)
        
        scope.launch {
            delay(3000)
            notificationManager.cancel(NOTIFICATION_ID_OFFLINE)
        }
    }
    
    /**
     * Cancel all streaming notifications
     */
    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID_SYNC)
        notificationManager.cancel(NOTIFICATION_ID_AUTH)
        notificationManager.cancel(NOTIFICATION_ID_LIKE)
        notificationManager.cancel(NOTIFICATION_ID_PLAYLIST)
        notificationManager.cancel(NOTIFICATION_ID_QUALITY)
        notificationManager.cancel(NOTIFICATION_ID_ERROR)
        notificationManager.cancel(NOTIFICATION_ID_OFFLINE)
    }
}
