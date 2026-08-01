package chromahub.rhythm.app.features.local.presentation.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.features.local.data.repository.MusicRepository
import chromahub.rhythm.app.shared.data.model.ScanPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class LibraryNotificationManager(private val context: Application) {

    companion object {
        private const val TAG = "LibraryNotificationManager"
        
        const val OPERATIONS_NOTIFICATION_CHANNEL_ID = "rhythm_operations"
        const val MEDIA_SCAN_NOTIFICATION_ID = 1501
        const val PLAYLIST_IMPORT_NOTIFICATION_ID = 1502
        const val PLAYLIST_EXPORT_NOTIFICATION_ID = 1503
        const val LIBRARY_SETUP_NOTIFICATION_ID = 1504
        const val OPERATION_NOTIFICATION_AUTO_DISMISS_MS = 6000L
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val operationNotificationDismissJobs = mutableMapOf<Int, Job>()
    private var mediaScanNotificationJob: Job? = null
    private var mediaScanNotificationSequence: Long = 0L
    private var lastMediaScanProgressKey: String? = null
    
    private var librarySetupNotificationArmed = false
    private var librarySetupProcessingObserved = false
    private var lastLibrarySetupProgressText: String? = null
    private var librarySetupCompletionDebounceJob: Job? = null
    private var isChannelCreated = false

    fun ensureOperationsNotificationChannel() {
        if (isChannelCreated) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            isChannelCreated = true
            return
        }

        val channel = NotificationChannel(
            OPERATIONS_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_operations_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_operations_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
        isChannelCreated = true
    }

    private fun createMainActivityPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showOperationProgressNotification(
        notificationId: Int,
        title: String,
        content: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
        requestCode: Int = notificationId
    ) {
        val appSettings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context)
        if (!appSettings.libraryOperationsNotificationsEnabled.value) {
            return
        }

        ensureOperationsNotificationChannel()
        operationNotificationDismissJobs.remove(notificationId)?.cancel()

        val notification = NotificationCompat.Builder(
            context,
            OPERATIONS_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(max.coerceAtLeast(0), progress.coerceAtLeast(0), indeterminate)
            .setContentIntent(createMainActivityPendingIntent(requestCode))
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showOperationResultNotification(
        scope: CoroutineScope,
        notificationId: Int,
        title: String,
        content: String,
        isError: Boolean,
        autoDismissMs: Long = OPERATION_NOTIFICATION_AUTO_DISMISS_MS,
        requestCode: Int = notificationId
    ) {
        ensureOperationsNotificationChannel()
        operationNotificationDismissJobs.remove(notificationId)?.cancel()

        val notification = NotificationCompat.Builder(
            context,
            OPERATIONS_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(
                if (isError) {
                    NotificationCompat.CATEGORY_ERROR
                } else {
                    NotificationCompat.CATEGORY_STATUS
                }
            )
            .setPriority(
                if (isError) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_LOW
                }
            )
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentIntent(createMainActivityPendingIntent(requestCode))
            .build()

        notificationManager.notify(notificationId, notification)
        if (autoDismissMs > 0L) {
            operationNotificationDismissJobs[notificationId] = scope.launch {
                delay(autoDismissMs)
                notificationManager.cancel(notificationId)
                operationNotificationDismissJobs.remove(notificationId)
            }
        }
    }

    fun startMediaScanProgressNotifications(
        scope: CoroutineScope,
        repository: MusicRepository,
        sequence: Long
    ) {
        mediaScanNotificationSequence = sequence
        mediaScanNotificationJob?.cancel()
        lastMediaScanProgressKey = null

        showOperationProgressNotification(
            notificationId = MEDIA_SCAN_NOTIFICATION_ID,
            title = context.getString(R.string.notification_media_scan_title),
            content = context.getString(R.string.scanning_media),
            indeterminate = true
        )

        var lastPostTime = 0L
        mediaScanNotificationJob = scope.launch(Dispatchers.Default) {
            repository.scanProgress.collect { progressState ->
                if (sequence != mediaScanNotificationSequence) {
                    return@collect
                }

                val stage = progressState.stage
                if (stage is ScanPhase.Idle) {
                    return@collect
                }

                // Throttle notifications during progress phases to once every 500ms
                val isProgressStage = stage is ScanPhase.Songs || stage is ScanPhase.Incremental
                val isLastItem = progressState.total > 0 && progressState.current == progressState.total
                val currentTime = System.currentTimeMillis()
                if (isProgressStage && !isLastItem && currentTime - lastPostTime < 500L) {
                    return@collect
                }
                lastPostTime = currentTime

                val stageLabel = when (stage) {
                    is ScanPhase.Songs,
                    is ScanPhase.Incremental -> {
                        context.getString(R.string.notification_media_scan_stage_songs)
                    }

                    is ScanPhase.SavingDb -> {
                        context.getString(R.string.notification_media_scan_stage_saving)
                    }

                    is ScanPhase.Complete -> {
                        context.getString(R.string.notification_media_scan_stage_finishing)
                    }

                    is ScanPhase.Error,
                    is ScanPhase.PermissionDenied -> {
                        context.getString(R.string.notification_media_scan_failed)
                    }

                    is ScanPhase.Idle -> "" // handled above
                }

                val hasDeterminateProgress = progressState.total > 0
                val safeTotal = if (hasDeterminateProgress) progressState.total else 0
                val safeCurrent = if (hasDeterminateProgress) {
                    progressState.current.coerceIn(0, progressState.total)
                } else {
                    0
                }
                val content = if (hasDeterminateProgress) {
                    "$stageLabel ($safeCurrent/$safeTotal)"
                } else {
                    stageLabel
                }

                val progressKey = "${stage.displayName}|$safeCurrent|$safeTotal"
                if (progressKey == lastMediaScanProgressKey) {
                    return@collect
                }
                lastMediaScanProgressKey = progressKey

                showOperationProgressNotification(
                    notificationId = MEDIA_SCAN_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_media_scan_title),
                    content = content,
                    progress = safeCurrent,
                    max = safeTotal,
                    indeterminate = !hasDeterminateProgress
                )
            }
        }
    }

    fun stopMediaScanProgressNotifications() {
        mediaScanNotificationJob?.cancel()
        mediaScanNotificationJob = null
        lastMediaScanProgressKey = null
    }

    fun clearOperationNotifications() {
        disarmLibrarySetupCompletionNotification()
        stopMediaScanProgressNotifications()
        operationNotificationDismissJobs.values.forEach { it.cancel() }
        operationNotificationDismissJobs.clear()
        notificationManager.cancel(MEDIA_SCAN_NOTIFICATION_ID)
        notificationManager.cancel(PLAYLIST_IMPORT_NOTIFICATION_ID)
        notificationManager.cancel(PLAYLIST_EXPORT_NOTIFICATION_ID)
        notificationManager.cancel(LIBRARY_SETUP_NOTIFICATION_ID)
    }

    fun armLibrarySetupCompletionNotification() {
        librarySetupNotificationArmed = true
        librarySetupProcessingObserved = false
        lastLibrarySetupProgressText = null
    }

    fun disarmLibrarySetupCompletionNotification() {
        librarySetupCompletionDebounceJob?.cancel()
        librarySetupCompletionDebounceJob = null
        librarySetupNotificationArmed = false
        librarySetupProcessingObserved = false
        lastLibrarySetupProgressText = null
    }

    private fun resolveLibrarySetupProgressText(
        isMediaScanRunning: Boolean,
        isGenreDetectionRunning: Boolean,
        isArtworkFetching: Boolean,
        isMetadataExtractionRunning: Boolean
    ): String {
        return when {
            isMediaScanRunning -> context.getString(R.string.scanning_media)
            isGenreDetectionRunning -> context.getString(R.string.detecting_genres)
            isArtworkFetching -> context.getString(R.string.fetching_artwork)
            isMetadataExtractionRunning -> context.getString(R.string.extracting_metadata)
            else -> context.getString(R.string.processing_library)
        }
    }

    fun startLibrarySetupCompletionMonitor(
        scope: CoroutineScope,
        isBackgroundProcessing: StateFlow<Boolean>,
        isMediaScanning: StateFlow<Boolean>,
        isGenreDetectionRunning: StateFlow<Boolean>,
        isFetchingArtwork: StateFlow<Boolean>,
        isExtractingMetadata: StateFlow<Boolean>,
        filteredSongsSize: () -> Int,
        filteredAlbumsSize: () -> Int,
        filteredArtistsSize: () -> Int
    ) {
        scope.launch {
            combine(
                isBackgroundProcessing,
                isMediaScanning,
                isGenreDetectionRunning,
                isFetchingArtwork,
                isExtractingMetadata
            ) { processing, mediaScanRunning, genreDetectionRunning, artworkFetching, metadataExtractionRunning ->
                listOf(
                    processing,
                    mediaScanRunning,
                    genreDetectionRunning,
                    artworkFetching,
                    metadataExtractionRunning
                )
            }.collect { state ->
                if (!librarySetupNotificationArmed) {
                    return@collect
                }

                val processing = state[0]
                val mediaScanRunning = state[1]
                val genreDetectionRunning = state[2]
                val artworkFetching = state[3]
                val metadataExtractionRunning = state[4]

                if (processing) {
                    librarySetupProcessingObserved = true
                    librarySetupCompletionDebounceJob?.cancel()
                    librarySetupCompletionDebounceJob = null

                    // Media scan has its own dedicated progress notification.
                    // Show library-setup progress only for post-scan background tasks.
                    if (mediaScanRunning && !genreDetectionRunning && !artworkFetching && !metadataExtractionRunning) {
                        return@collect
                    }

                    val progressText = resolveLibrarySetupProgressText(
                        isMediaScanRunning = mediaScanRunning,
                        isGenreDetectionRunning = genreDetectionRunning,
                        isArtworkFetching = artworkFetching,
                        isMetadataExtractionRunning = metadataExtractionRunning
                    )

                    if (progressText != lastLibrarySetupProgressText) {
                        lastLibrarySetupProgressText = progressText
                        showOperationProgressNotification(
                            notificationId = LIBRARY_SETUP_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_library_setup_title),
                            content = progressText,
                            indeterminate = true
                        )
                    }

                    return@collect
                }

                if (!librarySetupProcessingObserved) {
                    return@collect
                }

                librarySetupCompletionDebounceJob?.cancel()
                librarySetupCompletionDebounceJob = scope.launch {
                    delay(5500)
                    if (!librarySetupNotificationArmed || isBackgroundProcessing.value) {
                        return@launch
                    }

                    val summary = context.getString(
                        R.string.notification_library_setup_complete_summary,
                        filteredSongsSize(),
                        filteredAlbumsSize(),
                        filteredArtistsSize()
                    )

                    showOperationResultNotification(
                        scope = scope,
                        notificationId = LIBRARY_SETUP_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_library_setup_title),
                        content = summary,
                        isError = false,
                        autoDismissMs = 5000L
                    )

                    disarmLibrarySetupCompletionNotification()
                }
            }
        }
    }
}
