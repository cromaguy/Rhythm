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
import chromahub.rhythm.app.util.UpdateIntegrity
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
    val sha256: String? = null,
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
        viewModelScope.launch(Dispatchers.IO) { loadDownloadState() }

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
                    val isValid = runCatching {
                        UpdateIntegrity.verifyBytes(file, downloadPrefs.getLong("verified_size", 0), downloadPrefs.getString("verified_checksum", null))
                        UpdateIntegrity.verifyPackage(getApplication(), file)
                    }.isSuccess

                    if (isValid) {
                        val info = getApplication<Application>().packageManager.getPackageArchiveInfo(file.path, 0)
                        val code = if (Build.VERSION.SDK_INT >= 28) info?.longVersionCode else info?.versionCode?.toLong()
                        if (code != null && code <= BuildConfig.VERSION_CODE) {
                            clearDownloadState(); _downloadProgress.value = 0f
                            return
                        }
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
                // Apply compatible experience data before considering the native package update.
                if (chromahub.rhythm.app.features.local.data.experience.ExperienceRulesWorker.enabled(getApplication())) {
                    try { chromahub.rhythm.app.features.local.data.experience.ExperienceRulesWorker.checkNow(getApplication()) }
                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (error: Exception) { Log.w(tag, "Experience update unavailable; checking APK normally", error) }
                }
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
            val v1 = r1.tag_name
            val v2 = r2.tag_name
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
        val versionName = release.tag_name.removePrefix("v").removePrefix("V")

        return AppVersion(
            versionName = versionName,
            versionCode = calculateVersionCode(versionName),
            releaseDate = releaseDateString,
            whatsNew = releaseContent.whatsNew,
            knownIssues = releaseContent.knownIssues,
            downloadUrl = downloadUrl,
            apkAssetName = apkAsset?.name ?: "",
            sha256 = UpdateIntegrity.sha256(apkAsset?.digest),
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

            if (UpdateIntegrity.sha256(latestVersion.sha256) == null || !latestVersion.apkAssetName.endsWith(".apk", true)) {
                _error.value = getApplication<Application>().getString(R.string.update_integrity_missing)
                openInBrowser(downloadUrl)
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
        if (_isDownloading.value) return
        val release = _latestVersion.value?.takeIf { it.downloadUrl == downloadUrl }
        val expectedDigest = release?.sha256 ?: activeDownload?.checksum
        val size = if (expectedSize > 0) expectedSize else activeDownload?.totalBytes ?: 0
        if (File(fileName).name != fileName || '\\' in fileName || !fileName.endsWith(".apk", true) || size <= 0 || UpdateIntegrity.sha256(expectedDigest) == null) {
            _error.value = getApplication<Application>().getString(R.string.update_integrity_failed)
            return
        }
        val resume = activeDownload?.url == downloadUrl && activeDownload?.fileName == fileName
        val previous = if (resume) activeDownload else null
        if (!resume) { _downloadedFile.value = null; activeDownload = null; _downloadState.value = null }
        _isDownloading.value = true; _error.value = null
        cancelDownloadNotification()
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "verified-updates").apply { mkdirs() }
            val file = File(directory, fileName)
            var call: Call? = null
            try {
                if (!resume) file.delete()
                var offset = file.length()
                if (offset >= size) { file.delete(); offset = 0 }
                val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
                val request = Request.Builder().url(downloadUrl).header("User-Agent", "T2Rhythm/${BuildConfig.VERSION_NAME}")
                if (offset > 0) {
                    request.header("Range", "bytes=$offset-")
                    previous?.etag?.let { request.header("If-Range", it) }
                }
                call = client.newCall(request.build()); activeCall = call
                call.execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val append = response.code == 206 && offset > 0
                    if (response.code == 206) {
                        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty()) ?: error("Invalid range")
                        require(range.groupValues[1].toLong() == offset && range.groupValues[3].toLong() == size) { "Resumed APK range mismatch" }
                    }
                    if (!append) offset = 0
                    activeDownload = DownloadState(fileName, downloadUrl, size, offset, response.header("ETag"), response.header("Last-Modified"), offset, expectedDigest, retryAttempt)
                    _downloadState.value = activeDownload; saveDownloadState()
                    var written = offset; var savedPercent = -1
                    response.body.byteStream().use { input -> FileOutputStream(file, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (!_isDownloading.value || activeCall !== call) return@launch
                            val n = input.read(buffer); if (n < 0) break
                            written += n; require(written <= size) { "APK exceeds declared size" }; output.write(buffer, 0, n)
                            val progress = (written * 100.0 / size).toFloat().coerceIn(0f, 100f)
                            _downloadProgress.value = progress; maybeUpdateDownloadProgressNotification(progress)
                            activeDownload = activeDownload?.copy(downloadedBytes = written, resumePosition = written); _downloadState.value = activeDownload
                            if (progress.toInt() / 5 != savedPercent) { output.flush(); output.fd.sync(); saveDownloadState(); savedPercent = progress.toInt() / 5 }
                        }
                        output.flush(); output.fd.sync()
                    } }
                }
                if (!_isDownloading.value || activeCall !== call) return@launch
                UpdateIntegrity.verifyBytes(file, size, expectedDigest)
                UpdateIntegrity.verifyPackage(context, file, release?.versionCode)
                downloadPrefs.edit(commit = true) { putString("verified_checksum", expectedDigest); putLong("verified_size", size) }
                _downloadedFile.value = file; _downloadProgress.value = 100f; _isDownloading.value = false
                activeDownload = null; activeCall = null; _downloadState.value = null; saveDownloadState()
                showDownloadCompletedNotification(file.name)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                call?.cancel(); throw cancelled
            } catch (invalid: IllegalArgumentException) {
                if (activeCall === call) {
                    file.delete(); _isDownloading.value = false; activeDownload = null; activeCall = null
                    _downloadState.value = null; clearDownloadState(); cancelDownloadNotification()
                    _error.value = context.getString(R.string.update_integrity_failed)
                }
            } catch (error: Exception) {
                if (_isDownloading.value && activeCall === call) handleDownloadFailure(downloadUrl, fileName, retryAttempt, error.message ?: "Download failed")
            }
        }
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
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "verified-updates")
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
                if (!_isDownloading.value && activeDownload?.url == downloadUrl) {
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
        _canProceedWithMismatchedDownload.value = false
        _error.value = getApplication<Application>().getString(R.string.update_integrity_failed)
    }

    private fun cleanupUpdaterDownloadAfterFailure(
        message: String,
        fileToDelete: File? = null,
    ) {
        val context = getApplication<Application>()
        val downloadDir =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "verified-updates")
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

    private fun selectReleaseApkAsset(release: GitHubRelease): GitHubAsset? = UpdateIntegrity.selectAsset(
        release.assets, resolveUpdateSourceFlavor().lowercase(Locale.ROOT), BuildConfig.DEBUG, Build.SUPPORTED_ABIS.toList())

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
        viewModelScope.launch {
            val file = _downloadedFile.value ?: return@launch
            val valid = withContext(Dispatchers.IO) { runCatching {
                UpdateIntegrity.verifyBytes(file, downloadPrefs.getLong("verified_size", 0), downloadPrefs.getString("verified_checksum", null))
                UpdateIntegrity.verifyPackage(getApplication(), file, _latestVersion.value?.versionCode)
            }.isSuccess }
            if (valid) launchVerifiedInstaller() else {
                _error.value = getApplication<Application>().getString(R.string.update_integrity_failed)
                _downloadedFile.value = null; clearDownloadState()
            }
        }
    }

    private fun launchVerifiedInstaller() {
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
                // Installer launch is not installation success. Keep the verified APK so cancel/retry works.
                saveDownloadState()
            } else {
                // Keep _downloadedFile so user can retry from Settings
                _error.value =
                    "No app available to install APK files. The APK file is still saved — go to your file manager to install it manually."
                saveDownloadState()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error installing APK", e)
            // Keep _downloadedFile so user can retry install
            _error.value = getApplication<Application>().getString(R.string.update_install_retry)
            saveDownloadState()
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
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "verified-updates")

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
