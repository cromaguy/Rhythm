/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.core.domain.model.StreamingConfig
import chromahub.rhythm.app.core.domain.model.StreamingQuality
import chromahub.rhythm.app.core.utils.NetworkUtils
import chromahub.rhythm.app.features.streaming.data.repository.StreamingMusicRepositoryImpl
import chromahub.rhythm.app.features.streaming.data.repository.StreamingServiceSession
import chromahub.rhythm.app.features.streaming.data.repository.StreamingServiceSessionRepository
import chromahub.rhythm.app.features.streaming.di.StreamingMusicModule
import chromahub.rhythm.app.features.streaming.domain.model.BrowseCategory
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceId
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceRules
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.infrastructure.notification.StreamingNotificationManager
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.ArtistSeparator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.net.Uri
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.R
import android.util.Log
import androidx.core.net.toUri
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for managing streaming music playback and library.
 * Handles authentication, browsing, and playback for streaming services.
 */
class StreamingMusicViewModel(application: Application) : AndroidViewModel(application) {
    private val appSettings = AppSettings.getInstance(application)

    companion object {
        private const val AUTH_PING_RETRIES = 3
        private const val AUTH_PING_RETRY_DELAY_MS = 800L
    }
    private val serviceSessionRepository = StreamingServiceSessionRepository(application)
    val repository = StreamingMusicModule.provideStreamingMusicRepository(application)
    private val providerRepository = repository as? StreamingMusicRepositoryImpl
    private val notificationManager = StreamingNotificationManager(application)
    private var playbackHandler: ((List<StreamingSong>, Int) -> Unit)? = null
    private var seekProgressHandler: ((Float) -> Unit)? = null
    private var seekPositionHandler: ((Long) -> Unit)? = null
    private var wasOffline: Boolean? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkLostJob: Job? = null
    private var networkAvailableJob: Job? = null
    private val authMutex = Mutex()
    private var lastSuccessfulAuthTimestamp = 0L

    private fun showStatusToast(resId: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(getApplication(), resId, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateOnlineStatus(isOnline: Boolean) {
        val previous = wasOffline
        if (previous == null) {
            wasOffline = !isOnline
            return
        }
        if (previous && isOnline) {
            wasOffline = false
            showStatusToast(R.string.rhythm_go_online_toast)
        } else if (!previous && !isOnline) {
            wasOffline = true
            showStatusToast(R.string.rhythm_go_offline_toast)
        }
    }

    fun switchToDownloadedMode() {
        val downloaded = _downloadedSongs.value
        val downloadedAlb = if (_downloadedAlbums.value.isNotEmpty()) _downloadedAlbums.value else deriveAlbumsFromSongs(downloaded, limit = 500)
        val downloadedArt = if (_downloadedArtists.value.isNotEmpty()) _downloadedArtists.value else deriveArtistsFromSongs(downloaded, limit = 500)
        
        _downloadedSongs.value = downloaded
        _downloadedAlbums.value = downloadedAlb
        _downloadedArtists.value = downloadedArt
        _allSongs.value = downloaded
        _savedAlbums.value = downloadedAlb
        _newReleases.value = downloadedAlb
        _followedArtists.value = downloadedArt
        _recommendations.value = downloaded.shuffled().take(24)
        _savedPlaylists.value = emptyList()
        _likedSongs.value = emptyList()
        _isLoading.value = false
        _syncProgress.value = StreamingSyncProgress(isSyncing = false, stage = StreamingSyncStage.Idle)
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networkLostJob?.cancel()
                    networkAvailableJob?.cancel()
                    networkAvailableJob = viewModelScope.launch {
                        delay(1500)
                        if (!NetworkUtils.isNetworkAvailable(getApplication())) return@launch
                        val serviceId = appSettings.streamingService.value
                        if (serviceSessionRepository.isConnected(serviceId)) {
                            val connected = checkAndSyncAuthentication(serviceId, forceCheck = true)
                            if (connected) {
                                loadHomeContent()
                                loadLibrary()
                            }
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    if (hasInternet) {
                        networkLostJob?.cancel()
                    }
                }

                override fun onLost(network: Network) {
                    // If device still has another active network interface, avoid false offline drops
                    if (NetworkUtils.isNetworkAvailable(getApplication())) {
                        return
                    }
                    networkLostJob?.cancel()
                    networkLostJob = viewModelScope.launch {
                        delay(2500)
                        val isStillAvail = NetworkUtils.isNetworkAvailable(getApplication())
                        if (!isStillAvail) {
                            networkAvailableJob?.cancel()
                            _isAuthenticated.value = false
                            updateOnlineStatus(false)
                            switchToDownloadedMode()
                        }
                    }
                }
            }
            networkCallback = callback
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w("StreamingMusicViewModel", "Failed to register network callback", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            networkCallback?.let {
                val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            // Ignore callback unregister error
        }
    }

    
    // Authentication state
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    val serviceSessions: StateFlow<Map<String, StreamingServiceSession>> = serviceSessionRepository.sessions
    
    private val _currentService = MutableStateFlow(SourceType.SUBSONIC)
    val currentService: StateFlow<SourceType> = _currentService.asStateFlow()
    
    private val _streamingConfig = MutableStateFlow(StreamingConfig())
    val streamingConfig: StateFlow<StreamingConfig> = _streamingConfig.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncProgress = MutableStateFlow(StreamingSyncProgress())
    val syncProgress: StateFlow<StreamingSyncProgress> = _syncProgress.asStateFlow()

    private val _hasLoadedHomeContent = MutableStateFlow(false)
    val hasLoadedHomeContent: StateFlow<Boolean> = _hasLoadedHomeContent.asStateFlow()

    private val _hasLoadedLibrary = MutableStateFlow(false)
    val hasLoadedLibrary: StateFlow<Boolean> = _hasLoadedLibrary.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Home content
    private val _recommendations = MutableStateFlow<List<StreamingSong>>(emptyList())
    val recommendations: StateFlow<List<StreamingSong>> = _recommendations.asStateFlow()
    
    private val _newReleases = MutableStateFlow<List<StreamingAlbum>>(emptyList())
    val newReleases: StateFlow<List<StreamingAlbum>> = _newReleases.asStateFlow()
    
    private val _featuredPlaylists = MutableStateFlow<List<StreamingPlaylist>>(emptyList())
    val featuredPlaylists: StateFlow<List<StreamingPlaylist>> = _featuredPlaylists.asStateFlow()
    
    // Browse content
    private val _browseCategories = MutableStateFlow<List<BrowseCategory>>(emptyList())
    val browseCategories: StateFlow<List<BrowseCategory>> = _browseCategories.asStateFlow()
    
    private val _topCharts = MutableStateFlow<List<StreamingSong>>(emptyList())
    val topCharts: StateFlow<List<StreamingSong>> = _topCharts.asStateFlow()
    
    // Library content
    private val _likedSongs = MutableStateFlow<List<StreamingSong>>(emptyList())
    val likedSongs: StateFlow<List<StreamingSong>> = _likedSongs.asStateFlow()
    
    private val _savedAlbums = MutableStateFlow<List<StreamingAlbum>>(emptyList())
    val savedAlbums: StateFlow<List<StreamingAlbum>> = _savedAlbums.asStateFlow()
    
    private val _followedArtists = MutableStateFlow<List<StreamingArtist>>(emptyList())
    val followedArtists: StateFlow<List<StreamingArtist>> = _followedArtists.asStateFlow()
    
    private val _savedPlaylists = MutableStateFlow<List<StreamingPlaylist>>(emptyList())
    val savedPlaylists: StateFlow<List<StreamingPlaylist>> = _savedPlaylists.asStateFlow()
    
    private val _downloadedSongs = MutableStateFlow<List<StreamingSong>>(emptyList())
    val downloadedSongs: StateFlow<List<StreamingSong>> = _downloadedSongs.asStateFlow()

    private val _downloadedAlbums = MutableStateFlow<List<StreamingAlbum>>(emptyList())
    val downloadedAlbums: StateFlow<List<StreamingAlbum>> = _downloadedAlbums.asStateFlow()

    private val _downloadedArtists = MutableStateFlow<List<StreamingArtist>>(emptyList())
    val downloadedArtists: StateFlow<List<StreamingArtist>> = _downloadedArtists.asStateFlow()

    private val _downloadingSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingSongIds: StateFlow<Set<String>> = _downloadingSongIds.asStateFlow()

    // All provider songs (full catalog exposed by repository)
    private val _allSongs = MutableStateFlow<List<StreamingSong>>(emptyList())
    val allSongs: StateFlow<List<StreamingSong>> = _allSongs.asStateFlow()
    
    // Current playback state
    private val _currentSong = MutableStateFlow<StreamingSong?>(null)
    val currentSong: StateFlow<StreamingSong?> = _currentSong.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    // Queue
    private val _queue = MutableStateFlow<List<StreamingSong>>(emptyList())
    val queue: StateFlow<List<StreamingSong>> = _queue.asStateFlow()
    
    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<StreamingSearchResults>(StreamingSearchResults())
    val searchResults: StateFlow<StreamingSearchResults> = _searchResults.asStateFlow()
    
    init {
        observeSelectedService()
        registerNetworkCallback()
        // Keep an updated view of the provider catalog exposed by the repository
        viewModelScope.launch {
            repository.getSongs().collect { items ->
                _allSongs.value = items.filterIsInstance<StreamingSong>()
            }
        }
        // Keep an updated view of provider artists
        viewModelScope.launch {
            repository.getArtists().collect { items ->
                val artists = items.filterIsInstance<StreamingArtist>()
                if (artists.isNotEmpty() && _followedArtists.value.isEmpty()) {
                    _followedArtists.value = artists
                }
            }
        }
        // Keep an updated view of provider albums
        viewModelScope.launch {
            repository.getAlbums().collect { items ->
                val albums = items.filterIsInstance<StreamingAlbum>()
                if (albums.isNotEmpty() && _savedAlbums.value.isEmpty()) {
                    _savedAlbums.value = albums
                }
            }
        }
        // Keep an updated view of downloaded songs
        viewModelScope.launch {
            repository.getDownloadedSongs().collect { items ->
                val downloaded = items.filterIsInstance<StreamingSong>()
                _downloadedSongs.value = downloaded
                _downloadedAlbums.value = deriveAlbumsFromSongs(downloaded, limit = 500)
                _downloadedArtists.value = deriveArtistsFromSongs(downloaded, limit = 500)
            }
        }
        // React immediately to offline mode toggles
        viewModelScope.launch {
            appSettings.offlineMode.drop(1).collect { isOffline ->
                if (isOffline) {
                    switchToDownloadedMode()
                } else {
                    if (NetworkUtils.isNetworkAvailable(getApplication())) {
                        val serviceId = appSettings.streamingService.value
                        if (serviceSessionRepository.isConnected(serviceId)) {
                            val connected = checkAndSyncAuthentication(serviceId, forceCheck = true)
                            if (connected) {
                                loadHomeContent()
                                loadLibrary()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeSelectedService() {
        viewModelScope.launch {
            appSettings.streamingService.collect { serviceId ->
                val normalizedServiceId = normalizeServiceId(serviceId)
                if (normalizedServiceId != serviceId) {
                    appSettings.setStreamingService(normalizedServiceId)
                    return@collect
                }

                _currentService.value = sourceTypeFromServiceId(normalizedServiceId)

                val connected = checkAndSyncAuthentication(normalizedServiceId)
                loadHomeContent()
                loadLibrary()
            }
        }
    }
    
    /**
     * Check if user is authenticated with the current service.
     */
    private fun checkAuthenticationStatus() {
        viewModelScope.launch {
            checkAndSyncAuthentication()
        }
    }
    
    /**
     * Select a streaming service.
     */
    fun selectService(service: SourceType) {
        viewModelScope.launch {
            val serviceId = serviceIdFromSourceType(service)
            _currentService.value = service
            if (appSettings.streamingService.value != serviceId) {
                appSettings.setStreamingService(serviceId)
            }
            checkAndSyncAuthentication(serviceId)
        }
    }
    
    /**
     * Authenticate with the current streaming service.
     */
    fun authenticate() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                checkAndSyncAuthentication()
                if (!_isAuthenticated.value) {
                    _error.value = "Open service setup and connect an account first"
                }
            } catch (e: Exception) {
                _error.value = "Authentication failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Log out from the current streaming service.
     */
    fun logout() {
        val selectedService = appSettings.streamingService.value
        disconnectService(selectedService)
    }

    fun connectService(serviceId: String, serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val normalizedServiceId = normalizeServiceId(serviceId)
                validateCredentials(normalizedServiceId, serverUrl, username, password)

                val connection = providerRepository?.connect(
                    serviceId = normalizedServiceId,
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    saveCredentials = appSettings.rememberStreamingPasswords.value
                ) ?: throw IllegalStateException("Streaming repository is not initialized")

                serviceSessionRepository.connect(
                    serviceId = normalizedServiceId,
                    serverUrl = connection.serverUrl.trim(),
                    username = connection.displayName.trim()
                )
                if (appSettings.streamingService.value != normalizedServiceId) {
                    appSettings.setStreamingService(normalizedServiceId)
                }
                checkAndSyncAuthentication(normalizedServiceId)
                loadHomeContent()
                loadLibrary()
                
                // Show success notification
                notificationManager.notifyAuthenticationSuccess(getSourceTypeName(sourceTypeFromServiceId(normalizedServiceId)))
            } catch (e: Exception) {
                val userMessage = when {
                    e is java.net.ConnectException ||
                    e is java.net.SocketTimeoutException ||
                    (e is java.io.IOException && (e.message?.contains("connect", ignoreCase = true) == true ||
                                                  e.message?.contains("timeout", ignoreCase = true) == true)) ||
                    e.cause is java.net.ConnectException ||
                    e.cause is java.net.SocketTimeoutException ->
                        "Cannot reach server. Please check that the server address and port are correct and that the server is online and reachable from this device."
                    e is java.net.UnknownHostException ||
                    e.cause is java.net.UnknownHostException ->
                        "Server not found. Please check the server URL."
                    e is javax.net.ssl.SSLException ||
                    e.cause is javax.net.ssl.SSLException ->
                        "Secure connection failed. The server's certificate may not be trusted."
                    e.message?.contains("HTTP 401", ignoreCase = true) == true ||
                    e.message?.contains("401", ignoreCase = true) == true ->
                        "Incorrect username or password."
                    e.message?.contains("HTTP 4", ignoreCase = true) == true ->
                        "Server rejected the connection (${e.message}). Check credentials and server version."
                    else -> "Connection failed: ${e.message}"
                }
                _error.value = userMessage
                notificationManager.notifyAuthenticationFailed(getSourceTypeName(_currentService.value))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun disconnectService(serviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val normalizedServiceId = normalizeServiceId(serviceId)
                providerRepository?.disconnect(normalizedServiceId)

                serviceSessionRepository.disconnect(normalizedServiceId)
                if (appSettings.streamingService.value == normalizedServiceId) {
                    checkAndSyncAuthentication(normalizedServiceId)
                    clearContent()
                }
            } catch (e: Exception) {
                _error.value = "Disconnect failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getServiceSession(serviceId: String): StreamingServiceSession {
        return serviceSessionRepository.getSession(serviceId)
    }

    fun setPlaybackHandler(handler: (List<StreamingSong>, Int) -> Unit) {
        playbackHandler = handler
    }

    fun setSeekHandlers(
        progressHandler: (Float) -> Unit,
        positionHandler: (Long) -> Unit
    ) {
        seekProgressHandler = progressHandler
        seekPositionHandler = positionHandler
    }
    
    /**
     * Report an error to the user.
     */
    fun reportError(message: String) {
        _error.value = message
    }
    
    /**
     * Report a warning (stored in error state for display).
     */
    fun reportWarning(message: String) {
        _error.value = message
    }
    
    /**
     * Refresh the current service session connection status.
     */
    fun refreshCurrentSession() {
        viewModelScope.launch {
            val currentServiceId = appSettings.streamingService.value
            checkAndSyncAuthentication(currentServiceId)
        }
    }
    
    /**
     * Load home screen content.
     */
    fun loadHomeContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _hasLoadedHomeContent.value = false
            
            try {
                if (!checkAndSyncAuthentication()) {
                    switchToDownloadedMode()
                    return@launch
                }
                
                // Check network constraints
                if (!NetworkUtils.canStream(getApplication(), appSettings.allowCellularStreaming.value)) {
                    switchToDownloadedMode()
                    _error.value = "Streaming not allowed on current network"
                    return@launch
                }
                
                if (appSettings.offlineMode.value) {
                    switchToDownloadedMode()
                    _error.value = null
                    return@launch
                }

                val syncedPlaylists = repository.syncPlaylists()

                // Use provider-native random songs instead of inefficient seed queries
                var recommendations = repository.getRecommendations(limit = 24)
                if (recommendations.isEmpty()) {
                    recommendations = repository.getRandomSongs(limit = 24)
                }

                val newReleases = repository.getNewReleases(limit = 24)

                // Use actual provider playlists only.
                val featuredPlaylists = if (syncedPlaylists.isNotEmpty()) {
                    syncedPlaylists
                } else {
                    repository.getFeaturedPlaylists(limit = 24)
                }

                _recommendations.value = recommendations
                _newReleases.value = newReleases
                _featuredPlaylists.value = featuredPlaylists
            } catch (e: Exception) {
                Log.e("StreamingMusicViewModel", "loadHomeContent failed", e)
                val isNetworkDown = !NetworkUtils.isNetworkAvailable(getApplication())
                if (isNetworkDown) {
                    switchToDownloadedMode()
                    updateOnlineStatus(false)
                    _error.value = "Connection lost. Switched to downloaded content."
                } else {
                    _error.value = "Failed to load content: ${e.message}"
                }
            } finally {
                _hasLoadedHomeContent.value = true
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh home screen and library content.
     */
    fun refreshHome() {
        viewModelScope.launch {
            checkAndSyncAuthentication(forceCheck = true)
            loadHomeContent()
            loadLibrary()
        }
    }

    /**
     * Refresh library content.
     */
    fun refreshLibrary() {
        loadLibrary()
    }
    
    /**
     * Load browse categories.
     */
    fun loadBrowseCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                if (!checkAndSyncAuthentication()) {
                    _browseCategories.value = emptyList()
                    return@launch
                }

                _browseCategories.value = repository.getBrowseCategories()
            } catch (e: Exception) {
                _error.value = "Failed to load categories: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load top charts.
     */
    fun loadTopCharts() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                if (!checkAndSyncAuthentication()) {
                    _topCharts.value = emptyList()
                    return@launch
                }

                _topCharts.value = repository.getTopCharts()
            } catch (e: Exception) {
                _error.value = "Failed to load charts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load user's library content.
     */
    fun loadLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            _hasLoadedLibrary.value = false
            val serviceName = getSourceTypeName(_currentService.value)
            _syncProgress.value = StreamingSyncProgress(isSyncing = true, stage = StreamingSyncStage.Syncing)
            notificationManager.notifySyncStarted(serviceName)
            var syncSuccess = false
            
            try {
                if (!checkAndSyncAuthentication()) {
                    switchToDownloadedMode()
                    return@launch
                }

                // 1. First fetch artists directly from provider so they are available immediately
                try {
                    repository.syncArtists()
                } catch (e: Exception) {
                    Log.e("StreamingMusicViewModel", "syncArtists failed", e)
                }

                // 2. Pull the provider catalog with live progress callbacks
                try {
                    repository.syncCatalog(limit = 5_000) { current, total, songCount ->
                        _syncProgress.value = StreamingSyncProgress(
                            isSyncing = true,
                            current = current,
                            total = total,
                            songsCount = songCount,
                            stage = StreamingSyncStage.Syncing
                        )
                        notificationManager.updateSyncProgress(
                            songCount = songCount,
                            albumCount = current,
                            artistCount = total,
                            current = current,
                            total = total
                        )
                    }
                } catch (e: Exception) {
                    Log.e("StreamingMusicViewModel", "syncCatalog failed", e)
                }

                val likedSongs = try { repository.getLikedSongs().first() } catch (e: Exception) { emptyList() }
                val followedArtists = try { repository.getFollowedArtists().first() } catch (e: Exception) { emptyList() }
                val downloadedSongs = try { repository.getDownloadedSongs().first() } catch (e: Exception) { emptyList() }
                
                var syncedPlaylists = emptyList<StreamingPlaylist>()
                try {
                    syncedPlaylists = repository.syncPlaylists()
                } catch (e: Exception) {
                    Log.e("StreamingMusicViewModel", "syncPlaylists failed", e)
                }

                val savedPlaylists = try {
                    repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
                } catch (e: Exception) {
                    emptyList()
                }

                val savedAlbums = try { repository.getSavedAlbums().first() } catch (e: Exception) { emptyList() }
                val newReleases = try { repository.getNewReleases(limit = 100) } catch (e: Exception) { emptyList() }

                val catalogAlbums = if (savedAlbums.isNotEmpty()) {
                    savedAlbums
                } else {
                    newReleases
                }
                val catalogArtists = try {
                    repository.getArtists().first()
                        .filterIsInstance<StreamingArtist>()
                        .distinctBy { it.id }
                } catch (e: Exception) {
                    emptyList()
                }

                val hasExplicitLibraryData = likedSongs.isNotEmpty() ||
                    savedAlbums.isNotEmpty() ||
                    followedArtists.isNotEmpty() ||
                    downloadedSongs.isNotEmpty() ||
                    savedPlaylists.isNotEmpty()

                // Use provider-native methods instead of inefficient seeding/derivation
                val resolvedAlbums = catalogAlbums

                val resolvedArtists = if (followedArtists.isNotEmpty()) {
                    val catalogArtistsByName = catalogArtists.associateBy { it.name.lowercase() }
                    val separatorEnabled = appSettings.artistSeparatorEnabled.value
                    val separatorDelimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { AppSettings.DEFAULT_ARTIST_SEPARATOR_DELIMITERS }
                    followedArtists
                        .flatMap { followedArtist ->
                            val splitNames = ArtistSeparator.splitArtistNames(
                                followedArtist.name,
                                delimiters = separatorDelimiters,
                                enabled = separatorEnabled
                            )
                            if (splitNames.isEmpty()) {
                                listOf(
                                    catalogArtistsByName[followedArtist.name.lowercase()]
                                        ?: catalogArtists.firstOrNull { it.id == followedArtist.id }
                                        ?: followedArtist
                                )
                            } else {
                                splitNames.map { name ->
                                    catalogArtistsByName[name.lowercase()]
                                        ?: catalogArtists.firstOrNull { it.id == repository.buildArtistId(followedArtist.sourceType.name, name) }
                                        ?: followedArtist.copy(
                                            id = repository.buildArtistId(followedArtist.sourceType.name, name),
                                            name = name,
                                            artworkUri = followedArtist.artworkUri
                                        )
                                }
                            }
                        }
                        .distinctBy { it.id }
                } else if (catalogArtists.isNotEmpty()) {
                    catalogArtists
                } else {
                    val directArtists = try {
                        repository.syncArtists()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (directArtists.isNotEmpty()) {
                        directArtists
                    } else {
                        repository.searchArtists("")
                            .filterIsInstance<StreamingArtist>()
                            .distinctBy { it.id }
                    }
                }

                val resolvedPlaylists = when {
                    savedPlaylists.isNotEmpty() -> (savedPlaylists + syncedPlaylists).distinctBy { it.id }
                    syncedPlaylists.isNotEmpty() -> syncedPlaylists
                    else -> {
                        // Try featured playlists from the provider cache
                        val featuredPlaylists = repository.getFeaturedPlaylists(limit = 24)
                        if (featuredPlaylists.isNotEmpty()) {
                            featuredPlaylists
                        } else {
                            // Don't derive playlists in streaming context - show empty list instead
                            // Only show actual streaming provider playlists
                            emptyList()
                        }
                    }
                }

                _likedSongs.value = likedSongs
                _savedAlbums.value = resolvedAlbums
                _newReleases.value = newReleases
                _followedArtists.value = resolvedArtists
                _savedPlaylists.value = resolvedPlaylists
                _downloadedSongs.value = downloadedSongs
                _downloadedAlbums.value = deriveAlbumsFromSongs(downloadedSongs, limit = 500)
                _downloadedArtists.value = deriveArtistsFromSongs(downloadedSongs, limit = 500)

                val catalogSongs = try {
                    repository.getSongs().first().filterIsInstance<StreamingSong>()
                } catch (e: Exception) {
                    emptyList()
                }
                if (catalogSongs.isNotEmpty()) {
                    _allSongs.value = catalogSongs
                }

                if (_featuredPlaylists.value.isEmpty()) {
                    _featuredPlaylists.value = resolvedPlaylists
                }
                syncSuccess = true
            } catch (e: Exception) {
                Log.e("StreamingMusicViewModel", "loadLibrary failed", e)
                _error.value = "Failed to load library: ${e.message}"
                notificationManager.notifySyncFailed(e.message)
                val isNetworkDown = !NetworkUtils.isNetworkAvailable(getApplication())
                if (isNetworkDown) {
                    switchToDownloadedMode()
                    updateOnlineStatus(false)
                }
            } finally {
                val finalSongsCount = if (syncSuccess) {
                    _allSongs.value.size.takeIf { it > 0 } ?: _syncProgress.value.songsCount
                } else {
                    0
                }
                _syncProgress.value = StreamingSyncProgress(
                    isSyncing = false,
                    current = if (syncSuccess) _syncProgress.value.total else 0,
                    total = if (syncSuccess) _syncProgress.value.total else 0,
                    songsCount = finalSongsCount,
                    stage = if (syncSuccess) StreamingSyncStage.Complete else if (_error.value != null) StreamingSyncStage.Error else StreamingSyncStage.Idle
                )
                if (syncSuccess) {
                    notificationManager.notifySyncComplete(finalSongsCount, serviceName)
                }
                _hasLoadedLibrary.value = true
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Search across the streaming service.
     */
    fun search(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _searchResults.value = StreamingSearchResults()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            try {
                if (!checkAndSyncAuthentication()) {
                    _searchResults.value = StreamingSearchResults()
                    _error.value = "Connect to a streaming service first"
                    return@launch
                }
                
                // Check network and offline constraints
                if (!NetworkUtils.canStream(getApplication(), appSettings.allowCellularStreaming.value)) {
                    _searchResults.value = StreamingSearchResults()
                    _error.value = "Streaming not allowed on current network"
                    return@launch
                }
                
                if (appSettings.offlineMode.value) {
                    _searchResults.value = StreamingSearchResults()
                    _error.value = "Search not available in offline mode"
                    return@launch
                }

                val songs = repository.searchSongs(query).filterIsInstance<StreamingSong>()
                val artistsFromRepository = repository.searchArtists(query).filterIsInstance<StreamingArtist>()
                val albumsFromRepository = repository.searchAlbums(query).filterIsInstance<StreamingAlbum>()
                val playlists = repository.searchPlaylists(query).filterIsInstance<StreamingPlaylist>()

                _searchResults.value = StreamingSearchResults(
                    songs = songs,
                    albums = albumsFromRepository,
                    artists = artistsFromRepository,
                    playlists = playlists
                )
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Play a streaming song.
     */
    fun playSong(song: StreamingSong) {
        val queueSource = when {
            _searchResults.value.songs.any { it.id == song.id } -> _searchResults.value.songs
            _recommendations.value.any { it.id == song.id } -> _recommendations.value
            _queue.value.any { it.id == song.id } -> _queue.value
            else -> listOf(song)
        }

        val selectedIndex = queueSource.indexOfFirst { it.id == song.id }
            .takeIf { it >= 0 }
            ?: 0

        val keepShuffle = appSettings.keepShuffleOnSelection.value && appSettings.savedShuffleState.value
        playQueue(queueSource, startIndex = selectedIndex, shuffle = keepShuffle, pinStartIndex = keepShuffle)
    }

    /**
     * Play the current recommendation list.
     */
    fun playRecommendations(shuffle: Boolean = false) {
        playQueue(_recommendations.value, startIndex = 0, shuffle = shuffle)
    }

    /**
     * Play a specific queue and start index.
     */
    fun playQueue(queue: List<StreamingSong>, startIndex: Int = 0, shuffle: Boolean = false, pinStartIndex: Boolean = false) {
        val playableQueue = queue.filter { it.isPlayable }
        if (playableQueue.isEmpty()) {
            _error.value = "No playable tracks available"
            return
        }

        viewModelScope.launch {
            val safeStartIndex = startIndex.coerceIn(0, playableQueue.lastIndex)
            val selectedTargetSong = playableQueue[safeStartIndex]
            val isTargetDownloaded = isSongDownloaded(selectedTargetSong.id)

            val normalizedServiceId = normalizeServiceId(appSettings.streamingService.value)
            val sessionMarkedConnected = serviceSessionRepository.isConnected(normalizedServiceId)
            val credentialsExist = providerRepository?.isServiceConnected(normalizedServiceId) ?: sessionMarkedConnected

            if (!credentialsExist && !isTargetDownloaded && _downloadedSongs.value.isEmpty()) {
                _error.value = "Connect to a streaming service first"
                return@launch
            }
            val shouldPinStart = pinStartIndex || (shuffle && safeStartIndex > 0)
            val queueToPlay = if (shuffle && playableQueue.size > 1) {
                if (shouldPinStart) {
                    val startSong = playableQueue[safeStartIndex]
                    val tail = playableQueue.toMutableList().apply {
                        removeAt(safeStartIndex)
                        shuffle()
                    }
                    listOf(startSong) + tail
                } else {
                    playableQueue.shuffled()
                }
            } else {
                playableQueue
            }

            val selectedIndex = if (shuffle && queueToPlay.size > 1) {
                0
            } else {
                safeStartIndex
            }

            val selectedSong = queueToPlay[selectedIndex]
            val queueWithResolvedSongs = queueToPlay.map { song ->
                val fallbackUrl = song.streamingUrl?.takeIf { cachedUrl ->
                    val isNetworkUrl =
                        cachedUrl.startsWith("http://", ignoreCase = true) ||
                            cachedUrl.startsWith("https://", ignoreCase = true)
                    !isNetworkUrl || !song.id.contains("::")
                }
                val resolvedUrl = repository.getStreamingUrl(song.id)
                    ?: fallbackUrl

                if (resolvedUrl.isNullOrBlank()) {
                    song
                } else {
                    song.copy(streamingUrl = resolvedUrl)
                }
            }

            val selectedResolvedSong = queueWithResolvedSongs[selectedIndex]
            if (selectedResolvedSong.streamingUrl.isNullOrBlank()) {
                _error.value = when {
                    appSettings.offlineMode.value -> "Offline mode: Song not in cache"
                    !NetworkUtils.canStream(getApplication(), appSettings.allowCellularStreaming.value) -> "Streaming not allowed on current network"
                    else -> "Unable to resolve stream URL for this song"
                }
                return@launch
            }

            _queue.value = queueWithResolvedSongs
            _currentSong.value = selectedResolvedSong
            _isPlaying.value = true

            playbackHandler?.invoke(queueWithResolvedSongs, selectedIndex)
            }
    }

    /**
     * Play an album.
     */
    fun playAlbum(album: StreamingAlbum) {
        // Album logic intentionally disabled for streaming mode cleanup.
    }

    /**
     * Play a playlist.
     */
    fun playPlaylist(playlist: StreamingPlaylist) {
        viewModelScope.launch {
            val tracks = playlist.getTracks()
            if (tracks.isNotEmpty()) {
                playQueue(tracks, startIndex = 0, shuffle = false)
            }
        }
    }

    /**
     * Resolve songs for an album with repository-first lookup and local fallback.
     */
    suspend fun getAlbumSongs(album: StreamingAlbum): List<StreamingSong> {
        // 1. If the album already contains tracks (derived from songs or downloaded), return immediately
        if (album.tracks.isNotEmpty()) {
            return album.tracks
        }

        // 2. Check downloaded songs matching this album (by albumId or by title and artist)
        val downloadedMatches = _downloadedSongs.value.filter { song ->
            (song.albumId != null && song.albumId == album.id) ||
            (song.album.equals(album.title, ignoreCase = true) &&
                (album.artist.isBlank() || song.artist.equals(album.artist, ignoreCase = true)))
        }
        if (downloadedMatches.isNotEmpty()) {
            return downloadedMatches
        }

        // 3. Query repository
        try {
            val songs = repository.getAlbumSongs(album.id)
            if (songs.isNotEmpty()) {
                return songs
            }
        } catch (e: Exception) {
            Log.w("StreamingMusicVM", "Failed to get album songs from repository for ${album.id}", e)
        }

        // 4. Fallback to all loaded songs in memory
        val memoryMatches = _allSongs.value.filter { song ->
            (song.albumId != null && song.albumId == album.id) ||
            (song.album.equals(album.title, ignoreCase = true) &&
                (album.artist.isBlank() || song.artist.equals(album.artist, ignoreCase = true)))
        }
        if (memoryMatches.isNotEmpty()) {
            return memoryMatches
        }

        return emptyList()
    }

    /**
     * Resolve top songs for an artist with repository-first lookup and local fallback.
     */
    suspend fun getArtistTopSongs(
        artistId: String,
        artistNameHint: String? = null,
        limit: Int = 40
    ): List<StreamingSong> {
        val safeLimit = limit.coerceAtLeast(1)
        val cachedArtist = (_downloadedArtists.value + _followedArtists.value + _searchResults.value.artists)
            .distinctBy { it.id }
            .firstOrNull { it.id == artistId || (artistNameHint != null && it.name.equals(artistNameHint, ignoreCase = true)) }

        val embeddedTracks = cachedArtist
            ?.getTopTracks()
            .orEmpty()
            .filter { it.isPlayable }
            .distinctBy { it.id }
        if (embeddedTracks.isNotEmpty()) {
            return embeddedTracks.take(safeLimit)
        }

        val normalizedHint = artistNameHint?.trim().orEmpty()

        // Check downloaded songs matching this artist
        val downloadedMatches = _downloadedSongs.value.filter { song ->
            when {
                normalizedHint.isNotBlank() -> song.artist.equals(normalizedHint, ignoreCase = true) ||
                    ArtistSeparator.splitArtistNames(song.artist, enabled = true).any { it.equals(normalizedHint, ignoreCase = true) }
                cachedArtist != null -> song.artist.equals(cachedArtist.name, ignoreCase = true) ||
                    ArtistSeparator.splitArtistNames(song.artist, enabled = true).any { it.equals(cachedArtist.name, ignoreCase = true) }
                else -> artistIdMatchesSongArtist(artistId = artistId, songArtist = song.artist)
            }
        }
        if (downloadedMatches.isNotEmpty()) {
            return downloadedMatches.take(safeLimit)
        }

        // If service is connected and not syncing, try repository
        if (serviceSessionRepository.isConnected(appSettings.streamingService.value) && !_syncProgress.value.isSyncing) {
            try {
                val repositoryTracks = repository.getArtistTopTracks(artistId, safeLimit)
                    .filter { it.isPlayable }
                    .distinctBy { it.id }
                if (repositoryTracks.isNotEmpty()) {
                    return repositoryTracks
                }
            } catch (e: Exception) {
                Log.w("StreamingMusicVM", "getArtistTopTracks repository lookup failed for $artistId", e)
            }
        }

        // Fallback: match songs by artist name
        val allAvailableSongs = _likedSongs.value +
            _downloadedSongs.value +
            _allSongs.value +
            _recommendations.value +
            _searchResults.value.songs +
            _queue.value

        return allAvailableSongs
            .asSequence()
            .filter {
                when {
                    normalizedHint.isNotBlank() -> it.artist.equals(normalizedHint, ignoreCase = true) ||
                        ArtistSeparator.splitArtistNames(it.artist, enabled = true).any { name -> name.equals(normalizedHint, ignoreCase = true) }
                    cachedArtist != null -> it.artist.equals(cachedArtist.name, ignoreCase = true) ||
                        ArtistSeparator.splitArtistNames(it.artist, enabled = true).any { name -> name.equals(cachedArtist.name, ignoreCase = true) }
                    else -> artistIdMatchesSongArtist(artistId = artistId, songArtist = it.artist)
                }
            }
            .filter { it.isPlayable }
            .distinctBy { it.id }
            .take(safeLimit)
            .toList()
    }

    /**
     * Resolve albums for an artist with repository-first lookup and local fallback.
     */
    suspend fun getArtistAlbums(
        artistId: String,
        artistNameHint: String? = null
    ): List<StreamingAlbum> {
        val normalizedHint = artistNameHint?.trim().orEmpty()
        val downloadedArtistAlbums = _downloadedAlbums.value.filter {
            it.artist.equals(normalizedHint, ignoreCase = true)
        }

        // If connected and not syncing, try repository
        if (serviceSessionRepository.isConnected(appSettings.streamingService.value) && !_syncProgress.value.isSyncing) {
            try {
                val resolvedArtistId = if (normalizedHint.isNotBlank()) {
                    val serviceName = artistId.substringBefore("::", _currentService.value.name)
                    repository.buildArtistId(serviceName, normalizedHint)
                } else {
                    artistId
                }
                val repoAlbums = repository.getArtistAlbums(resolvedArtistId)
                if (repoAlbums.isNotEmpty()) {
                    return (downloadedArtistAlbums + repoAlbums).distinctBy { it.id }
                }
            } catch (e: Exception) {
                Log.w("StreamingMusicVM", "getArtistAlbums repository lookup failed for $artistId", e)
            }
        }

        if (downloadedArtistAlbums.isNotEmpty()) {
            return downloadedArtistAlbums
        }

        // Derive from matching downloaded or cached songs
        val matchingSongs = (_downloadedSongs.value + _allSongs.value).filter {
            it.artist.equals(normalizedHint, ignoreCase = true)
        }
        if (matchingSongs.isNotEmpty()) {
            return deriveAlbumsFromSongs(matchingSongs, 100)
        }

        return emptyList()
    }

    /**
     * Fetch full artist info (including artwork) from the repository.
     * Returns null if the artist cannot be resolved.
     */
    suspend fun getArtistInfo(
        artistId: String,
        artistNameHint: String? = null
    ): StreamingArtist? {
        // Check memory caches first including downloaded artists
        val cached = (_downloadedArtists.value + _followedArtists.value + _searchResults.value.artists)
            .distinctBy { it.id }
            .firstOrNull { it.id == artistId || (artistNameHint != null && it.name.equals(artistNameHint, ignoreCase = true)) }
        if (cached?.artworkUri != null) return cached

        return try {
            val item = repository.getArtistById(artistId)
            item as? StreamingArtist
                ?: item?.let { artistItem ->
                    // Map generic ArtistItem -> StreamingArtist using name hint
                    val name = artistItem.name.ifBlank { artistNameHint ?: artistId }
                    StreamingArtist(
                        id = artistItem.id,
                        name = name,
                        artworkUri = artistItem.artworkUri ?: cached?.artworkUri,
                        songCount = if (artistItem.songCount > 0) artistItem.songCount else (cached?.songCount ?: 0),
                        albumCount = if (artistItem.albumCount > 0) artistItem.albumCount else (cached?.albumCount ?: 0),
                        sourceType = _currentService.value
                    )
                } ?: cached
        } catch (e: Exception) {
            cached
        }
    }

    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        // TODO: Connect to MediaPlaybackService
    }

    /**
     * Skip to next song.
     */
    private var lastSkipTime = 0L
    private val SKIP_DEBOUNCE_MS = 400L

    fun skipToNext() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < SKIP_DEBOUNCE_MS) return
        lastSkipTime = currentTime

        val currentIndex = _queue.value.indexOf(_currentSong.value)
        if (currentIndex >= 0 && currentIndex < _queue.value.size - 1) {
            playSong(_queue.value[currentIndex + 1])
        }
    }

    /**
     * Skip to previous song.
     */
    fun skipToPrevious() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < SKIP_DEBOUNCE_MS) return
        lastSkipTime = currentTime

        val currentIndex = _queue.value.indexOf(_currentSong.value)
        if (currentIndex > 0) {
            playSong(_queue.value[currentIndex - 1])
        }
    }

    /**
     * Seek to a position.
     */
    fun seekTo(progress: Float) {
        _progress.value = progress
        seekProgressHandler?.invoke(progress)
    }

    fun seekTo(positionMs: Long, autoPlayIfPaused: Boolean = false) {
        seekPositionHandler?.invoke(positionMs)
        if (autoPlayIfPaused && _isPlaying.value != true) {
            togglePlayPause()
        }
    }

    /**
     * Like/save a song.
     */
    fun likeSong(song: StreamingSong) {
        viewModelScope.launch {
            try {
                repository.likeSong(song.id)
                _likedSongs.value = repository.getLikedSongs().first()
                notificationManager.notifyLikeSong(getSourceTypeName(_currentService.value))
            } catch (e: Exception) {
                _error.value = "Failed to save song: ${e.message}"
            }
        }
    }

    /**
     * Unlike/unsave a song.
     */
    fun unlikeSong(song: StreamingSong) {
        viewModelScope.launch {
            try {
                repository.unlikeSong(song.id)
                _likedSongs.value = repository.getLikedSongs().first()
                notificationManager.notifyUnlikeSong(getSourceTypeName(_currentService.value))
            } catch (e: Exception) {
                _error.value = "Failed to remove song: ${e.message}"
            }
        }
    }

    /**
     * Like a song by its ID. Looks up the song from known library content.
     */
    fun likeSongById(songId: String) {
        viewModelScope.launch {
            try {
                repository.likeSong(songId)
                _likedSongs.value = repository.getLikedSongs().first()
                notificationManager.notifyLikeSong(getSourceTypeName(_currentService.value))
            } catch (e: Exception) {
                _error.value = "Failed to save song: ${e.message}"
            }
        }
    }

    /**
     * Download a song for offline playback.
     */
    fun downloadSong(song: StreamingSong) {
        viewModelScope.launch {
            if (_downloadingSongIds.value.contains(song.id)) return@launch
            _downloadingSongIds.value = _downloadingSongIds.value + song.id
            try {
                val success = repository.downloadSong(song)
                if (success) {
                    val downloaded = repository.getDownloadedSongs().first()
                    _downloadedSongs.value = downloaded
                    _downloadedAlbums.value = deriveAlbumsFromSongs(downloaded, limit = 500)
                    _downloadedArtists.value = deriveArtistsFromSongs(downloaded, limit = 500)
                    if (!_isAuthenticated.value || appSettings.offlineMode.value || !NetworkUtils.isNetworkAvailable(getApplication())) {
                        switchToDownloadedMode()
                    }
                } else {
                    _error.value = "Failed to download ${song.title}"
                }
            } catch (e: Exception) {
                _error.value = "Download failed: ${e.message}"
            } finally {
                _downloadingSongIds.value = _downloadingSongIds.value - song.id
            }
        }
    }

    /**
     * Download a song by ID for offline playback.
     */
    fun downloadSongById(songId: String) {
        val song = _allSongs.value.firstOrNull { it.id == songId }
            ?: _likedSongs.value.firstOrNull { it.id == songId }
            ?: _recommendations.value.firstOrNull { it.id == songId }
            ?: _queue.value.firstOrNull { it.id == songId }
        if (song != null) {
            downloadSong(song)
        } else {
            viewModelScope.launch {
                if (_downloadingSongIds.value.contains(songId)) return@launch
                _downloadingSongIds.value = _downloadingSongIds.value + songId
                try {
                    val success = repository.downloadSong(songId)
                    if (success) {
                        val downloaded = repository.getDownloadedSongs().first()
                        _downloadedSongs.value = downloaded
                        _downloadedAlbums.value = deriveAlbumsFromSongs(downloaded, limit = 500)
                        _downloadedArtists.value = deriveArtistsFromSongs(downloaded, limit = 500)
                        if (!_isAuthenticated.value || appSettings.offlineMode.value || !NetworkUtils.isNetworkAvailable(getApplication())) {
                            switchToDownloadedMode()
                        }
                    }
                } catch (e: Exception) {
                    _error.value = "Download failed: ${e.message}"
                } finally {
                    _downloadingSongIds.value = _downloadingSongIds.value - songId
                }
            }
        }
    }

    /**
     * Remove a downloaded song.
     */
    fun removeDownload(songId: String) {
        viewModelScope.launch {
            try {
                val success = repository.removeDownload(songId)
                if (success) {
                    val downloaded = repository.getDownloadedSongs().first()
                    _downloadedSongs.value = downloaded
                    _downloadedAlbums.value = deriveAlbumsFromSongs(downloaded, limit = 500)
                    _downloadedArtists.value = deriveArtistsFromSongs(downloaded, limit = 500)
                    if (!_isAuthenticated.value || appSettings.offlineMode.value || !NetworkUtils.isNetworkAvailable(getApplication())) {
                        switchToDownloadedMode()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to remove download: ${e.message}"
            }
        }
    }

    /**
     * Download multiple songs for offline playback (batch).
     */
    fun downloadSongs(songs: List<StreamingSong>) {
        viewModelScope.launch {
            songs.forEach { song ->
                if (!_downloadingSongIds.value.contains(song.id) && !isSongDownloaded(song.id)) {
                    downloadSong(song)
                }
            }
        }
    }

    /**
     * Check if a song is downloaded locally.
     */
    fun isSongDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.id == songId }
    }

    /**
     * Check if a song is currently downloading.
     */
    fun isSongDownloading(songId: String): Boolean {
        return _downloadingSongIds.value.contains(songId)
    }

    /**
     * Create a new playlist.
     */
    fun createPlaylist(name: String, songsToAdd: List<StreamingSong> = emptyList(), onCreated: ((StreamingPlaylist) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val newPlaylist = repository.createPlaylist(name)
                if (newPlaylist != null) {
                    if (songsToAdd.isNotEmpty()) {
                        repository.addSongsToPlaylist(newPlaylist.id, songsToAdd.map { it.id })
                    }
                    val playlistsList = repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
                    _savedPlaylists.value = playlistsList
                    notificationManager.notifyPlaylistCreated(name, getSourceTypeName(_currentService.value))
                    
                    val updatedPlaylist = playlistsList.firstOrNull { it.id == newPlaylist.id } ?: newPlaylist
                    onCreated?.invoke(updatedPlaylist)
                } else {
                    _error.value = "Failed to create playlist: received null playlist from repository"
                }
            } catch (e: Exception) {
                _error.value = "Failed to create playlist: ${e.message}"
            }
        }
    }

    /**
     * Rename a playlist on the streaming service.
     */
    fun renamePlaylist(playlist: StreamingPlaylist, newName: String) {
        if (newName.isBlank() || playlist.name == newName) return

        viewModelScope.launch {
            try {
                val success = repository.renamePlaylist(playlist.id, newName)
                if (success) {
                    _savedPlaylists.value = repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
                    notificationManager.notifyPlaylistUpdated(newName, getSourceTypeName(_currentService.value))
                } else {
                    _error.value = "Failed to rename playlist"
                }
            } catch (e: Exception) {
                _error.value = "Failed to rename playlist: ${e.message}"
            }
        }
    }

    /**
     * Add a song to a playlist.
     */
    fun addSongToPlaylist(playlistId: String, song: StreamingSong) {
        viewModelScope.launch {
            try {
                repository.addSongsToPlaylist(playlistId, listOf(song.id))
                _savedPlaylists.value = repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
            } catch (e: Exception) {
                _error.value = "Failed to add song to playlist: ${e.message}"
            }
        }
    }

    /**
     * Add multiple songs to a playlist.
     */
    fun addSongsToPlaylist(playlistId: String, songs: List<StreamingSong>) {
        if (songs.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.addSongsToPlaylist(playlistId, songs.map { it.id })
                _savedPlaylists.value = repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
            } catch (e: Exception) {
                _error.value = "Failed to add songs to playlist: ${e.message}"
            }
        }
    }

    /**
     * Remove a song from a playlist.
     */
    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            try {
                repository.removeSongsFromPlaylist(playlistId, listOf(songId))
                _savedPlaylists.value = repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
            } catch (e: Exception) {
                _error.value = "Failed to remove song from playlist: ${e.message}"
            }
        }
    }

    /**
     * Unfollow/delete a playlist.
     */
    fun unfollowPlaylist(playlist: StreamingPlaylist) {
        deletePlaylist(playlist)
    }

    /**
     * Delete a playlist on the streaming service.
     */
    fun deletePlaylist(playlist: StreamingPlaylist, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val success = repository.deletePlaylist(playlist.id)
                if (success) {
                    _savedPlaylists.value = repository.getPlaylists().first().filterIsInstance<StreamingPlaylist>()
                    notificationManager.notifyPlaylistDeleted(playlist.name, getSourceTypeName(_currentService.value))
                } else {
                    _error.value = "Failed to remove playlist"
                }
                onComplete(success)
            } catch (e: Exception) {
                _error.value = "Failed to remove playlist: ${e.message}"
                onComplete(false)
            }
        }
    }

    /**
     * Follow an artist.
     */
    fun followArtist(artist: StreamingArtist) {
        viewModelScope.launch {
            try {
                repository.followArtist(artist.id)
                _followedArtists.value = repository.getFollowedArtists().first()
            } catch (e: Exception) {
                _error.value = "Failed to follow artist: ${e.message}"
            }
        }
    }

    /**
     * Save an album.
     */
    fun saveAlbum(album: StreamingAlbum) {
        // Album logic intentionally disabled for streaming mode cleanup.
    }
    
    /**
     * Set streaming quality.
     */
    fun setStreamingQuality(quality: StreamingQuality) {
        viewModelScope.launch {
            _streamingConfig.value = _streamingConfig.value.copy(streamingQuality = quality)
            appSettings.setStreamingQuality(quality.name)
            refreshCurrentSession()
            refreshCurrentPlaybackQueue()
        }
    }

    private fun refreshCurrentPlaybackQueue() {
        viewModelScope.launch {
            val currentQueue = _queue.value
            if (currentQueue.isEmpty()) {
                return@launch
            }

            if (!checkAndSyncAuthentication()) {
                return@launch
            }

            // Preserve current playback position and playing state so we can re-seek
            val savedProgress = _progress.value
            val savedDuration = _duration.value
            val savedPositionMs = if (savedDuration > 0L) {
                (savedProgress.coerceIn(0f, 1f) * savedDuration).toLong()
            } else {
                0L
            }
            val wasPlaying = _isPlaying.value

            val refreshedQueue = currentQueue.map { song ->
                val resolvedUrl = repository.getStreamingUrl(song.id)
                    ?: song.streamingUrl
                    ?: song.previewUrl

                if (resolvedUrl.isNullOrBlank()) {
                    song
                } else {
                    song.copy(streamingUrl = resolvedUrl)
                }
            }

            val currentSongId = _currentSong.value?.id
            val currentIndex = refreshedQueue.indexOfFirst { it.id == currentSongId }
                .takeIf { it >= 0 }
                ?: 0

            _queue.value = refreshedQueue
            _currentSong.value = refreshedQueue[currentIndex]

            // Tell the playback handler to re-prepare the queue at the same index
            playbackHandler?.invoke(refreshedQueue, currentIndex)

            // Attempt to restore playback position immediately after re-preparing
            // The handler should be ready to accept a seek command; call the seek handler
            // which wiring normally forwards into the active player controller.
            if (savedPositionMs > 0L) {
                seekPositionHandler?.invoke(savedPositionMs)
            }

            // If playback was active before the change, ensure playing state is preserved
            if (wasPlaying) {
                _isPlaying.value = true
            }
        }
    }
    
    /**
     * Clear all loaded content.
     */
    private fun clearContent() {
        _recommendations.value = emptyList()
        _newReleases.value = emptyList()
        _featuredPlaylists.value = emptyList()
        _browseCategories.value = emptyList()
        _topCharts.value = emptyList()
        _likedSongs.value = emptyList()
        _savedAlbums.value = emptyList()
        _followedArtists.value = emptyList()
        _savedPlaylists.value = emptyList()
        _queue.value = emptyList()
        _currentSong.value = null
    }
    
    /**
     * Clear the currently playing song state.
     */
    fun clearCurrentSong() {
        _currentSong.value = null
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    fun deriveAlbumsFromSongs(
        songs: List<StreamingSong>,
        limit: Int
    ): List<StreamingAlbum> {
        if (songs.isEmpty()) {
            return emptyList()
        }

        return songs
            .filter { it.album.isNotBlank() }
            .groupBy { song ->
                // Prefer provider album ID (albumId) for dedup; fall back to title/artist key
                song.albumId?.takeIf { it.isNotBlank() }
                    ?: "derived:${song.sourceType.name}:album:${song.artist.lowercase()}:${song.album.lowercase()}"
            }
            .values
            .sortedByDescending { albumSongs -> albumSongs.size }
            .take(limit.coerceAtLeast(1))
            .map { albumSongs ->
                val firstSong = albumSongs.first()
                val providerId = firstSong.albumId?.takeIf { it.isNotBlank() }
                val derivedKey = "derived:${firstSong.sourceType.name}:album:${firstSong.artist.lowercase()}:${firstSong.album.lowercase()}"
                val albumArt = albumSongs.firstOrNull { it.artworkUri?.startsWith("file:") == true }?.artworkUri
                    ?: albumSongs.firstNotNullOfOrNull { it.artworkUri }
                StreamingAlbum(
                    id = providerId ?: derivedKey,
                    title = firstSong.album,
                    artist = firstSong.artist,
                    artworkUri = albumArt,
                    songCount = albumSongs.size,
                    year = firstSong.releaseDate?.take(4)?.toIntOrNull(),
                    sourceType = firstSong.sourceType,
                    tracks = albumSongs
                )
            }
    }

    fun deriveArtistsFromSongs(
        songs: List<StreamingSong>,
        limit: Int
    ): List<StreamingArtist> {
        if (songs.isEmpty()) {
            return emptyList()
        }

        val separatorEnabled = appSettings.artistSeparatorEnabled.value
        val separatorDelimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { AppSettings.DEFAULT_ARTIST_SEPARATOR_DELIMITERS }

        return songs
            .filter { it.artist.isNotBlank() }
            .flatMap { song ->
                val artistNames = ArtistSeparator.splitArtistNames(
                    song.artist,
                    delimiters = separatorDelimiters,
                    enabled = separatorEnabled
                )

                if (artistNames.isEmpty()) {
                    listOf(song to song.artist.trim())
                } else {
                    artistNames.mapNotNull { artistName ->
                        artistName.trim().takeIf { it.isNotBlank() }?.let { trimmedName ->
                            song to trimmedName
                        }
                    }
                }
            }
            .groupBy { (song, artistName) -> "${song.sourceType.name}:${artistName.lowercase()}" }
            .values
            .sortedByDescending { artistSongs -> artistSongs.size }
            .take(limit.coerceAtLeast(1))
            .map { artistSongs ->
                val firstSong = artistSongs.first().first
                val artistName = artistSongs.first().second
                val artistTracks = artistSongs.map { it.first }
                val artistAlbums = deriveAlbumsFromSongs(artistTracks, limit = 8)
                val artistArt = artistTracks.firstOrNull { it.artworkUri?.startsWith("file:") == true }?.artworkUri
                    ?: artistTracks.firstNotNullOfOrNull { it.artworkUri }
                    ?: artistAlbums.firstNotNullOfOrNull { it.artworkUri }
                StreamingArtist(
                    id = "derived:${firstSong.sourceType.name}:artist:${artistName.lowercase()}",
                    name = artistName,
                    artworkUri = artistArt,
                    songCount = artistTracks.size,
                    albumCount = artistAlbums.size,
                    sourceType = firstSong.sourceType,
                    topTracks = artistTracks.take(20),
                    albums = artistAlbums
                )
            }
    }

    suspend fun checkAndSyncAuthentication(
        serviceId: String = appSettings.streamingService.value,
        retries: Int = AUTH_PING_RETRIES,
        forceCheck: Boolean = false
    ): Boolean = authMutex.withLock {
        val normalizedServiceId = normalizeServiceId(serviceId)
        val sessionMarkedConnected = serviceSessionRepository.isConnected(normalizedServiceId)
        val credentialsExist = providerRepository?.isServiceConnected(normalizedServiceId)
            ?: sessionMarkedConnected

        if (!credentialsExist) {
            _isAuthenticated.value = false
            _streamingConfig.value = _streamingConfig.value.copy(
                activeService = sourceTypeFromServiceId(normalizedServiceId),
                isAuthenticated = false
            )
            if (sessionMarkedConnected) {
                _error.value = getApplication<Application>().getString(
                    R.string.streaming_home_connect_selected_service,
                    getSourceTypeName(sourceTypeFromServiceId(normalizedServiceId))
                )
            }
            switchToDownloadedMode()
            return@withLock false
        }

        val isNetworkAvail = NetworkUtils.isNetworkAvailable(getApplication())
        if (!isNetworkAvail) {
            _isAuthenticated.value = false
            _streamingConfig.value = _streamingConfig.value.copy(
                activeService = sourceTypeFromServiceId(normalizedServiceId),
                isAuthenticated = false
            )
            updateOnlineStatus(false)
            _error.value = getApplication<Application>().getString(
                R.string.streaming_home_connect_selected_service,
                getSourceTypeName(sourceTypeFromServiceId(normalizedServiceId))
            )
            switchToDownloadedMode()
            return@withLock false
        }

        // If offline mode is enabled, trust saved credentials without pinging
        if (appSettings.offlineMode.value) {
            _isAuthenticated.value = true
            _streamingConfig.value = _streamingConfig.value.copy(
                activeService = sourceTypeFromServiceId(normalizedServiceId),
                isAuthenticated = true
            )
            return@withLock true
        }

        // Cache hit: If already authenticated and checked recently (< 15 seconds ago), avoid ping stampede
        val now = System.currentTimeMillis()
        if (!forceCheck && _isAuthenticated.value && (now - lastSuccessfulAuthTimestamp) < 15_000L) {
            return@withLock true
        }

        val wasAlreadyAuthenticated = _isAuthenticated.value
        var connected = false

        for (attempt in 0 until retries) {
            val authJob = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                try {
                    when (normalizedServiceId) {
                        "SUBSONIC" -> providerRepository?.authenticate() == true
                        "JELLYFIN" -> providerRepository?.authenticate() == true
                        else -> false
                    }
                } catch (e: Exception) {
                    Log.w("StreamingMusicViewModel", "Auth ping attempt $attempt failed", e)
                    false
                }
            }
            if (authJob == true) {
                connected = true
                break
            }
            if (attempt < retries - 1) {
                delay(AUTH_PING_RETRY_DELAY_MS)
            }
        }

        if (connected) {
            lastSuccessfulAuthTimestamp = System.currentTimeMillis()
            _isAuthenticated.value = true
            _streamingConfig.value = _streamingConfig.value.copy(
                activeService = sourceTypeFromServiceId(normalizedServiceId),
                isAuthenticated = true
            )
            updateOnlineStatus(true)
            _error.value = null
            return@withLock true
        }

        // Resiliency Guard: If network is available and session was already active,
        // do not wipe the library or trigger false offline flaps due to a slow/busy server response
        if (wasAlreadyAuthenticated && NetworkUtils.isNetworkAvailable(getApplication())) {
            Log.w("StreamingMusicViewModel", "Auth ping timed out while network is available; maintaining active session")
            return@withLock true
        }

        _isAuthenticated.value = false
        _streamingConfig.value = _streamingConfig.value.copy(
            activeService = sourceTypeFromServiceId(normalizedServiceId),
            isAuthenticated = false
        )
        updateOnlineStatus(false)
        _error.value = getApplication<Application>().getString(
            R.string.streaming_home_connect_selected_service,
            getSourceTypeName(sourceTypeFromServiceId(normalizedServiceId))
        )
        switchToDownloadedMode()
        return@withLock false
    }

    private fun validateCredentials(
        serviceId: String,
        serverUrl: String,
        username: String,
        password: String
    ) {
        if (StreamingServiceRules.requiresServerUrl(serviceId) && serverUrl.isBlank()) {
            throw IllegalArgumentException("Server URL is required")
        }
        val requiresUsername = true
        if (requiresUsername && username.isBlank()) {
            throw IllegalArgumentException("Username is required")
        }
        if (password.isBlank()) {
            throw IllegalArgumentException("Password is required")
        }
    }

    private fun sourceTypeFromServiceId(serviceId: String): SourceType {
        return when (serviceId.uppercase()) {
            StreamingServiceId.SUBSONIC -> SourceType.SUBSONIC
            StreamingServiceId.JELLYFIN -> SourceType.JELLYFIN
            else -> SourceType.UNKNOWN
        }
    }

    private fun serviceIdFromSourceType(sourceType: SourceType): String {
        return when (sourceType) {
            SourceType.SUBSONIC -> StreamingServiceId.SUBSONIC
            SourceType.JELLYFIN -> StreamingServiceId.JELLYFIN
            SourceType.SPOTIFY,
            SourceType.APPLE_MUSIC,
            SourceType.YOUTUBE_MUSIC,
            SourceType.DEEZER,
            SourceType.LOCAL,
            SourceType.UNKNOWN -> StreamingServiceId.SUBSONIC
        }
    }

    private fun normalizeServiceId(serviceId: String): String {
        val normalized = serviceId.uppercase()
        return if (StreamingServiceId.all.contains(normalized)) {
            normalized
        } else {
            StreamingServiceId.SUBSONIC
        }
    }
    
    /**
     * Get display name for SourceType
     */
    fun getSourceTypeName(sourceType: SourceType): String {
        return when (sourceType) {
            SourceType.SUBSONIC -> "Subsonic"
            SourceType.JELLYFIN -> "Jellyfin"
            SourceType.SPOTIFY -> "Spotify"
            SourceType.APPLE_MUSIC -> "Apple Music"
            SourceType.YOUTUBE_MUSIC -> "YouTube Music"
            SourceType.DEEZER -> "Deezer"
            SourceType.LOCAL -> "Local"
            SourceType.UNKNOWN -> "Unknown"
        }
    }

    private fun artistIdMatchesSongArtist(artistId: String, songArtist: String): Boolean {
        val normalizedArtist = songArtist.trim().lowercase()
        if (normalizedArtist.isBlank()) {
            return false
        }

        // Normalize the artist name the same way it's done in buildArtistId:
        // lowercase, then replace spaces with underscores
        val normalizedIdFormat = normalizedArtist.replace("\\s+".toRegex(), "_")
        val normalizedId = artistId.lowercase()
        
        return normalizedId.contains(normalizedIdFormat) ||
            normalizedId.contains(normalizedArtist.replace(" ", "_")) ||
            normalizedId.contains(normalizedArtist.replace(" ", "-"))
    }

    /**
     * Play the streaming song next in the active local playback queue.
     */
    fun playNext(song: StreamingSong, localViewModel: MusicViewModel) {
        viewModelScope.launch {
            try {
                val isDownloadedSong = isSongDownloaded(song.id) || repository.isDownloaded(song.id)
                if (!isDownloadedSong && !checkAndSyncAuthentication()) {
                    _error.value = "Connect to a streaming service first"
                    return@launch
                }
                val resolvedUrl = repository.getStreamingUrl(song.id)
                    ?: song.streamingUrl
                    ?: song.previewUrl

                val updatedSong = if (resolvedUrl.isNullOrBlank()) {
                    song
                } else {
                    song.copy(streamingUrl = resolvedUrl)
                }

                if (updatedSong.streamingUrl.isNullOrBlank()) {
                    _error.value = "Unable to resolve stream URL for this song"
                    android.widget.Toast.makeText(getApplication(), R.string.streamingmusicviewmodel_failed_to_play_next, android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Sync VM queue state flow representation if needed
                val currentQueue = _queue.value.toMutableList()
                val currentSongId = _currentSong.value?.id
                val currentIndex = currentQueue.indexOfFirst { it.id == currentSongId }
                val insertIndex = if (currentIndex >= 0) currentIndex + 1 else 0
                if (insertIndex in 0..currentQueue.size) {
                    currentQueue.add(insertIndex, updatedSong)
                } else {
                    currentQueue.add(updatedSong)
                }
                _queue.value = currentQueue

                // Delegate to localViewModel
                val localSong = updatedSong.toLocalSong()
                localViewModel.playNext(localSong)
            } catch (e: Exception) {
                android.util.Log.e("StreamingMusicViewModel", "Error in playNext for streaming song", e)
                _error.value = "Failed to play next: ${e.message}"
            }
        }
    }

    /**
     * Add the streaming song to the end of the active local playback queue.
     */
    fun addSongToQueue(song: StreamingSong, localViewModel: MusicViewModel) {
        viewModelScope.launch {
            try {
                val isDownloadedSong = isSongDownloaded(song.id) || repository.isDownloaded(song.id)
                if (!isDownloadedSong && !checkAndSyncAuthentication()) {
                    _error.value = "Connect to a streaming service first"
                    return@launch
                }
                val resolvedUrl = repository.getStreamingUrl(song.id)
                    ?: song.streamingUrl
                    ?: song.previewUrl

                val updatedSong = if (resolvedUrl.isNullOrBlank()) {
                    song
                } else {
                    song.copy(streamingUrl = resolvedUrl)
                }

                if (updatedSong.streamingUrl.isNullOrBlank()) {
                    _error.value = "Unable to resolve stream URL for this song"
                    android.widget.Toast.makeText(getApplication(), R.string.streamingmusicviewmodel_failed_to_add_to, android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Sync VM queue state flow representation if needed
                val currentQueue = _queue.value.toMutableList()
                currentQueue.add(updatedSong)
                _queue.value = currentQueue

                // Delegate to localViewModel
                val localSong = updatedSong.toLocalSong()
                localViewModel.addSongToQueue(localSong)
            } catch (e: Exception) {
                android.util.Log.e("StreamingMusicViewModel", "Error in addSongToQueue for streaming song", e)
                _error.value = "Failed to add to queue: ${e.message}"
            }
        }
    }

    private fun StreamingSong.toLocalSong(): Song {
        val playbackUri = when {
            !streamingUrl.isNullOrBlank() -> (streamingUrl).toUri()
            !previewUrl.isNullOrBlank() -> (previewUrl).toUri()
            else -> ("streaming://track/$id").toUri()
        }

        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId.orEmpty(),
            duration = duration,
            uri = playbackUri,
            artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
            albumArtist = albumArtist
        )
    }
}

/**
 * Container for streaming search results.
 */
data class StreamingSearchResults(
    val songs: List<StreamingSong> = emptyList(),
    val albums: List<StreamingAlbum> = emptyList(),
    val artists: List<StreamingArtist> = emptyList(),
    val playlists: List<StreamingPlaylist> = emptyList()
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
    
    val totalCount: Int
        get() = songs.size + albums.size + artists.size + playlists.size
}

/**
 * Stages of library synchronization for streaming services.
 */
enum class StreamingSyncStage {
    Idle,
    Syncing,
    Complete,
    Error
}

/**
 * Live library sync progress state.
 */
data class StreamingSyncProgress(
    val isSyncing: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val songsCount: Int = 0,
    val stage: StreamingSyncStage = StreamingSyncStage.Idle
)
