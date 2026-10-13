/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.R
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.network.GitHubAsset
import chromahub.rhythm.app.network.GitHubRelease
import chromahub.rhythm.app.network.GitHubWorkflowRun
import chromahub.rhythm.app.network.NetworkManager
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.ChangelogFilter
import chromahub.rhythm.app.util.VersionComparator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * App version data model
 */
data class AppVersion(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val whatsNew: List<String>,
    val knownIssues: List<String>,
    val downloadUrl: String,
    val apkAssetName: String = "",
    val apkSize: Long = 0,
    val releaseNotes: String = "",
    val isPreRelease: Boolean = false,
    val buildNumber: Int = 0,
)

data class ReleaseContent(
    val whatsNew: List<String>,
    val knownIssues: List<String>,
)

/**
 * Download state for tracking download progress and resumption
 */
data class DownloadState(
    val fileName: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val etag: String?,
    val lastModified: String?,
    val resumePosition: Long,
    val checksum: String? = null, // SHA-256 checksum if available
    val retryCount: Int = 0, // Track retry attempts
)

private data class PendingMismatchedDownload(
    val filePath: String,
    val downloadUrl: String,
    val fileName: String,
    val retryAttempt: Int,
)

/**
 * ViewModel for handling app updates
 */
class AppUpdaterViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val tag = "AppUpdaterViewModel"
    private val updateDownloadChannelId = "update_download_progress"
    private val updateDownloadNotificationId = 1401
    private val updateDownloadCompletionAutoDismissMs = 7000L

    // GitHub repository information
    private val githubOwner = "tak2-08"
    private val githubRepo = "T2Rhythm"

    // Update check interval (6 hours)
    private val updateCheckInterval = TimeUnit.HOURS.toMillis(6)

    // API service
    private val gitHubApiService = NetworkManager.createGitHubApiService()

    // Last update check timestamp
    private var lastUpdateCheck = 0L

    // AppSettings instance
    private val _appSettings = AppSettings.getInstance(application.applicationContext)
    val appSettings: AppSettings = _appSettings // Expose AppSettings publicly

    // SharedPreferences for download state persistence
    private val downloadPrefs: SharedPreferences = application.getSharedPreferences("app_updater_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Active download state
    private var activeDownload: DownloadState? = null
    private var activeCall: Call? = null
    private var lastNotifiedProgressPercent: Int = -1
    private var completionNotificationDismissJob: kotlinx.coroutines.Job? = null

    // Mutex to prevent concurrent downloads
    private val downloadMutex = Mutex()

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Maximum retry attempts for downloads
    private val maxRetryAttempts = 2

    // Update channel (stable or beta)
    private val _updateChannel = MutableStateFlow("stable")
    val updateChannel: StateFlow<String> = _updateChannel.asStateFlow()

    // Current app version info
    private val _currentVersion =
        MutableStateFlow(
            AppVersion(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                releaseDate = BuildConfig.RELEASE_DATE,
                whatsNew = emptyList(),
                knownIssues = emptyList(),
                downloadUrl = "",
                isPreRelease = BuildConfig.VERSION_NAME.contains("Beta", ignoreCase = true),
                buildNumber = extractBuildNumber(BuildConfig.VERSION_NAME),
            ),
        )
    val currentVersion: StateFlow<AppVersion> = _currentVersion.asStateFlow()

    // Latest version info
    private val _latestVersion = MutableStateFlow<AppVersion?>(null)
    val latestVersion: StateFlow<AppVersion?> = _latestVersion.asStateFlow()

    // Update check state
    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    // Update available state
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    // Force update trigger for testing
    private val _forceUpdateTrigger = MutableStateFlow(0L)
    val forceUpdateTrigger: StateFlow<Long> = _forceUpdateTrigger.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _canProceedWithMismatchedDownload = MutableStateFlow(false)
    val canProceedWithMismatchedDownload: StateFlow<Boolean> = _canProceedWithMismatchedDownload.asStateFlow()

    private val _isVersionCodeDowngrade = MutableStateFlow(false)
    val isVersionCodeDowngrade: StateFlow<Boolean> = _isVersionCodeDowngrade.asStateFlow()

    // Download state - true when actively downloading
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    // Download progress (0-100)
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    // Downloaded file
    private val _downloadedFile = MutableStateFlow<File?>(null)
    val downloadedFile: StateFlow<File?> = _downloadedFile.asStateFlow()

    // Download state for tracking download progress and resumption
    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState.asStateFlow()
    private var pendingMismatchedDownload: PendingMismatchedDownload? = null

    init {
        ensureDownloadNotificationChannel()

        // Load any persisted download state
        loadDownloadState()

        viewModelScope.launch {
            combine(_appSettings.updateChannel, _appSettings.updateSource) { channel, source ->
                channel to source
            }.distinctUntilChanged()
                .drop(1)
                .collectLatest { (channel, _) ->
                    _updateChannel.value = channel
                    // Re-check for updates if channel changes, but only if updates are enabled
                    if (_appSettings.updatesEnabled.first()) {
                        checkForUpdates(force = true)
                    }
                }
        }

        // Start periodic update checks
        startPeriodicUpdateChecks()
    }

    /**
     * Load download state from SharedPreferences
     */
    private fun loadDownloadState() {
        try {
            val downloadStateJson = downloadPrefs.getString("active_download", null)
            val downloadProgress = downloadPrefs.getFloat("download_progress", 0f)
            val isDownloading = downloadPrefs.getBoolean("is_downloading", false)
            val downloadedFilePath = downloadPrefs.getString("downloaded_file", null)

            if (downloadStateJson != null) {
                activeDownload = gson.fromJson(downloadStateJson, DownloadState::class.java)
                // Validate the download state
                if (validateDownloadState(activeDownload)) {
                    _downloadState.value = activeDownload
                    Log.d(tag, "Loaded download state: ${activeDownload?.fileName}")
                } else {
                    Log.w(tag, "Download state validation failed, clearing")
                    clearDownloadState()
                    return
                }
            }

            if (downloadProgress > 0f && downloadProgress <= 100f) {
                _downloadProgress.value = downloadProgress
                Log.d(tag, "Loaded download progress: $downloadProgress%")
            }

            if (downloadedFilePath != null) {
                val file = File(downloadedFilePath)
                if (file.exists() && file.length() > 0) {
                    // Verify file integrity if checksum available
                    val isValid =
                        activeDownload?.checksum?.let { checksum ->
                            verifyFileChecksum(file, checksum)
                        } ?: true

                    if (isValid) {
                        _downloadedFile.value = file
                        _downloadProgress.value = 100f
                        Log.d(tag, "Found completed download: ${file.absolutePath}")
                    } else {
                        Log.w(tag, "Downloaded file checksum mismatch, deleting")
                        file.delete()
                        clearDownloadState()
                    }
                } else {
                    Log.w(tag, "Downloaded file not found or empty, clearing state")
                    clearDownloadState()
                }
            }

            // Don't restore isDownloading state - always start fresh to avoid stuck downloads
            _isDownloading.value = false
        } catch (e: Exception) {
            Log.e(tag, "Failed to load download state", e)
            clearDownloadState()
        }
    }

    /**
     * Validate download state data
     */
    private fun validateDownloadState(state: DownloadState?): Boolean {
        if (state == null) return false
        return state.fileName.isNotBlank() &&
            state.url.isNotBlank() &&
            state.totalBytes >= 0 &&
            state.downloadedBytes >= 0 &&
            state.downloadedBytes <= state.totalBytes &&
            state.retryCount >= 0 &&
            state.retryCount < maxRetryAttempts
    }

    /**
     * Save download state to SharedPreferences
     */
    private fun saveDownloadState() {
        try {
            downloadPrefs.edit {
                if (activeDownload != null) {
                    putString("active_download", gson.toJson(activeDownload))
                } else {
                    remove("active_download")
                }

                putFloat("download_progress", _downloadProgress.value)
                putBoolean("is_downloading", _isDownloading.value)

                _downloadedFile.value?.let { file ->
                    putString("downloaded_file", file.absolutePath)
                } ?: remove("downloaded_file")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to save download state", e)
        }
    }

    /**
     * Clear persisted download state
     */
    private fun clearDownloadState() {
        downloadPrefs.edit { clear() }
        activeDownload = null
        pendingMismatchedDownload = null
        _canProceedWithMismatchedDownload.value = false
        _downloadState.value = null
    }

    /**
     * Check for updates by fetching the latest release from GitHub
     */
    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            val updatesEnabled = _appSettings.updatesEnabled.first()
            val autoCheckEnabled = _appSettings.autoCheckForUpdates.first()
            val currentChannel = _appSettings.updateChannel.first()

            // Master check: if updates are completely disabled, don't check at all
            if (!updatesEnabled) {
                Log.d(tag, "Skipping update check - updates are completely disabled.")
                _isCheckingForUpdates.value = false
                return@launch
            }

            // Auto-check setting: only applies to automatic checks, not forced checks
            if (!force && !autoCheckEnabled) {
                Log.d(tag, "Skipping update check - auto-check is disabled and not forced.")
                _isCheckingForUpdates.value = false
                return@launch
            }

            // Skip check if within update interval unless forced
            if (!force && System.currentTimeMillis() - lastUpdateCheck < updateCheckInterval) {
                Log.d(tag, "Skipping update check - within interval")
                return@launch
            }

            _isCheckingForUpdates.value = true
            _error.value = null
            _latestVersion.value = null // Clear any previous version data

            try {
                var candidate: AppVersion? = null

                if (currentChannel == "nightly") {
                    // 1. Fetch nightly workflow runs
                    var nightlyCandidate: AppVersion? = null
                    val runsResponse = gitHubApiService.getWorkflowRuns(githubOwner, githubRepo, "nightly.yml")
                    if (runsResponse.isSuccessful) {
                        val latestRun = runsResponse.body()?.workflow_runs?.firstOrNull {
                            it.status == "completed" && it.conclusion == "success"
                        }
                        if (latestRun != null) {
                            var apkSize: Long = 0
                            try {
                                val artifactsResponse = gitHubApiService.getRunArtifacts(githubOwner, githubRepo, latestRun.id)
                                if (artifactsResponse.isSuccessful) {
                                    apkSize = artifactsResponse.body()?.artifacts?.firstOrNull {
                                        it.name == "Rhythm-Nightly-Artifacts"
                                    }?.size_in_bytes ?: 0
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error fetching nightly artifact size", e)
                            }
                            nightlyCandidate = convertWorkflowRunToAppVersion(latestRun, apkSize)
                        }
                    }

                    // 2. Also fetch releases (to compare against any equal/higher Stable or Beta releases)
                    var releaseCandidate: AppVersion? = null
                    val releasesResponse = gitHubApiService.getReleases(githubOwner, githubRepo)
                    if (releasesResponse.isSuccessful) {
                        val allReleases = releasesResponse.body()
                        if (!allReleases.isNullOrEmpty()) {
                            val bestRelease = findLatestSuitableRelease(allReleases, "beta")
                            if (bestRelease != null) {
                                releaseCandidate = convertReleaseToAppVersion(bestRelease)
                            }
                        }
                    }

                    // 3. Rank across all tiers using VersionComparator (Higher Version Number >> Stable > Beta > Nightly)
                    val candidates = listOfNotNull(nightlyCandidate, releaseCandidate)
                    candidate = candidates.maxWithOrNull { c1, c2 ->
                        VersionComparator.compare(
                            c1.versionName,
                            c2.versionName,
                            isPreRelease1 = c1.isPreRelease,
                            isPreRelease2 = c2.isPreRelease,
                        )
                    }

                    if (candidate == null) {
                        _error.value = "No builds or releases found on GitHub"
                        _isCheckingForUpdates.value = false
                        return@launch
                    }
                } else {
                    val releasesResponse = gitHubApiService.getReleases(githubOwner, githubRepo)
                    if (releasesResponse.isSuccessful) {
                        val allReleases = releasesResponse.body()
                        if (allReleases.isNullOrEmpty()) {
                            _error.value = "No releases found on GitHub"
                            _isCheckingForUpdates.value = false
                            return@launch
                        }

                        val latestSuitableRelease = findLatestSuitableRelease(allReleases, currentChannel)
                        if (latestSuitableRelease == null) {
                            _error.value = "No suitable release found for channel '$currentChannel'"
                            _isCheckingForUpdates.value = false
                            return@launch
                        }

                        candidate = convertReleaseToAppVersion(latestSuitableRelease)
                    } else {
                        handleApiError(releasesResponse.code(), releasesResponse.message())
                        return@launch
                    }
                }

                processCandidate(candidate)
            } catch (e: Exception) {
                Log.e(tag, "Error checking for updates", e)
                _error.value = "Network error: ${e.message ?: "Unknown error"}"
            } finally {
                _isCheckingForUpdates.value = false
                lastUpdateCheck = System.currentTimeMillis()
            }
        }
    }

    private fun processCandidate(candidate: AppVersion) {
        _latestVersion.value = candidate
        val isNewer = VersionComparator.isNewer(
            candidate = candidate.versionName,
            current = _currentVersion.value.versionName,
            isCandidatePreRelease = candidate.isPreRelease,
            isCurrentPreRelease = _currentVersion.value.isPreRelease,
        )
        val hasVersionCodeDowngrade = candidate.versionCode > 0 && candidate.versionCode < BuildConfig.VERSION_CODE
        _isVersionCodeDowngrade.value = hasVersionCodeDowngrade

        Log.d(
            tag,
            "Version comparison: current=${_currentVersion.value.versionName} (code=${BuildConfig.VERSION_CODE}) vs latest=${candidate.versionName} (code=${candidate.versionCode}) -> isNewer=$isNewer, isDowngrade=$hasVersionCodeDowngrade"
        )
        _updateAvailable.value = isNewer
    }

    private fun extractBuildNumber(
        versionString: String,
        versionParts: List<String>? = null,
    ): Int {
        val cleaned = versionString.trim().removePrefix("v").removePrefix("V")
        val buildRegex = Regex("(?:b|build)-(\\d+)", RegexOption.IGNORE_CASE)
        val explicitBuildNumber =
            buildRegex
                .find(cleaned)
                ?.groupValues
                ?.get(1)
                ?.filter { it.isDigit() }
                ?.toIntOrNull()
        if (explicitBuildNumber != null) {
            return explicitBuildNumber
        }

        val parts =
            versionParts ?: cleaned
                .split(" ")[0]
                .split("-")[0]
                .split("_")[0]
                .split(".")
        return parts.getOrNull(3)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    }

    private fun calculateVersionCode(versionString: String): Int {
        val cleaned = versionString.trim().removePrefix("v").removePrefix("V")
        val versionBase = cleaned.split(" ")[0].split("-")[0].split("_")[0]
        val versionParts = versionBase.split(".")
        val codeString =
            buildString {
                append(versionParts.getOrNull(0)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: "0")
                append(versionParts.getOrNull(1)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: "0")
                append(versionParts.getOrNull(2)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: "0")
                val buildPart = versionParts.getOrNull(3)?.filter { it.isDigit() }
                if (!buildPart.isNullOrEmpty()) {
                    append(buildPart)
                } else {
                    append(extractBuildNumber(cleaned, versionParts).toString())
                }
            }

        return codeString.toIntOrNull() ?: 0
    }

    /**
     * Handle API errors with specific messages based on status code
     */
    private fun handleApiError(
        code: Int,
        message: String,
    ) {
        _error.value =
            when (code) {
                403 -> "GitHub API rate limit exceeded. Please try again later."
                404 -> "No releases found on GitHub."
                500, 502, 503, 504 -> "GitHub server error. Please try again later."
                else -> "GitHub API error: $code - $message"
            }
        _isCheckingForUpdates.value = false
    }

    /**
     * Find the latest suitable release based on the update channel.
     * "stable" channel: latest non-prerelease, non-draft release
     * "beta" channel: latest release (including pre-releases) that is not a draft
     * Ranks candidates using VersionComparator (Higher Version Number >> Stable > Beta).
     */
    private fun findLatestSuitableRelease(
        releases: List<GitHubRelease>,
        channel: String,
    ): GitHubRelease? {
        val filteredReleases =
            when (channel) {
                "stable" -> releases.filter { !it.draft && !it.prerelease }
                "beta" -> releases.filter { !it.draft } // Include all non-draft releases (stable + pre-release)
                else -> {
                    Log.w(tag, "Unknown channel: $channel, defaulting to stable")
                    releases.filter { !it.draft && !it.prerelease }
                }
            }

        return filteredReleases.maxWithOrNull { r1, r2 ->
            val v1 = r1.name.ifEmpty { r1.tag_name }
            val v2 = r2.name.ifEmpty { r2.tag_name }
            VersionComparator.compare(v1, v2, isPreRelease1 = r1.prerelease, isPreRelease2 = r2.prerelease)
        }
    }

    /**
     * Convert a GitHub release to an AppVersion object
     */
    private fun convertReleaseToAppVersion(release: GitHubRelease): AppVersion {
        val semanticVersion = VersionComparator.parse(release.tag_name, isPreRelease = release.prerelease)

        // Format the release date
        val releaseDateString =
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = inputFormat.parse(release.published_at)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                "Unknown date"
            }

        // Parse changelog from release body
        val releaseContent = parseReleaseBody(release.body)
        Log.d(tag, "Parsed whatsNew: ${releaseContent.whatsNew}")
        Log.d(tag, "Parsed knownIssues: ${releaseContent.knownIssues}")

        val apkAsset = selectReleaseApkAsset(release)
        val downloadUrl = apkAsset?.browser_download_url ?: release.html_url
        val apkSize = apkAsset?.size ?: 0
        val versionName = release.name.ifEmpty { release.tag_name }

        return AppVersion(
            versionName = versionName,
            versionCode = calculateVersionCode(versionName),
            releaseDate = releaseDateString,
            whatsNew = releaseContent.whatsNew,
            knownIssues = releaseContent.knownIssues,
            downloadUrl = downloadUrl,
            apkAssetName = apkAsset?.name ?: "",
            apkSize = apkSize,
            releaseNotes = release.body,
            isPreRelease = release.prerelease,
            buildNumber = semanticVersion.buildNumber,
        )
    }

    private fun extractNightlyRunNumber(versionString: String): Int {
        val regex = Regex("nightly-r(\\d+)", RegexOption.IGNORE_CASE)
        return regex
            .find(versionString)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull() ?: 0
    }

    private fun convertWorkflowRunToAppVersion(
        run: GitHubWorkflowRun,
        apkSize: Long = 0,
    ): AppVersion {
        val releaseDateString =
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = inputFormat.parse(run.updated_at)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                "Unknown date"
            }

        val shortSha = run.head_sha.take(7)
        val cleanedBaseName =
            BuildConfig.VERSION_NAME
                .replace(" Beta", "")
                .replace(Regex("-nightly-r\\d+-[0-9a-f]+", RegexOption.IGNORE_CASE), "")
        val versionName = "$cleanedBaseName-nightly-r${run.run_number}-$shortSha"

        val downloadUrl = "https://nightly.link/tak2-08/T2Rhythm/workflows/nightly.yml/main/Rhythm-Nightly-Artifacts.zip"

        val commitMessage = run.head_commit?.message ?: "New features and performance updates"
        val changelogItems = ChangelogFilter.filterLines(commitMessage.lines())

        return AppVersion(
            versionName = versionName,
            versionCode = run.run_number,
            releaseDate = releaseDateString,
            whatsNew = changelogItems,
            knownIssues = emptyList(),
            downloadUrl = downloadUrl,
            apkAssetName = "Rhythm-Nightly-Artifacts.zip",
            apkSize = apkSize,
            releaseNotes = commitMessage,
            isPreRelease = true,
            buildNumber = run.run_number,
        )
    }

    /**
     * Parses the release body string to extract "What's New" and "Known Issues" sections.
     * Assumes a Markdown-like format with specific headings.
     */
    private enum class ParsingState {
        NONE,
        WHATS_NEW,
        KNOWN_ISSUES,
    }

    private fun parseReleaseBody(body: String?): ReleaseContent {
        if (body.isNullOrBlank()) {
            return ReleaseContent(emptyList(), emptyList())
        }

        val whatsNew = mutableListOf<String>()
        val knownIssues = mutableListOf<String>()

        var currentState = ParsingState.NONE

        body.lines().forEach { line ->
            val trimmedLine = line.trim()

            when {
                trimmedLine.startsWith("**What's New:**") -> {
                    currentState = ParsingState.WHATS_NEW
                }
                trimmedLine.startsWith("**Known Issues") -> { // Matches "Known Issues (Will be fixed on a later build):"
                    currentState = ParsingState.KNOWN_ISSUES
                }
                trimmedLine.startsWith("**Build Information:**") -> {
                    currentState = ParsingState.NONE // Stop parsing for these sections
                }
                // If we are in a section and encounter another heading, stop parsing the current section
                (trimmedLine.startsWith("#") || trimmedLine.startsWith("##")) &&
                    currentState != ParsingState.NONE -> {
                    currentState = ParsingState.NONE
                }
                else -> {
                    // Add line to current section if we are in one
                    when (currentState) {
                        ParsingState.WHATS_NEW -> {
                            // Defense-in-depth: the release body is generated by the
                            // junk-filtered release-notes script, but drop any stray
                            // low-value entry that slips through.
                            if (ChangelogFilter.isJunkReleaseBullet(trimmedLine)) {
                                return@forEach
                            }
                            val htmlLine =
                                trimmedLine
                                    .replace(Regex("^[*-]\\s*"), "") // Remove list prefixes
                                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>") // Bold
                                    .replace(Regex("_(.*?)_"), "<i>$1</i>") // Italic
                                    .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\">$1</a>") // Links
                            if (htmlLine.isNotBlank()) {
                                whatsNew.add(htmlLine)
                            }
                        }
                        ParsingState.KNOWN_ISSUES -> {
                            val htmlLine =
                                trimmedLine
                                    .replace(Regex("^[*-]\\s*"), "") // Remove list prefixes
                                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>") // Bold
                                    .replace(Regex("_(.*?)_"), "<i>$1</i>") // Italic
                                    .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\">$1</a>") // Links
                            if (htmlLine.isNotBlank()) {
                                knownIssues.add(htmlLine)
                            }
                        }
                        ParsingState.NONE -> {
                            // Do nothing if not in a specific section
                        }
                    }
                }
            }
        }
        return ReleaseContent(whatsNew, knownIssues)
    }

    /**
     * Clear any error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Force update available state for testing/debugging purposes
     * This is useful for testing update UI without waiting for actual updates
     */
    fun forceUpdateAvailable(available: Boolean) {
        _updateAvailable.value = available
        _forceUpdateTrigger.value = System.currentTimeMillis() // Trigger change
        if (available) {
            // Create a mock latest version for testing
            _latestVersion.value =
                AppVersion(
                    versionName = "2.0.0",
                    versionCode = 200,
                    releaseDate = System.currentTimeMillis().toString(),
                    whatsNew =
                        listOf(
                            "New update system with bottom sheet",
                            "Improved UI and performance",
                            "Bug fixes and optimizations",
                        ),
                    knownIssues = emptyList(),
                    downloadUrl = "https://github.com/tak2-08/T2Rhythm/releases",
                    apkAssetName = "rhythm-release.apk",
                    apkSize = 0,
                    releaseNotes = "Test update",
                    isPreRelease = false,
                    buildNumber = 200,
                )
        } else {
            _latestVersion.value = null
        }
    }

    /**
     * Calculate readable file size
     */
    fun getReadableFileSize(size: Long): String {
        if (size <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()

        return String.format(
            Locale.ROOT,
            "%.1f %s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups],
        )
    }

    /**
     * Download the update by opening the browser to the download URL or starting in-app download
     */
    fun downloadUpdate() {
        viewModelScope.launch {
            val updatesEnabled = _appSettings.updatesEnabled.first()

            if (!updatesEnabled) {
                _error.value = "Updates are disabled in settings"
                return@launch
            }

            val latestVersion =
                _latestVersion.value ?: run {
                    _error.value = "No update information available"
                    return@launch
                }
            val downloadUrl = latestVersion.downloadUrl

            if (downloadUrl.isBlank()) {
                _error.value = "No download URL available"
                return@launch
            }

            // Clear any previous errors
            _error.value = null

            // If it's not an APK file, open in browser
            if (latestVersion.apkAssetName.isNullOrEmpty()) {
                openInBrowser(downloadUrl)
                return@launch
            }

            // Check if we have an active download
            if (_isDownloading.value) {
                Log.d(tag, "Download already in progress")
                return@launch
            }

            // Start or resume download
            downloadApkInApp(downloadUrl, latestVersion.apkAssetName, expectedSize = latestVersion.apkSize)
        }
    }

    /**
     * Open a URL in the browser
     */
    private fun openInBrowser(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e(tag, "Error opening download URL", e)
            _error.value = "Could not open download link: ${e.message ?: "Unknown error"}"
        }
    }

    /**
     * Download an APK file in-app with progress tracking and resume support
     * @param expectedSize The expected file size from GitHub API (0 if unknown)
     */
    private fun downloadApkInApp(
        downloadUrl: String,
        fileName: String,
        expectedSize: Long = 0,
        retryAttempt: Int = 0,
    ) {
        // Use mutex to prevent concurrent downloads
        viewModelScope.launch {
            if (!downloadMutex.tryLock()) {
                Log.w(tag, "Download already in progress")
                return@launch
            }

            try {
                downloadApkInAppInternal(downloadUrl, fileName, expectedSize, retryAttempt)
            } finally {
                downloadMutex.unlock()
            }
        }
    }

    /**
     * Internal download implementation with mutex protection
     * @param expectedSize The expected file size from GitHub API (0 if unknown)
     */
    private fun downloadApkInAppInternal(
        downloadUrl: String,
        fileName: String,
        expectedSize: Long = 0,
        retryAttempt: Int = 0,
    ) {
        if (_isDownloading.value) {
            return // Already downloading
        }

        // Cancel any stale notification from a previous session
        cancelDownloadNotification()

        _downloadProgress.value = 0f
        _error.value = null

        val shouldResumeDownload =
            activeDownload != null &&
                activeDownload?.url == downloadUrl

        if (!shouldResumeDownload) {
            // Starting fresh download - clear previous state and delete partial files
            _downloadedFile.value = null

            // Delete any existing partial download file
            val context = getApplication<Application>()
            val downloadDir =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
            downloadDir?.let { dir ->
                val existingFile = File(dir, fileName)
                if (existingFile.exists()) {
                    Log.d(tag, "Deleting partial download file: ${existingFile.absolutePath}")
                    existingFile.delete()
                }
            }

            activeDownload = null
        }

        _isDownloading.value = true
        lastNotifiedProgressPercent = -1
        showDownloadProgressNotification(0)

        // Use viewModelScope with IO dispatcher for background work
        // The download continues in background even if user navigates away
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use app-specific external storage instead of public Downloads
                val context = getApplication<Application>()
                val downloadDir =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // For Android 10 and above, use app-specific directory
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    } else {
                        // For older versions, check permission first
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModelScope.launch {
                                _error.value = "Storage permission required to download updates"
                                _isDownloading.value = false
                            }
                            return@launch
                        }
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    }

                if (downloadDir == null) {
                    viewModelScope.launch {
                        _error.value = "Could not access storage"
                        _isDownloading.value = false
                    }
                    return@launch
                }

                // Ensure download directory exists
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                // Create or get existing file
                val file = File(downloadDir, fileName)
                var existingLength = 0L

                // Validate existing file before resume
                if (file.exists() && activeDownload != null && shouldResumeDownload) {
                    existingLength = file.length()

                    // Validate file size matches expected resume position
                    if (existingLength != activeDownload?.resumePosition) {
                        Log.w(tag, "File size mismatch (expected: ${activeDownload?.resumePosition}, actual: $existingLength), deleting")
                        file.delete()
                        existingLength = 0L
                        activeDownload = null
                    }
                } else if (file.exists()) {
                    // File exists but we're not resuming - delete it
                    Log.d(tag, "Deleting existing file for fresh download")
                    file.delete()
                }

                // Create OkHttp client with longer timeouts
                val client =
                    OkHttpClient
                        .Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()

                // Create request with resume support
                val requestBuilder =
                    Request
                        .Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "Rhythm-App/${BuildConfig.VERSION_NAME} (Android)")

                // Add range header if resuming
                if (existingLength > 0 && activeDownload != null) {
                    Log.d(tag, "Resuming download from byte $existingLength")
                    requestBuilder.header("Range", "bytes=$existingLength-")
                    activeDownload?.etag?.let { requestBuilder.header("If-Match", it) }
                    activeDownload?.lastModified?.let { requestBuilder.header("If-Unmodified-Since", it) }
                }

                val request = requestBuilder.build()

                // Execute request
                activeCall = client.newCall(request)
                activeCall?.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            viewModelScope.launch {
                                if (!call.isCanceled()) {
                                    Log.e(tag, "Download failed", e)
                                    handleDownloadFailure(downloadUrl, fileName, retryAttempt, e.message ?: "Unknown error")
                                }
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            // Handle HTTP 412 Precondition Failed - file changed on server
                            if (response.code == 412) {
                                Log.w(tag, "Server file changed (HTTP 412), restarting download")
                                viewModelScope.launch {
                                    _isDownloading.value = false
                                    activeDownload = null
                                    activeCall = null
                                    // Delete partial file and restart
                                    file.delete()
                                    handleDownloadFailure(downloadUrl, fileName, retryAttempt, "File changed on server", forceRetry = true)
                                }
                                return
                            }

                            if (!response.isSuccessful && response.code != 206) {
                                viewModelScope.launch {
                                    handleDownloadFailure(
                                        downloadUrl,
                                        fileName,
                                        retryAttempt,
                                        "HTTP ${response.code} - ${response.message}",
                                    )
                                }
                                return
                            }

                            try {
                                // Get content length and resume info
                                val contentLength = response.body.contentLength()
                                val totalLength =
                                    if (response.code == 206) {
                                        val range = response.header("Content-Range")
                                        range?.substringAfter("/")?.toLongOrNull() ?: 0L
                                    } else {
                                        contentLength
                                    }

                                var resumePosition = existingLength
                                if (resumePosition > 0 && response.code != 206) {
                                    Log.w(tag, "Server ignored range request with HTTP ${response.code}; restarting download from scratch")
                                    resumePosition = 0L
                                    file.delete()
                                }

                                // Store download state
                                val checksumHeader = response.header("X-Checksum-SHA256") ?: response.header("Digest")
                                activeDownload =
                                    DownloadState(
                                        fileName = fileName,
                                        url = downloadUrl,
                                        totalBytes = totalLength,
                                        downloadedBytes = resumePosition,
                                        etag = response.header("ETag"),
                                        lastModified = response.header("Last-Modified"),
                                        resumePosition = resumePosition,
                                        checksum = checksumHeader,
                                        retryCount = retryAttempt,
                                    )
                                viewModelScope.launch {
                                    _downloadState.value = activeDownload
                                }

                                // Create output stream
                                val outputStream = FileOutputStream(file, resumePosition > 0)

                                // Get input stream
                                val inputStream = response.body.byteStream()

                                // Create buffer
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytesRead = resumePosition

                                // Read input stream
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    if (!_isDownloading.value) {
                                        // Download was cancelled
                                        break
                                    }

                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead

                                    // Update progress
                                    val totalBytes =
                                        if (totalLength > 0) {
                                            totalLength
                                        } else if (response.code == 206 && resumePosition > 0) {
                                            resumePosition + contentLength.coerceAtLeast(0)
                                        } else {
                                            contentLength
                                        }
                                    if (totalBytes > 0) {
                                        val progress = (totalBytesRead.toFloat() / totalBytes.toFloat()) * 100f
                                        viewModelScope.launch {
                                            _downloadProgress.value = progress.coerceIn(0f, 100f)
                                            maybeUpdateDownloadProgressNotification(_downloadProgress.value)
                                            activeDownload = activeDownload?.copy(downloadedBytes = totalBytesRead)
                                            _downloadState.value = activeDownload

                                            // Save state periodically (every 5% progress)
                                            if (progress % 5f < 1f) {
                                                saveDownloadState()
                                            }
                                        }
                                    }
                                }

                                // Close streams
                                outputStream.flush()
                                outputStream.close()
                                inputStream.close()

                                if (!_isDownloading.value) {
                                    Log.d(tag, "Download cancelled/paused by user. Keeping partial file.")
                                    return
                                }

                                // Verify file integrity
                                val fileSize = file.length()
                                // Prefer HTTP headers (Content-Range/Content-Length) which reflect the actual file
                                // being downloaded. Fall back to GitHub API's expectedSize only if HTTP headers
                                // are unavailable (e.g. chunked transfer with no Content-Length).
                                val httpExpectedSize = if (totalLength > 0) totalLength else contentLength
                                val finalExpectedSize = if (httpExpectedSize > 0) httpExpectedSize else expectedSize

                                if (finalExpectedSize == 0L) {
                                    Log.w(
                                        tag,
                                        "No reference size available (HTTP Content-Length unavailable and GitHub API returned 0) — skipping size verification for $fileName ($fileSize bytes)",
                                    )
                                } else if (fileSize != finalExpectedSize) {
                                    Log.w(
                                        tag,
                                        "File size mismatch (expected: $finalExpectedSize [HTTP: $httpExpectedSize, GitHub: $expectedSize], actual: $fileSize) — proceeding anyway; checksum will verify integrity",
                                    )
                                } else {
                                    Log.d(tag, "File size verification passed: $fileSize bytes (expected: $finalExpectedSize)")
                                }

                                // Verify checksum if available
                                val checksumValid =
                                    activeDownload?.checksum?.let { expectedChecksum ->
                                        val actualChecksum = calculateFileChecksum(file)
                                        val isValid = verifyChecksum(actualChecksum, expectedChecksum)
                                        if (!isValid) {
                                            Log.e(tag, "Checksum verification failed. Expected: $expectedChecksum, Actual: $actualChecksum")
                                        }
                                        isValid
                                    } ?: true

                                if (!checksumValid) {
                                    viewModelScope.launch {
                                        file.delete() // Delete corrupted file
                                        handleDownloadFailure(downloadUrl, fileName, retryAttempt, "Checksum verification failed")
                                    }
                                    return
                                }

                                // Download complete and verified
                                viewModelScope.launch(Dispatchers.IO) {
                                    if (_isDownloading.value) {
                                        val isZip = fileName.endsWith(".zip", ignoreCase = true)
                                        if (isZip) {
                                            _isExtracting.value = true
                                        }
                                        val finalFile =
                                            if (isZip) {
                                                val apkFile = File(file.parentFile, fileName.replace(".zip", ".apk", ignoreCase = true))
                                                if (extractApkFromZip(file, apkFile)) {
                                                    file.delete() // delete the zip file
                                                    _isExtracting.value = false
                                                    apkFile
                                                } else {
                                                    _isExtracting.value = false
                                                    _isDownloading.value = false
                                                    _downloadProgress.value = 0f
                                                    activeDownload = null
                                                    _downloadState.value = null
                                                    clearDownloadState()
                                                    cancelDownloadNotification()
                                                    withContext(Dispatchers.Main) {
                                                        _error.value =
                                                            "Failed to extract APK from ZIP. The ZIP file is still saved — you can extract it manually or try downloading again."
                                                    }
                                                    return@launch
                                                }
                                            } else {
                                                file
                                            }

                                        _isDownloading.value = false
                                        _downloadProgress.value = 100f
                                        _downloadedFile.value = finalFile
                                        showDownloadCompletedNotification(finalFile.name)

                                        // Calculate and store final checksum
                                        val finalChecksum = calculateFileChecksum(finalFile)
                                        activeDownload = activeDownload?.copy(checksum = finalChecksum)
                                        saveDownloadState() // Save final state with checksum
                                        // Clear active download but keep downloaded file info
                                        activeDownload = null
                                        activeCall = null
                                        _downloadState.value = null
                                        Log.d(
                                            tag,
                                            "Download complete: ${finalFile.absolutePath} (${finalFile.length()} bytes, checksum: $finalChecksum)",
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                viewModelScope.launch {
                                    if (_isDownloading.value) {
                                        Log.e(tag, "Download failed during write", e)
                                        handleDownloadFailure(downloadUrl, fileName, retryAttempt, e.message ?: "Unknown error")
                                    }
                                }
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                Log.e(tag, "Download setup failed", e)
                _isDownloading.value = false
                _error.value = "Download failed: ${e.message ?: "Unknown error"}"
                activeDownload = null
                activeCall = null
                _downloadState.value = null
                cancelDownloadNotification()
            }
        }
    }

    /**
     * Handle download failures with retry logic
     */
    private fun handleSizeMismatch(
        downloadUrl: String,
        fileName: String,
        retryAttempt: Int,
        file: File,
        expectedSize: Long,
        actualSize: Long,
        isHttpSizeAbsent: Boolean = false,
    ) {
        Log.w(
            tag,
            "File size mismatch (expected: $expectedSize, actual: $actualSize, httpSizeAbsent: $isHttpSizeAbsent) — proceeding with download anyway",
        )

        _isDownloading.value = false
        activeCall = null
        activeDownload = null
        _downloadState.value = null
        clearDownloadState()
        cancelDownloadNotification()

        viewModelScope.launch(Dispatchers.IO) {
            finishDownload(file, fileName)
        }
    }

    private fun finishDownload(
        file: File,
        fileName: String,
    ) {
        val isZip = fileName.endsWith(".zip", ignoreCase = true)
        if (isZip) {
            _isExtracting.value = true
        }
        val finalFile =
            if (isZip) {
                val apkFile = File(file.parentFile, fileName.replace(".zip", ".apk", ignoreCase = true))
                if (extractApkFromZip(file, apkFile)) {
                    file.delete()
                    _isExtracting.value = false
                    apkFile
                } else {
                    _isExtracting.value = false
                    _isDownloading.value = false
                    _downloadProgress.value = 0f
                    clearDownloadState()
                    cancelDownloadNotification()
                    _error.value = "Failed to extract APK from ZIP. Opening GitHub releases instead."
                    openInBrowser("https://github.com/$githubOwner/$githubRepo/releases")
                    return
                }
            } else {
                file
            }

        _isDownloading.value = false
        _downloadProgress.value = 100f
        _downloadedFile.value = finalFile
        showDownloadCompletedNotification(finalFile.name)

        val finalChecksum = calculateFileChecksum(finalFile)
        activeDownload = null
        activeCall = null
        _downloadState.value = null
        Log.d(
            tag,
            "Download complete (proceeded despite size mismatch): ${finalFile.absolutePath} (${finalFile.length()} bytes, checksum: $finalChecksum)",
        )
    }

    private fun handleDownloadFailure(
        downloadUrl: String,
        fileName: String,
        retryAttempt: Int,
        errorMessage: String,
        forceRetry: Boolean = false,
    ) {
        _isDownloading.value = false
        activeCall = null

        // Cancel old notification to avoid duplicate download notifications before retry
        cancelDownloadNotification()

        val nextRetryAttempt = retryAttempt + 1

        // Update activeDownload's resumePosition and retryCount so we can resume during retry
        val context = getApplication<Application>()
        val downloadDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
        val file = downloadDir?.let { File(it, fileName) }
        val currentSize = file?.length() ?: 0L

        if (activeDownload != null && activeDownload?.url == downloadUrl) {
            activeDownload =
                activeDownload?.copy(
                    resumePosition = currentSize,
                    downloadedBytes = currentSize,
                    retryCount = nextRetryAttempt,
                )
            saveDownloadState()
        }

        if (forceRetry || nextRetryAttempt < maxRetryAttempts) {
            val delayMs = if (forceRetry) 1000L else (1L shl retryAttempt) * 1000L

            Log.w(tag, "Download attempt ${retryAttempt + 1} failed: $errorMessage. Retrying in ${delayMs}ms...")
            _error.value = "Download failed: $errorMessage. Retrying ($nextRetryAttempt/$maxRetryAttempts)..."

            viewModelScope.launch {
                delay(delayMs)
                if (!_isDownloading.value) {
                    Log.d(tag, "Retrying download (attempt $nextRetryAttempt)")
                    val expectedSize = _latestVersion.value?.apkSize ?: 0
                    downloadApkInApp(downloadUrl, fileName, expectedSize, nextRetryAttempt)
                }
            }
        } else {
            Log.e(tag, "Download failed after $maxRetryAttempts attempts: $errorMessage")
            _error.value = "Download failed. Opening GitHub releases in browser..."
            activeDownload = null
            _downloadState.value = null
            clearDownloadState()
            cancelDownloadNotification()
            openInBrowser("https://github.com/$githubOwner/$githubRepo/releases")
        }
    }

    fun proceedWithMismatchedDownload() {
        val pending = pendingMismatchedDownload
        if (pending == null) {
            _error.value = "No mismatched download is available to proceed with."
            return
        }

        val file = File(pending.filePath)
        if (!file.exists() || file.length() == 0L) {
            cleanupUpdaterDownloadAfterFailure("Downloaded update file is missing or empty.", file)
            return
        }

        _error.value = null
        _isDownloading.value = true
        _downloadProgress.value = 100f
        _canProceedWithMismatchedDownload.value = false

        // Track proceed attempts to avoid infinite loops on persistent extraction failures
        val maxProceedAttempts = 3
        val proceedAttempt = activeDownload?.retryCount?.coerceAtMost(maxProceedAttempts) ?: 0

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalFile =
                    if (pending.fileName.endsWith(".zip", ignoreCase = true)) {
                        _isExtracting.value = true
                        val apkFile = File(file.parentFile, pending.fileName.replace(".zip", ".apk", ignoreCase = true))
                        if (extractApkFromZip(file, apkFile)) {
                            file.delete()
                            _isExtracting.value = false
                            apkFile
                        } else {
                            _isExtracting.value = false
                            _isDownloading.value = false
                            if (proceedAttempt >= maxProceedAttempts) {
                                _error.value =
                                    "Could not extract an APK from the downloaded ZIP after $maxProceedAttempts attempts. The ZIP file has been kept at: ${file.absolutePath}. Reset to download again."
                                pendingMismatchedDownload = null
                                clearDownloadState()
                                cancelDownloadNotification()
                            } else {
                                activeDownload = activeDownload?.copy(retryCount = proceedAttempt + 1)
                                _canProceedWithMismatchedDownload.value = true
                                _error.value =
                                    "Could not extract an APK from the downloaded ZIP. The ZIP file is still saved — you can try again or reset."
                            }
                            return@launch
                        }
                    } else {
                        file
                    }

                _isDownloading.value = false
                _downloadProgress.value = 100f
                _downloadedFile.value = finalFile
                pendingMismatchedDownload = null
                activeDownload = null
                activeCall = null
                _downloadState.value = null
                _canProceedWithMismatchedDownload.value = false
                saveDownloadState()
                showDownloadCompletedNotification(finalFile.name)
                Log.w(tag, "Proceeding with size-mismatched download: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(tag, "Proceeding with mismatched download failed", e)
                _isDownloading.value = false
                if (proceedAttempt >= maxProceedAttempts) {
                    _error.value =
                        "Could not use the downloaded update after $maxProceedAttempts attempts. The file has been kept at: ${file.absolutePath}. Reset to download again."
                    pendingMismatchedDownload = null
                    clearDownloadState()
                    cancelDownloadNotification()
                } else {
                    activeDownload = activeDownload?.copy(retryCount = proceedAttempt + 1)
                    _canProceedWithMismatchedDownload.value = true
                    _error.value =
                        "Could not use the downloaded update: ${e.message ?: "Unknown error"}. The file is still saved — you can try again or reset."
                }
            }
        }
    }

    private fun cleanupUpdaterDownloadAfterFailure(
        message: String,
        fileToDelete: File? = null,
    ) {
        val context = getApplication<Application>()
        val downloadDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
        val activeFile =
            activeDownload?.fileName?.let { fileName ->
                downloadDir?.let { File(it, fileName) }
            }
        val pendingFile = pendingMismatchedDownload?.filePath?.let { File(it) }
        val downloadedFile = _downloadedFile.value

        listOfNotNull(fileToDelete, activeFile, pendingFile, downloadedFile)
            .distinctBy { it.absolutePath }
            .forEach { file ->
                runCatching {
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(tag, "Deleted updater file after failure: ${file.absolutePath}, success: $deleted")
                    }
                }.onFailure { e ->
                    Log.e(tag, "Failed to delete updater file: ${file.absolutePath}", e)
                }
            }

        resetDownloadState()
        _error.value = "$message Download state was reset. Please try again."
    }

    /**
     * Calculate SHA-256 checksum of a file
     */
    private fun calculateFileChecksum(file: File): String =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int

            FileInputStream(file).use { fis ->
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error calculating checksum", e)
            ""
        }

    /**
     * Verify file checksum matches expected value
     */
    private fun verifyFileChecksum(
        file: File,
        expectedChecksum: String,
    ): Boolean {
        if (expectedChecksum.isBlank()) return true
        val actualChecksum = calculateFileChecksum(file)
        return verifyChecksum(actualChecksum, expectedChecksum)
    }

    /**
     * Verify checksum with various format support
     */
    private fun verifyChecksum(
        actual: String,
        expected: String,
    ): Boolean {
        if (actual.isBlank() || expected.isBlank()) return true

        // Handle different checksum formats (sha-256=xxx, sha256:xxx, etc.)
        val cleanExpected =
            expected
                .substringAfter("sha-256=", "")
                .substringAfter("sha256:", "")
                .substringAfter("SHA-256=", "")
                .substringAfter("SHA256:", "")
                .ifBlank { expected }
                .lowercase()
                .trim()

        return actual.lowercase() == cleanExpected
    }

    /**
     * Select the APK asset for the currently installed flavor.
     * Prefers the flavor-specific universal APK, then any flavor-matching APK.
     */
    private fun extractApkFromZip(
        zipFile: File,
        targetApkFile: File,
    ): Boolean =
        try {
            val flavor = resolveUpdateSourceFlavor().lowercase(Locale.ROOT)
            var extracted = false
            java.util.zip.ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val lowerName = entry.name.lowercase(Locale.ROOT)
                        val matchFlavor =
                            when (flavor) {
                                "fdroid" -> lowerName.contains("fdroidrelease") || lowerName.contains("-fdroid-")
                                "github" -> lowerName.contains("githubrelease") || lowerName.contains("-github-")
                                else -> true
                            }
                        if (matchFlavor && (isUniversalApkName(entry.name) || !hasAbiSuffix(entry.name))) {
                            FileOutputStream(targetApkFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            extracted = true
                            break
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            extracted
        } catch (e: Exception) {
            Log.e(tag, "Error extracting ZIP file", e)
            false
        }

    private fun selectReleaseApkAsset(release: GitHubRelease): GitHubAsset? {
        val flavor = resolveUpdateSourceFlavor().lowercase(Locale.ROOT)

        val uploadedApks =
            release.assets.filter { asset ->
                asset.state == "uploaded" && asset.name.endsWith(".apk", ignoreCase = true)
            }

        val flavorAssets =
            uploadedApks.filter { asset ->
                val lowerName = asset.name.lowercase(Locale.ROOT)
                when (flavor) {
                    "fdroid" -> lowerName.contains("fdroidrelease") || lowerName.contains("-fdroid-")
                    "github" -> lowerName.contains("githubrelease") || lowerName.contains("-github-")
                    else -> false
                }
            }

        if (flavorAssets.isEmpty()) {
            Log.w(tag, "No APK asset matched current flavor '$flavor' for release ${release.tag_name}")
            return null
        }

        return flavorAssets.firstOrNull { asset -> isUniversalApkName(asset.name) }
            ?: flavorAssets.firstOrNull { asset -> !hasAbiSuffix(asset.name) }
            ?: flavorAssets.firstOrNull()
    }

    private fun resolveUpdateSourceFlavor(): String =
        when (_appSettings.updateSource.value.lowercase(Locale.ROOT)) {
            "installed" -> BuildConfig.FLAVOR
            "github" -> "github"
            "fdroid" -> "fdroid"
            else -> BuildConfig.FLAVOR
        }

    private fun isUniversalApkName(name: String): Boolean = name.contains("universal", ignoreCase = true) || !hasAbiSuffix(name)

    private fun hasAbiSuffix(name: String): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        return listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").any { lowerName.contains(it) }
    }

    /**
     * Install the downloaded APK with improved error handling
     */
    fun installDownloadedApk() {
        val file = _downloadedFile.value
        if (file == null || !file.exists()) {
            _error.value = "No downloaded file found"
            return
        }

        try {
            val context = getApplication<Application>()

            // Check if the file is valid
            if (file.length() == 0L) {
                _downloadedFile.value = null
                _error.value = "Downloaded file is corrupted. Please try downloading again."
                clearDownloadState()
                return
            }

            // For Android 8.0 and later, check if install from unknown sources is allowed
            if (!context.packageManager.canRequestPackageInstalls()) {
                // Automatically open settings to allow install from unknown sources
                try {
                    val intent =
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            "package:${context.packageName}".toUri(),
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    _error.value = "Could not open settings. Please enable 'Install unknown apps' manually in Settings."
                    Log.e(tag, "Error opening unknown sources settings", e)
                }
                return
            }

            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                // IMPORTANT: Clear download state after successful launch of installer
                // This prevents the app from thinking the update is still downloaded
                // and showing "Install Update" again after the user installs it.
                _downloadedFile.value = null
                activeDownload = null
                _downloadState.value = null
                clearDownloadState() // Clear persisted state as well
            } else {
                // Keep _downloadedFile so user can retry from Settings
                _error.value =
                    "No app available to install APK files. The APK file is still saved — go to your file manager to install it manually."
                clearDownloadState()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error installing APK", e)
            // Keep _downloadedFile so user can retry install
            _error.value = "Could not install APK: ${e.message ?: "Unknown error"}. Tap Install to try again."
            clearDownloadState()
        }
    }

    /**
     * Cancel the current download with proper cleanup
     */
    fun cancelDownload() {
        Log.d(tag, "Cancelling download")
        activeCall?.cancel()
        activeCall = null
        _isDownloading.value = false
        _error.value = null

        // Delete partial download file
        try {
            val context = getApplication<Application>()
            val downloadDir =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }

            activeDownload?.let { downloadState ->
                downloadDir?.let { dir ->
                    val file = File(dir, downloadState.fileName)
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(tag, "Deleted partial download file: ${file.absolutePath}, success: $deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deleting partial download file", e)
        }

        // Clear download state
        activeDownload = null
        _downloadState.value = null
        _downloadProgress.value = 0f
        clearDownloadState()
        cancelDownloadNotification()
    }

    /**
     * Reset all download states - useful for retry scenarios
     */
    fun resetDownloadState() {
        Log.d(tag, "Resetting download state")
        activeCall?.cancel()
        activeCall = null
        activeDownload = null
        _isDownloading.value = false
        _downloadProgress.value = 0f
        _downloadState.value = null
        _downloadedFile.value = null
        _error.value = null

        // Clear persisted state
        clearDownloadState()
        cancelDownloadNotification()
    }

    /**
     * Resume a previously paused/interrupted download
     */
    fun resumeDownload() {
        if (activeDownload == null) {
            _error.value = "No download to resume"
            return
        }

        if (_isDownloading.value) {
            Log.d(tag, "Download already in progress")
            return
        }

        val downloadState = activeDownload!!
        Log.d(
            tag,
            "Resuming download: ${downloadState.fileName} from ${downloadState.downloadedBytes} bytes (retry: ${downloadState.retryCount})",
        )

        downloadApkInApp(downloadState.url, downloadState.fileName, downloadState.totalBytes, downloadState.retryCount)
    }

    /**
     * Check if there's a download that can be resumed
     */
    fun canResumeDownload(): Boolean = activeDownload != null && !_isDownloading.value && _downloadedFile.value == null

    /**
     * Start periodic update checks if auto-check is enabled and updates are enabled
     */
    private fun startPeriodicUpdateChecks() {
        viewModelScope.launch {
            // Combine both update settings
            _appSettings.updatesEnabled.collectLatest { updatesEnabled ->
                if (updatesEnabled) {
                    _appSettings.autoCheckForUpdates.collectLatest { autoCheckEnabled ->
                        if (autoCheckEnabled) {
                            // Check immediately if it's been more than the interval
                            val timeSinceLastCheck = System.currentTimeMillis() - lastUpdateCheck
                            if (timeSinceLastCheck > updateCheckInterval) {
                                checkForUpdates(force = false)
                            }

                            // Schedule periodic checks
                            while (autoCheckEnabled && _appSettings.updatesEnabled.first()) {
                                delay(updateCheckInterval)
                                if (_appSettings.autoCheckForUpdates.first() && _appSettings.updatesEnabled.first()) {
                                    checkForUpdates(force = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        Log.d(tag, "ViewModel cleared")
        // Note: Active downloads will be cancelled when ViewModel is cleared
        // Download state is persisted and can be resumed later
        cancelDownload()
    }

    private fun ensureDownloadNotificationChannel() {
        val context = getApplication<Application>()

        val channel =
            NotificationChannel(
                updateDownloadChannelId,
                context.getString(R.string.notification_updater_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_updater_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun maybeUpdateDownloadProgressNotification(progress: Float) {
        val progressPercent = progress.toInt().coerceIn(0, 100)
        if (progressPercent == lastNotifiedProgressPercent) {
            return
        }
        lastNotifiedProgressPercent = progressPercent
        showDownloadProgressNotification(progressPercent)
    }

    private fun showDownloadProgressNotification(progressPercent: Int) {
        val context = getApplication<Application>()
        val openUpdatesIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "updates")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                4201,
                openUpdatesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = context.getString(R.string.notification_updater_downloading, progressPercent)
        val notification =
            NotificationCompat
                .Builder(context, updateDownloadChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_updater_title))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(100, progressPercent, false)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(updateDownloadNotificationId, notification)
    }

    private fun showDownloadCompletedNotification(
        @Suppress("UNUSED_PARAMETER") fileName: String,
    ) {
        val context = getApplication<Application>()
        val openUpdatesIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "updates")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                4202,
                openUpdatesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = context.getString(R.string.notification_updater_download_complete)
        val notification =
            NotificationCompat
                .Builder(context, updateDownloadChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_updater_title))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(updateDownloadNotificationId, notification)

        completionNotificationDismissJob?.cancel()
        completionNotificationDismissJob =
            viewModelScope.launch {
                delay(updateDownloadCompletionAutoDismissMs)
                notificationManager.cancel(updateDownloadNotificationId)
                completionNotificationDismissJob = null
            }
    }

    private fun cancelDownloadNotification() {
        completionNotificationDismissJob?.cancel()
        completionNotificationDismissJob = null
        notificationManager.cancel(updateDownloadNotificationId)
        lastNotifiedProgressPercent = -1
    }
}
