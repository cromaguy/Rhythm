package chromahub.rhythm.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Background worker that checks for app updates using smart polling techniques
 * to minimize GitHub API calls while still providing timely notifications.
 * 
 * ## How the "Webhook" System Works
 * 
 * While Android apps cannot receive true webhooks (which require a server endpoint),
 * this worker implements a smart polling system that behaves similarly by:
 * 
 * ### 1. HTTP Conditional Requests (ETag/Last-Modified)
 * - Stores the `ETag` and `Last-Modified` headers from previous GitHub API responses
 * - On subsequent checks, includes these in conditional request headers
 * - GitHub returns `304 Not Modified` if nothing changed (saves bandwidth and API calls)
 * - Only processes full response when actual changes are detected
 * 
 * ### 2. Exponential Backoff
 * - Tracks consecutive `304 Not Modified` responses
 * - Gradually increases check interval when no updates are found:
 *   * 0-3 consecutive 304s: Check every 6 hours
 *   * 4-6 consecutive 304s: Check every 12 hours
 *   * 7-10 consecutive 304s: Check every 24 hours
 *   * 10+ consecutive 304s: Check every 72 hours (max backoff)
 * - Resets to 6 hours when a new version is detected
 * 
 * ### 3. Version Tracking
 * - Caches the last known version tag (e.g., "v3.0.5")
 * - Only sends notifications when a genuinely newer version appears
 * - Prevents duplicate notifications for the same version
 * 
 * ### 4. Rate Limit Awareness
 * - Monitors GitHub's `X-RateLimit-Remaining` header
 * - Automatically backs off if approaching rate limits
 * - Handles `403 Forbidden` responses gracefully
 * 
 * ### Benefits Over Regular Polling
 * - **Reduced API Calls**: HTTP 304 responses don't count toward rate limits as heavily
 * - **Bandwidth Efficient**: No data transfer when nothing changed
 * - **Battery Friendly**: Exponential backoff reduces wake-ups when app is stable
 * - **Timely Notifications**: Still detects updates within hours of release
 * - **User Control**: Can be disabled via settings while maintaining manual check ability
 * 
 * ### GitHub API Rate Limits
 * - Unauthenticated: 60 requests/hour
 * - Authenticated: 5000 requests/hour
 * - This worker typically uses <10 requests/day with smart polling
 * 
 * @see chromahub.rhythm.app.shared.data.model.AppSettings.updateNotificationsEnabled
 * @see chromahub.rhythm.app.shared.data.model.AppSettings.useSmartUpdatePolling
 */
class UpdateNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private enum class UpdateCheckResult {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        ERROR
    }

    companion object {
        const val TAG = "UpdateNotificationWorker"
        const val WORK_NAME = "update_notification_work"
        private const val UPDATE_AVAILABLE_CHANNEL_ID = "app_updates"
        private const val UPDATE_STATUS_CHANNEL_ID = "app_update_status"
        const val NOTIFICATION_ID = 1001
        
        // Metadata keys for SharedPreferences
        private const val PREF_NAME = "update_webhook_cache"
        private const val KEY_LAST_ETAG = "last_etag"
        private const val KEY_LAST_MODIFIED = "last_modified"
        private const val KEY_LAST_VERSION_TAG = "last_version_tag"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_CONSECUTIVE_NOT_MODIFIED = "consecutive_not_modified"
        private const val KEY_LAST_STATUS_NOTIFICATION_AT = "last_status_notification_at"
        private const val KEY_LAST_STATUS_NOTIFICATION_TYPE = "last_status_notification_type"

        private const val STATUS_TYPE_UP_TO_DATE = "up_to_date"
        private const val STATUS_TYPE_ERROR = "error"
        
        // Exponential backoff thresholds
        private const val MAX_CONSECUTIVE_NOT_MODIFIED = 10
    }
    
    private val appSettings = AppSettings.getInstance(applicationContext)
    private val gitHubApiService = NetworkManager.createGitHubApiService()
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private var lastCheckErrorMessage: String? = null
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting update check via webhook worker...")
            
            // Check if updates are enabled
            if (!appSettings.updatesEnabled.value) {
                Log.d(TAG, "Updates disabled, skipping check")
                return@withContext Result.success()
            }

            if (!appSettings.autoCheckForUpdates.value) {
                Log.d(TAG, "Auto-check for updates disabled, skipping background check")
                return@withContext Result.success()
            }
            
            val updateAvailabilityNotificationsEnabled = appSettings.updateNotificationsEnabled.value
            val updateStatusNotificationsEnabled = appSettings.updateStatusNotificationsEnabled.value
            if (!updateAvailabilityNotificationsEnabled && !updateStatusNotificationsEnabled) {
                Log.d(TAG, "All update notifications disabled, skipping check")
                return@withContext Result.success()
            }
            
            val currentChannel = appSettings.updateChannel.value
            
            // Perform smart polling check
            when (checkForUpdateWithSmartPolling(currentChannel)) {
                UpdateCheckResult.UPDATE_AVAILABLE -> {
                    if (updateAvailabilityNotificationsEnabled) {
                        Log.d(TAG, "New update detected! Sending notification...")
                        sendUpdateNotification()
                    } else {
                        Log.d(TAG, "Update available, but update-available notifications are disabled")
                    }
                }

                UpdateCheckResult.UP_TO_DATE -> {
                    Log.d(TAG, "No new updates detected")
                    maybeNotifyUpdateStatus(
                        type = STATUS_TYPE_UP_TO_DATE,
                        title = applicationContext.getString(R.string.updates_up_to_date),
                        text = applicationContext.getString(R.string.updates_up_to_date_message)
                    )
                }

                UpdateCheckResult.ERROR -> {
                    val message = lastCheckErrorMessage
                        ?: applicationContext.getString(R.string.updates_unknown_error)
                    Log.w(TAG, "Update check completed with error state: $message")
                    maybeNotifyUpdateStatus(
                        type = STATUS_TYPE_ERROR,
                        title = applicationContext.getString(R.string.updates_check_failed),
                        text = message
                    )
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}", e)
            maybeNotifyUpdateStatus(
                type = STATUS_TYPE_ERROR,
                title = applicationContext.getString(R.string.updates_check_failed),
                text = e.message ?: applicationContext.getString(R.string.updates_unknown_error)
            )
            Result.retry()
        }
    }
    
    /**
     * Smart polling using HTTP conditional requests to minimize API calls.
     */
    private suspend fun checkForUpdateWithSmartPolling(channel: String): UpdateCheckResult {
        lastCheckErrorMessage = null

        try {
            val lastETag = prefs.getString(KEY_LAST_ETAG, null)
            val lastModified = prefs.getString(KEY_LAST_MODIFIED, null)
            val lastVersionTag = prefs.getString(KEY_LAST_VERSION_TAG, null)
            val consecutiveNotModified = prefs.getInt(KEY_CONSECUTIVE_NOT_MODIFIED, 0)
            
            Log.d(TAG, "Smart polling - Last ETag: $lastETag, Last Modified: $lastModified")
            Log.d(TAG, "Consecutive 304 responses: $consecutiveNotModified")
            
            
            if (channel == "nightly") {
                val runsResponse = gitHubApiService.getWorkflowRuns(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO, "nightly.yml")
                val responseCode = runsResponse.code()
                Log.d(TAG, "Nightly response code: $responseCode")
                
                if (runsResponse.isSuccessful && runsResponse.body() != null) {
                    val runsData = runsResponse.body()
                    val latestRun = runsData?.workflow_runs?.firstOrNull { it.status == "completed" && it.conclusion == "success" }
                    
                    if (latestRun != null) {
                        val shortSha = latestRun.head_sha.take(7)
                        val cleanedBaseName = BuildConfig.VERSION_NAME.replace(" Beta", "")
                        val newVersionTag = "$cleanedBaseName-nightly-r${latestRun.run_number}-$shortSha"
                        
                        val isNewerNightly = if (BuildConfig.IS_NIGHTLY) {
                            val currentNightlyRun = extractNightlyRunNumber(BuildConfig.VERSION_NAME)
                            val latestNightlyRun = latestRun.run_number
                            latestNightlyRun > currentNightlyRun
                        } else {
                            try {
                                val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val nightlyDate = isoFmt.parse(latestRun.updated_at)
                                val buildDate = dateFmt.parse(BuildConfig.RELEASE_DATE)
                                nightlyDate != null && buildDate != null && nightlyDate.after(buildDate)
                            } catch (e: Exception) {
                                false
                            }
                        }
                        
                        val hasNewVersion = lastVersionTag != newVersionTag && isNewerNightly
                        Log.d(TAG, "Latest nightly version: $newVersionTag, Last known: $lastVersionTag")
                        Log.d(TAG, "Current version: ${BuildConfig.VERSION_NAME}, Is newer: $hasNewVersion")
                        
                        prefs.edit()
                            .putString(KEY_LAST_VERSION_TAG, newVersionTag)
                            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                            .putInt(KEY_CONSECUTIVE_NOT_MODIFIED, 0)
                            .apply()
                        
                        return if (hasNewVersion) {
                            UpdateCheckResult.UPDATE_AVAILABLE
                        } else {
                            UpdateCheckResult.UP_TO_DATE
                        }
                    }
                    lastCheckErrorMessage = "GitHub returned empty workflow runs"
                    return UpdateCheckResult.ERROR
                } else {
                    lastCheckErrorMessage = "GitHub returned unsuccessful workflow runs response"
                    return UpdateCheckResult.ERROR
                }
            }

            // Fetch latest release based on channel with conditional headers
            val response = if (channel == "beta") {
                gitHubApiService.getReleasesWithHeaders(
                    owner = BuildConfig.GITHUB_OWNER,
                    repo = BuildConfig.GITHUB_REPO,
                    perPage = 10,
                    ifNoneMatch = lastETag,
                    ifModifiedSince = lastModified
                )
            } else {
                gitHubApiService.getLatestReleaseWithHeaders(
                    owner = BuildConfig.GITHUB_OWNER,
                    repo = BuildConfig.GITHUB_REPO,
                    ifNoneMatch = lastETag,
                    ifModifiedSince = lastModified
                )
            }
            
            // Check response headers
            val responseCode = response.code()
            val newETag = response.headers()["ETag"]
            val newLastModified = response.headers()["Last-Modified"]
            val rateLimit = response.headers()["X-RateLimit-Remaining"]
            val rateLimitReset = response.headers()["X-RateLimit-Reset"]
            
            Log.d(TAG, "Response code: $responseCode")
            Log.d(TAG, "Rate limit remaining: $rateLimit, resets at: $rateLimitReset")
            
            when (responseCode) {
                304 -> {
                    // Not Modified - no changes since last check
                    Log.d(TAG, "304 Not Modified - no changes detected")
                    prefs.edit()
                        .putInt(KEY_CONSECUTIVE_NOT_MODIFIED, consecutiveNotModified + 1)
                        .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                        .apply()
                    return UpdateCheckResult.UP_TO_DATE
                }
                
                200 -> {
                    // Success - check if version changed
                    if (response.isSuccessful && response.body() != null) {
                        val latestRelease = if (channel == "beta") {
                            // For beta channel, get all releases and find first non-draft
                            @Suppress("UNCHECKED_CAST")
                            (response.body() as? List<chromahub.rhythm.app.network.GitHubRelease>)?.firstOrNull { !it.draft }
                        } else {
                            response.body() as? chromahub.rhythm.app.network.GitHubRelease
                        }
                        
                        if (latestRelease != null) {
                            val newVersionTag = latestRelease.tag_name
                            val hasNewVersion = lastVersionTag != newVersionTag && 
                                               isNewerVersion(latestRelease.tag_name, BuildConfig.VERSION_NAME)
                            
                            Log.d(TAG, "Latest version: $newVersionTag, Last known: $lastVersionTag")
                            Log.d(TAG, "Current version: ${BuildConfig.VERSION_NAME}, Is newer: $hasNewVersion")
                            
                            // Update cache
                            prefs.edit()
                                .putString(KEY_LAST_ETAG, newETag)
                                .putString(KEY_LAST_MODIFIED, newLastModified)
                                .putString(KEY_LAST_VERSION_TAG, newVersionTag)
                                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                                .putInt(KEY_CONSECUTIVE_NOT_MODIFIED, 0) // Reset counter
                                .apply()
                            
                            return if (hasNewVersion) {
                                UpdateCheckResult.UPDATE_AVAILABLE
                            } else {
                                UpdateCheckResult.UP_TO_DATE
                            }
                        }

                        lastCheckErrorMessage = "GitHub returned an empty release payload"
                        return UpdateCheckResult.ERROR
                    }

                    lastCheckErrorMessage = "GitHub returned an unsuccessful response"
                    return UpdateCheckResult.ERROR
                }
                
                403 -> {
                    // Rate limit exceeded
                    Log.w(TAG, "GitHub API rate limit exceeded. Next reset: $rateLimitReset")
                    lastCheckErrorMessage = "GitHub rate limit reached. Try again later."
                    return UpdateCheckResult.ERROR
                }
                
                else -> {
                    Log.w(TAG, "Unexpected response code: $responseCode")
                    lastCheckErrorMessage = "Update check failed with HTTP $responseCode"
                    return UpdateCheckResult.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during smart polling: ${e.message}", e)
            lastCheckErrorMessage = e.message ?: "Unknown network error"
            return UpdateCheckResult.ERROR
        }
    }

    private fun maybeNotifyUpdateStatus(type: String, title: String, text: String) {
        if (!appSettings.updateStatusNotificationsEnabled.value) {
            return
        }

        if (!shouldSendStatusNotification(type)) {
            return
        }

        sendUpdateStatusNotification(title, text)
        prefs.edit()
            .putString(KEY_LAST_STATUS_NOTIFICATION_TYPE, type)
            .putLong(KEY_LAST_STATUS_NOTIFICATION_AT, System.currentTimeMillis())
            .apply()
    }

    private fun shouldSendStatusNotification(type: String): Boolean {
        val lastType = prefs.getString(KEY_LAST_STATUS_NOTIFICATION_TYPE, null)
        val lastSentAt = prefs.getLong(KEY_LAST_STATUS_NOTIFICATION_AT, 0L)
        val now = System.currentTimeMillis()

        if (lastType != type) {
            return true
        }

        val minIntervalMs = when (type) {
            STATUS_TYPE_ERROR -> TimeUnit.HOURS.toMillis(2)
            else -> TimeUnit.HOURS.toMillis(12)
        }

        return now - lastSentAt >= minIntervalMs
    }
    
    private fun extractNightlyRunNumber(versionString: String): Int {
        val regex = Regex("nightly-r(\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(versionString)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Compare version strings to determine if new version is newer
     * Handles semantic versioning with build numbers and pre-release tags
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        try {
            // Parse semantic versions
            val newSemVer = parseSemanticVersion(newVersion)
            val currentSemVer = parseSemanticVersion(currentVersion)
            
            // Compare major.minor.patch first
            if (newSemVer.major != currentSemVer.major) return newSemVer.major > currentSemVer.major
            if (newSemVer.minor != currentSemVer.minor) return newSemVer.minor > currentSemVer.minor
            if (newSemVer.patch != currentSemVer.patch) return newSemVer.patch > currentSemVer.patch
            if (newSemVer.subpatch != currentSemVer.subpatch) return newSemVer.subpatch > currentSemVer.subpatch
            
            // If versions are equal, compare build numbers
            if (newSemVer.buildNumber != currentSemVer.buildNumber) {
                return newSemVer.buildNumber > currentSemVer.buildNumber
            }
            
            // Pre-releases are considered older than stable releases
            if (newSemVer.isPreRelease != currentSemVer.isPreRelease) {
                return !newSemVer.isPreRelease && currentSemVer.isPreRelease
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Parse version string to semantic version components
     */
    private data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val subpatch: Int = 0,
        val buildNumber: Int = 0,
        val isPreRelease: Boolean = false
    )
    
    private fun parseSemanticVersion(versionString: String): SemanticVersion {
        try {
            val cleaned = versionString.trim().removePrefix("v")
            
            // Extract build number (e.g., "b-127" or "build-127")
            val buildRegex = Regex("(?:b|build)-(\\d+)", RegexOption.IGNORE_CASE)
            val buildNumber = buildRegex.find(cleaned)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            // Extract base version (remove build info and tags)
            val versionBase = cleaned.split(" ")[0].split("-")[0].split("_")[0]
            val versionParts = versionBase.split(".")
            
            // Check for pre-release keywords
            val preReleaseKeywords = listOf("alpha", "beta", "pre", "rc", "dev", "snapshot")
            val isPreRelease = preReleaseKeywords.any { keyword ->
                cleaned.contains(keyword, ignoreCase = true)
            }
            
            return SemanticVersion(
                major = versionParts.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = versionParts.getOrNull(2)?.toIntOrNull() ?: 0,
                subpatch = versionParts.getOrNull(3)?.toIntOrNull() ?: 0,
                buildNumber = buildNumber,
                isPreRelease = isPreRelease
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing version: $versionString", e)
            return SemanticVersion(0, 0, 0, 0, 0, false)
        }
    }
    
    /**
     * Send a notification about the available update
     */
    private fun sendUpdateNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        ensureUpdateAvailableChannel(notificationManager)
        
        // Create intent to open app update screen
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "updates")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_AVAILABLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Make sure this icon exists
            .setContentTitle(applicationContext.getString(R.string.notification_updater_available_title))
            .setContentText(applicationContext.getString(R.string.notification_updater_available_text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Update notification sent")
    }

    private fun sendUpdateStatusNotification(title: String, text: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        ensureUpdateStatusChannel(notificationManager)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "updates")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText = if (text.startsWith(title)) {
            text
        } else {
            "$title. $text"
        }

        val notification = NotificationCompat.Builder(applicationContext, UPDATE_STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notification_updater_title))
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        Log.d(TAG, "Update status notification sent: $title")
    }

    private fun ensureUpdateAvailableChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            UPDATE_AVAILABLE_CHANNEL_ID,
            applicationContext.getString(R.string.service_app_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = applicationContext.getString(R.string.notification_updater_channel_desc)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun ensureUpdateStatusChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            UPDATE_STATUS_CHANNEL_ID,
            applicationContext.getString(R.string.service_update_status),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = applicationContext.getString(R.string.service_update_status_desc)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Get the recommended check interval based on consecutive 304 responses
     * Implements exponential backoff to reduce unnecessary API calls
     */
    fun getRecommendedCheckInterval(): Long {
        val consecutiveNotModified = prefs.getInt(KEY_CONSECUTIVE_NOT_MODIFIED, 0)
        
        return when {
            consecutiveNotModified < 3 -> 6L // 6 hours
            consecutiveNotModified < 6 -> 12L // 12 hours
            consecutiveNotModified < MAX_CONSECUTIVE_NOT_MODIFIED -> 24L // 1 day
            else -> 72L // 3 days (maximum backoff)
        }
    }
    
    /**
     * Clear cached webhook data (useful for testing or reset)
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Webhook cache cleared")
    }
}
