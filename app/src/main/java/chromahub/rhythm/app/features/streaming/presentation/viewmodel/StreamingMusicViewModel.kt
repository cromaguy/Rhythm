package chromahub.rhythm.app.features.streaming.presentation.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.net.Uri
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.R
import android.util.Log

/**
 * ViewModel for managing streaming music playback and library.
 * Handles authentication, browsing, and playback for streaming services.
 */
class StreamingMusicViewModel(application: Application) : AndroidViewModel(application) {
    private val appSettings = AppSettings.getInstance(application)
    private val context: Context = application
    private val serviceSessionRepository = StreamingServiceSessionRepository(application)
    val repository = StreamingMusicModule.provideStreamingMusicRepository(application)
    private val providerRepository = repository as? StreamingMusicRepositoryImpl
    private val notificationManager = StreamingNotificationManager(application)
    private var playbackHandler: ((List<StreamingSong>, Int) -> Unit)? = null
    private var seekProgressHandler: ((Float) -> Unit)? = null
    private var seekPositionHandler: ((Long) -> Unit)? = null

    
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
        // Keep an updated view of the provider catalog exposed by the repository
        viewModelScope.launch {
            repository.getSongs().collect { items ->
                _allSongs.value = items.filterIsInstance<StreamingSong>()
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
                if (connected) {
                    loadHomeContent()
                } else {
                    // Keep the user's explicit provider selection even if disconnected.
                    // Auto-reverting to another connected provider makes provider switching
                    // appear to do nothing from the Go settings popup flow.
                    clearContent()
                }
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
                
                // Show success notification
                notificationManager.notifyAuthenticationSuccess(getSourceTypeName(sourceTypeFromServiceId(normalizedServiceId)))
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.message}"
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
                    clearContent()
                    return@launch
                }
                
                // Check network constraints
                if (!NetworkUtils.canStream(context, appSettings.allowCellularStreaming.value)) {
                    clearContent()
                    _error.value = "Streaming not allowed on current network"
                    return@launch
                }
                
                if (appSettings.offlineMode.value) {
                    clearContent()
                    _error.value = "Content loading unavailable in offline mode"
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
                // Check if error is due to stale connection (timeout, connection error)
                val isConnectionError = e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true ||
                    e.message?.contains("socket", ignoreCase = true) == true ||
                    e.cause is java.net.SocketException ||
                    e.cause is java.net.ConnectException ||
                    e is java.io.IOException
                
                if (isConnectionError) {
                    // Attempt automatic reconnection by refreshing authentication
                    _error.value = "Connection lost. Attempting to reconnect..."
                    delay(1000) // Wait before attempting reconnection
                    val serviceId = appSettings.streamingService.value
                    if (checkAndSyncAuthentication(serviceId)) {
                        // Retry loading content after reconnection
                        _error.value = null
                        loadHomeContent()
                    } else {
                        _error.value = "Failed to reconnect to streaming service. Please try reconnecting manually."
                        clearContent()
                    }
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
     * Refresh home screen content.
     */
    fun refreshHome() {
        loadHomeContent()
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
            
            try {
                if (!checkAndSyncAuthentication()) {
                    _likedSongs.value = emptyList()
                    _savedAlbums.value = emptyList()
                    _followedArtists.value = emptyList()
                    _savedPlaylists.value = emptyList()
                    _downloadedSongs.value = emptyList()
                    return@launch
                }

                // Pull the provider catalog first so artist/album counts come from actual songs.
                try {
                    repository.syncCatalog(limit = 5_000)
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
                    val separatorDelimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { "/;,+&" }
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
                    repository.searchArtists("")
                        .filterIsInstance<StreamingArtist>()
                        .distinctBy { it.id }
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

                if (_featuredPlaylists.value.isEmpty()) {
                    _featuredPlaylists.value = resolvedPlaylists
                }
            } catch (e: Exception) {
                _error.value = "Failed to load library: ${e.message}"
            } finally {
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
                if (!NetworkUtils.canStream(context, appSettings.allowCellularStreaming.value)) {
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
                val albumsFromRepository = emptyList<StreamingAlbum>()
                val playlists = repository.searchPlaylists(query).filterIsInstance<StreamingPlaylist>()


                val derivedAlbums = emptyList<StreamingAlbum>()

                _searchResults.value = StreamingSearchResults(
                    songs = songs,
                    albums = if (albumsFromRepository.isNotEmpty()) albumsFromRepository else derivedAlbums,
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

        playQueue(queueSource, startIndex = selectedIndex, shuffle = false)
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
                if (!checkAndSyncAuthentication()) {
                    _error.value = "Connect to a streaming service first"
                    return@launch
                }

            val safeStartIndex = startIndex.coerceIn(0, playableQueue.lastIndex)
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
                    !NetworkUtils.canStream(context, appSettings.allowCellularStreaming.value) -> "Streaming not allowed on current network"
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
        val songs = repository.getAlbumSongs(album.id)
        if (songs.isNotEmpty()) {
            return songs
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
        val cachedArtist = (_followedArtists.value + _searchResults.value.artists)
            .distinctBy { it.id }
            .firstOrNull { it.id == artistId }

        val embeddedTracks = cachedArtist
            ?.getTopTracks()
            .orEmpty()
            .filter { it.isPlayable }
            .distinctBy { it.id }
        if (embeddedTracks.isNotEmpty()) {
            return embeddedTracks.take(safeLimit)
        }

        val repositoryTracks = repository.getArtistTopTracks(artistId, safeLimit)
            .filter { it.isPlayable }
            .distinctBy { it.id }
        if (repositoryTracks.isNotEmpty()) {
            return repositoryTracks
        }

        val normalizedHint = artistNameHint?.trim().orEmpty()
        
        // Fallback: match songs by artist name
        val allAvailableSongs = _likedSongs.value +
            _downloadedSongs.value +
            _recommendations.value +
            _searchResults.value.songs +
            _queue.value
        
        return allAvailableSongs
            .asSequence()
            .filter {
                when {
                    normalizedHint.isNotBlank() -> it.artist.equals(normalizedHint, ignoreCase = true)
                    cachedArtist != null -> it.artist.equals(cachedArtist.name, ignoreCase = true)
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
        val resolvedArtistId = if (!artistNameHint.isNullOrBlank()) {
            val serviceName = artistId.substringBefore("::", _currentService.value.name)
            repository.buildArtistId(serviceName, artistNameHint)
        } else {
            artistId
        }
        return repository.getArtistAlbums(resolvedArtistId)
    }

    /**
     * Fetch full artist info (including artwork) from the repository.
     * Returns null if the artist cannot be resolved.
     */
    suspend fun getArtistInfo(
        artistId: String,
        artistNameHint: String? = null
    ): StreamingArtist? {
        // Check memory caches first
        val cached = (_followedArtists.value + _searchResults.value.artists)
            .distinctBy { it.id }
            .firstOrNull { it.id == artistId }
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
                        artworkUri = artistItem.artworkUri,
                        songCount = artistItem.songCount,
                        albumCount = artistItem.albumCount,
                        sourceType = _currentService.value
                    )
                }
        } catch (e: Exception) {
            null
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

    fun seekTo(positionMs: Long) {
        seekPositionHandler?.invoke(positionMs)
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
     * Download a song for offline playback.
     */
    fun downloadSong(song: StreamingSong) {
        viewModelScope.launch {
            try {
                repository.downloadSong(song.id)
                _downloadedSongs.value = repository.getDownloadedSongs().first()
            } catch (e: Exception) {
                _error.value = "Download failed: ${e.message}"
            }
        }
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
        _downloadedSongs.value = emptyList()
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

    private fun deriveAlbumsFromSongs(
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
                StreamingAlbum(
                    id = providerId ?: derivedKey,
                    title = firstSong.album,
                    artist = firstSong.artist,
                    artworkUri = albumSongs.firstNotNullOfOrNull { it.artworkUri },
                    songCount = albumSongs.size,
                    year = firstSong.releaseDate?.take(4)?.toIntOrNull(),
                    sourceType = firstSong.sourceType,
                    tracks = albumSongs
                )
            }
    }

    private suspend fun deriveArtistsFromSongs(
        songs: List<StreamingSong>,
        limit: Int
    ): List<StreamingArtist> {
        if (songs.isEmpty()) {
            return emptyList()
        }

        val separatorEnabled = appSettings.artistSeparatorEnabled.value
        val separatorDelimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { "/;,+&" }

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
                StreamingArtist(
                    id = "derived:${firstSong.sourceType.name}:artist:${artistName.lowercase()}",
                    name = artistName,
                    artworkUri = null,
                    songCount = artistTracks.size,
                    albumCount = artistAlbums.size,
                    sourceType = firstSong.sourceType,
                    topTracks = artistTracks.take(20),
                    albums = artistAlbums
                )
            }
            .let { derivedArtists ->
                providerRepository?.enrichArtistsWithDeezerImages(derivedArtists) ?: derivedArtists
            }
    }

    private suspend fun checkAndSyncAuthentication(
        serviceId: String = appSettings.streamingService.value
    ): Boolean {
        val normalizedServiceId = normalizeServiceId(serviceId)
        val credentialsExist = providerRepository?.isServiceConnected(normalizedServiceId)
            ?: serviceSessionRepository.isConnected(normalizedServiceId)

        if (!credentialsExist) {
            _isAuthenticated.value = false
            _streamingConfig.value = _streamingConfig.value.copy(
                activeService = sourceTypeFromServiceId(normalizedServiceId),
                isAuthenticated = false
            )
            return false
        }

        // If offline mode is enabled, trust saved credentials without pinging
        if (appSettings.offlineMode.value) {
            _isAuthenticated.value = true
            _streamingConfig.value = _streamingConfig.value.copy(
                activeService = sourceTypeFromServiceId(normalizedServiceId),
                isAuthenticated = true
            )
            return true
        }

        // Actually test the connection with a ping to detect stale connections
        val connected = try {
            when (normalizedServiceId) {
                "SUBSONIC" -> providerRepository?.authenticate() == true
                "JELLYFIN" -> providerRepository?.authenticate() == true
                else -> false
            }
        } catch (e: Exception) {
            // Network error - connection is stale or network unavailable
            false
        }

        _isAuthenticated.value = connected
        _streamingConfig.value = _streamingConfig.value.copy(
            activeService = sourceTypeFromServiceId(normalizedServiceId),
            isAuthenticated = connected
        )
        return connected
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
    private fun getSourceTypeName(sourceType: SourceType): String {
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
                if (!checkAndSyncAuthentication()) {
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
                    android.widget.Toast.makeText(context, R.string.streamingmusicviewmodel_failed_to_play_next, android.widget.Toast.LENGTH_SHORT).show()
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
                if (!checkAndSyncAuthentication()) {
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
                    android.widget.Toast.makeText(context, R.string.streamingmusicviewmodel_failed_to_add_to, android.widget.Toast.LENGTH_SHORT).show()
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
            !streamingUrl.isNullOrBlank() -> Uri.parse(streamingUrl)
            !previewUrl.isNullOrBlank() -> Uri.parse(previewUrl)
            else -> Uri.parse("streaming://track/$id")
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
