package chromahub.rhythm.app.features.local.presentation.navigation

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import chromahub.rhythm.app.ui.theme.MusicDimensions
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongPickerBottomSheet
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.ui.UiConstants
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.R
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.UpdateBottomSheet
import chromahub.rhythm.app.features.local.presentation.screens.AddToPlaylistScreen
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.QueueActionDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.QueueListActionDialog
import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayer
import chromahub.rhythm.app.shared.presentation.components.player.RhythmPlayerSheet
import chromahub.rhythm.app.shared.presentation.components.player.SleepTimerBottomSheetNew
import chromahub.rhythm.app.features.local.presentation.screens.LibraryScreen
import chromahub.rhythm.app.features.local.presentation.screens.HomeScreen
import chromahub.rhythm.app.shared.presentation.screens.RhythmStatsScreen
import chromahub.rhythm.app.features.local.presentation.screens.EqualizerScreen
import chromahub.rhythm.app.shared.presentation.screens.player.PlayerScreen

import chromahub.rhythm.app.features.local.presentation.screens.PlaylistDetailScreen
import chromahub.rhythm.app.features.local.presentation.screens.ArtistDetailScreen
import chromahub.rhythm.app.features.local.presentation.screens.AlbumDetailScreen
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsScreenWrapper
import chromahub.rhythm.app.shared.presentation.screens.settings.*
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.shared.data.model.findAlbumForSong
import chromahub.rhythm.app.shared.presentation.components.MediaScanLoader // Add MediaScanLoader import
import chromahub.rhythm.app.util.ArtistSeparator
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel.SortOrder
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.rememberAppUpdaterViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.EaseInBack
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.features.local.presentation.screens.LibraryTab
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState // Redundant, but ensuring it's there
import androidx.compose.material3.OutlinedTextField // Redundant, but ensuring it's there
import androidx.compose.foundation.shape.CircleShape // Redundant, but ensuring it's there
import androidx.compose.ui.text.style.TextAlign // Redundant, but ensuring it's there
import androidx.compose.ui.res.stringResource

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library?tab={tab}") {
        fun createRoute(tab: LibraryTab = LibraryTab.SONGS): String = "library?tab=${tab.name.lowercase()}"
    }
    object Player : Screen("player")
    object Settings : Screen("settings")
    object AddToPlaylist : Screen("add_to_playlist")
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/${Uri.encode(artistName)}"
    }
    object AlbumDetail : Screen("album/{albumId}?albumName={albumName}") {
        fun createRoute(albumId: String, albumName: String) = "album/${Uri.encode(albumId)}?albumName=${Uri.encode(albumName)}"
    }
    
    // Tuner Settings Subroutes
    object TunerNotifications : Screen("tuner_notifications_settings")
    object TunerExperimentalFeatures : Screen("tuner_experimental_features_settings")
    object TunerAbout : Screen("tuner_about_screen")
    object TunerUpdates : Screen("tuner_updates_screen")
    object TunerMediaScan : Screen("tuner_media_scan_settings")
    object TunerPlaylists : Screen("tuner_playlist_settings")
    object TunerApiManagement : Screen("tuner_api_management_settings")
    object TunerCacheManagement : Screen("tuner_cache_management_settings")
    object TunerBackupRestore : Screen("tuner_backup_restore_settings")
    object TunerLibraryTabOrder : Screen("tuner_library_tab_order_settings")
    object TunerThemeCustomization : Screen("tuner_theme_customization_settings")
    object TunerEqualizer : Screen("tuner_equalizer_settings")
    object TunerSleepTimer : Screen("tuner_sleep_timer_settings")
    object TunerCrashLogHistory : Screen("tuner_crash_log_history_settings")
    object TunerQueue : Screen("tuner_queue_settings")
    object TunerPlayback : Screen("tuner_playback_settings")
    object TunerHomeScreen : Screen("tuner_home_screen_settings")
    object TunerExpressiveShapes : Screen("tuner_expressive_shapes_settings")
    object TunerPlayerCustomization : Screen("tuner_player_customization_settings")
    object TunerMiniPlayerCustomization : Screen("tuner_miniplayer_customization_settings")
    object TunerBatterySaver : Screen("tuner_battery_saver_settings")
    object TunerLibrarySettings : Screen("tuner_library_settings")
    object TunerRhythmGuard : Screen("tuner_rhythm_guard_settings")
    object TunerLyrics : Screen("tuner_lyrics_settings")
    object TunerGestures : Screen("tuner_gestures_settings")
    object TunerWidget : Screen("tuner_widget_settings")
    object TunerArtistSeparators : Screen("tuner_artist_separators_settings")
    object TunerGoSettings : Screen("tuner_go_settings")
    
    // Stats Screen
    object RhythmStats : Screen("rhythm_stats")
    object Equalizer : Screen("equalizer")
}

@Composable
private fun AnimateIn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 50),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer(
            alpha = alpha,
            scaleX = scale,
            scaleY = scale
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun LocalNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: MusicViewModel = viewModel(),
    themeViewModel: ThemeViewModel = viewModel(),
    appSettings: chromahub.rhythm.app.shared.data.model.AppSettings // Add appSettings parameter
) {
    val miniPlayerThemeId by appSettings.miniPlayerThemeId.collectAsState()
    // Update monitoring
    val updaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel()
    val updateAvailable by updaterViewModel.updateAvailable.collectAsState()
    val updatesEnabled by appSettings.updatesEnabled.collectAsState()
    val latestVersion by updaterViewModel.latestVersion.collectAsState()
    var showUpdateBottomSheet by remember { mutableStateOf(false) }
    
    // Track the version we've shown to avoid re-showing for the same version
    var lastShownVersion by remember { mutableStateOf<String?>(null) }
    
    // Monitor for updates and show bottom sheet automatically
    LaunchedEffect(updateAvailable, updatesEnabled, latestVersion) {
        if (updateAvailable && updatesEnabled) {
            val currentVersion = latestVersion?.versionName
            // Show if we haven't shown this version yet, or if the version changed
            if (currentVersion != null && currentVersion != lastShownVersion) {
                showUpdateBottomSheet = true
                lastShownVersion = currentVersion
            }
        } else {
            // Reset when update is no longer available
            lastShownVersion = null
        }
    }
    // Collect state from ViewModel
    val songs by viewModel.filteredSongs.collectAsState() // Use filtered songs to exclude blacklisted ones
    val allSongs by viewModel.songs.collectAsState() // Keep all songs for specific cases
    val albums by viewModel.filteredAlbums.collectAsState() // Use filtered albums to exclude albums with all songs blacklisted
    val artists by viewModel.filteredArtists.collectAsState() // Use filtered artists to exclude artists with all songs blacklisted
    val playlists by viewModel.playlists.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val showLyrics by viewModel.showLyrics.collectAsState()
    val showOnlineOnlyLyrics by viewModel.showOnlineOnlyLyrics.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val useExperimentalPlayerUi by appSettings.useExperimentalPlayerUi.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val isMediaScanning by viewModel.isMediaScanning.collectAsState() // Add media scanning state

    // Theme state
    val useSystemTheme by themeViewModel.useSystemTheme.collectAsState()
    val darkMode by themeViewModel.darkMode.collectAsState()
    
    // Library tab order settings - used to determine first visible tab
    val libraryTabOrder by appSettings.libraryTabOrder.collectAsState()
    val hiddenLibraryTabs by appSettings.hiddenLibraryTabs.collectAsState()
    
    // Compute the first visible tab based on user's tab order (respecting hidden tabs)
    val firstVisibleLibraryTab = remember(libraryTabOrder, hiddenLibraryTabs) {
        val firstVisibleId = libraryTabOrder.firstOrNull { !hiddenLibraryTabs.contains(it) }
        when (firstVisibleId) {
            "SONGS" -> LibraryTab.SONGS
            "PLAYLISTS" -> LibraryTab.PLAYLISTS
            "ALBUMS" -> LibraryTab.ALBUMS
            "ARTISTS" -> LibraryTab.ARTISTS
            "EXPLORER" -> LibraryTab.EXPLORER
            else -> LibraryTab.SONGS // Default fallback
        }
    }
    
    // Default landing screen
    val defaultScreen by appSettings.defaultScreen.collectAsState()
    val startDestination = when (defaultScreen) {
        "library" -> Screen.Library.createRoute(firstVisibleLibraryTab)
        else -> Screen.Home.route
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val isTablet = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    val onPlayPause = { viewModel.togglePlayPause() }
    val onSkipNext = { viewModel.skipToNext() }
    val onSkipPrevious = { viewModel.skipToPrevious() }
    val onSeek = { value: Float -> viewModel.seekTo(value) }
    val onLyricsSeek: (Long) -> Unit = { timestampMs ->
        // Use the timestamp-based seekTo method directly for lyrics
        Log.d("RhythmNavigation", "Lyrics seek: timestampMs=$timestampMs")
        viewModel.seekTo(timestampMs)
    }
    val onPlaySong = { song: chromahub.rhythm.app.shared.data.model.Song -> viewModel.playSong(song) }
    val onPlayAlbum = { album: chromahub.rhythm.app.shared.data.model.Album -> viewModel.playAlbum(album) }
    val onPlayAlbumShuffled = { album: chromahub.rhythm.app.shared.data.model.Album -> viewModel.playAlbumShuffled(album) }
    val onPlayArtist = { artist: chromahub.rhythm.app.shared.data.model.Artist -> viewModel.playArtist(artist) }
    val onPlayPlaylist =
        { playlist: chromahub.rhythm.app.shared.data.model.Playlist -> viewModel.playPlaylist(playlist) }
    val onPlayPlaylistShuffled = { playlist: chromahub.rhythm.app.shared.data.model.Playlist -> viewModel.playPlaylistShuffled(playlist) }
    val onToggleShuffle = { viewModel.toggleShuffle() }
    val onToggleRepeat = { viewModel.toggleRepeatMode() }
    val onToggleFavorite = { viewModel.toggleFavorite() }

    // Player click navigates to full player screen
    val onPlayerClick = {
        navController.navigate(Screen.Player.route)
    }

    // Track current destination for hiding navigation bar on player screen
    var currentRoute by remember { mutableStateOf(Screen.Home.route) }

    // Update current route when destination changes
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            currentRoute = backStackEntry.destination.route ?: Screen.Home.route
            val routeBase = currentRoute.substringBefore("?")
            // Update selectedTab based on current route
            when {
                currentRoute == Screen.Home.route -> selectedTab = 0
                routeBase == Screen.Library.route.substringBefore("?") -> selectedTab = 1
            }
        }
    }

    // Consume pending startup route from shared settings handoff and navigate if valid.
    LaunchedEffect(navController, appSettings) {
        val pendingRoute = appSettings.consumeInitialStreamingRoute()
        if (!pendingRoute.isNullOrBlank()) {
            val isValidLocalRoute = pendingRoute == Screen.Home.route ||
                pendingRoute == Screen.Search.route ||
                pendingRoute == Screen.Player.route ||
                pendingRoute == Screen.Settings.route ||
                pendingRoute == Screen.RhythmStats.route ||
                pendingRoute.startsWith(Screen.Library.route.substringBefore("?")) ||
                pendingRoute.startsWith("playlist/") ||
                pendingRoute.startsWith("artist/")

            if (isValidLocalRoute) {
                navController.navigate(pendingRoute) {
                    launchSingleTop = true
                }
            }
        }
    }

    val isLibraryRoute = remember(currentRoute) {
        currentRoute.substringBefore("?") == Screen.Library.route.substringBefore("?")
    }

    var isMiniPlayerDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentSong?.id, isPlaying) {
        if (currentSong != null && isPlaying) {
            isMiniPlayerDismissed = false
        }
    }

    // Provide dynamic mini-player padding with comprehensive navigation handling
    val showMiniPlayer =
        currentSong != null &&
            !isMiniPlayerDismissed &&
            currentRoute != Screen.Player.route &&
            currentRoute != Screen.Search.route
    val showNavBar = remember(currentRoute) {
        currentRoute == Screen.Home.route ||
            isLibraryRoute ||
            currentRoute == Screen.Search.route ||
            currentRoute == Screen.Settings.route ||
            currentRoute == Screen.RhythmStats.route
    }
    val showBottomNav = remember(currentRoute) {
        currentRoute == Screen.Home.route || isLibraryRoute
    }
    
    val systemNavBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Calculate content bottom padding based on visible UI elements
    // System insets are handled separately via windowInsetsPadding on the bottomBar
    val miniPlayerBottomPadding by animateDpAsState(
        targetValue = if (!isTablet) {
            val miniPlayerHeight = if (miniPlayerThemeId == "EXPRESSIVE") 84.dp else 96.dp
            val bottomNavigationHeight = MusicDimensions.bottomNavigationHeight
            val basePadding = when {
                showBottomNav && showMiniPlayer -> bottomNavigationHeight + 16.dp + miniPlayerHeight + 16.dp
                showBottomNav -> bottomNavigationHeight + 16.dp
                showMiniPlayer -> miniPlayerHeight + 16.dp
                else -> 0.dp
            }
            basePadding + systemNavBarPadding
        } else {
            if (showMiniPlayer) {
                val miniPlayerHeight = if (miniPlayerThemeId == "EXPRESSIVE") 84.dp else 96.dp
                miniPlayerHeight + 16.dp + systemNavBarPadding
            } else {
                0.dp
            }
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "local_miniplayer_bottom_padding"
    )
    val miniPlayerPaddingValues = PaddingValues(bottom = miniPlayerBottomPadding.coerceAtLeast(0.dp))

    val tabletContentStartPadding by animateDpAsState(
        targetValue = if (showNavBar) 96.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "local_tablet_content_start_padding"
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalMiniPlayerPadding provides miniPlayerPaddingValues) {
        if (isTablet) {
            // Tablet layout with NavigationRail
            Box(modifier = Modifier.fillMaxSize()) {
                // Navigation rail for tablets
                AnimatedVisibility(
                    visible = showNavBar,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                ) {
                    LocalNavigationRail(
                        currentRoute = currentRoute,
                        navController = navController,
                        firstVisibleLibraryTab = firstVisibleLibraryTab,
                        context = context,
                        haptic = haptic
                    )
                }
                
                // Main content wrapped in Scaffold (without bottom nav)
                LocalNavigationContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = tabletContentStartPadding),
                    navController = navController,
                    viewModel = viewModel,
                    themeViewModel = themeViewModel,
                    appSettings = appSettings,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope,
                    currentSong = currentSong,
                    currentRoute = currentRoute,
                    isPlaying = isPlaying,
                    progress = { progress },
                    onPlayPause = onPlayPause,
                    onPlayerClick = onPlayerClick,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onMiniPlayerDismiss = {
                        isMiniPlayerDismissed = true
                        if (isPlaying) {
                            onPlayPause()
                        }
                        viewModel.clearQueue()
                    },
                    showMiniPlayer = showMiniPlayer,
                    showBottomNav = false, // Hide nav bar in content for tablet
                    isTablet = true,
                    startDestination = startDestination,
                    songs = songs,
                    allSongs = allSongs,
                    albums = albums,
                    artists = artists,
                    playlists = playlists,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    sortOrder = sortOrder,
                    showLyrics = showLyrics,
                    showOnlineOnlyLyrics = showOnlineOnlyLyrics,
                    lyrics = lyrics,
                    isLoadingLyrics = isLoadingLyrics,
                    recentlyPlayed = recentlyPlayed,
                    currentDevice = currentDevice,
                    isMediaScanning = isMediaScanning,
                    useSystemTheme = useSystemTheme,
                    darkMode = darkMode,
                    useExperimentalPlayerUi = useExperimentalPlayerUi,
                    libraryTabOrder = libraryTabOrder,
                    hiddenLibraryTabs = hiddenLibraryTabs,
                    firstVisibleLibraryTab = firstVisibleLibraryTab,
                    onPlaySong = onPlaySong,
                    onPlayAlbum = onPlayAlbum,
                    onPlayAlbumShuffled = onPlayAlbumShuffled,
                    onPlayArtist = onPlayArtist,
                    onPlayPlaylist = onPlayPlaylist,
                    onPlayPlaylistShuffled = onPlayPlaylistShuffled,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onToggleFavorite = onToggleFavorite,
                    onSeek = onSeek,
                    onLyricsSeek = onLyricsSeek
                )
            }
        } else {
            // Phone layout with original bottom navigation
            LocalNavigationContent(
                navController = navController,
                viewModel = viewModel,
                themeViewModel = themeViewModel,
                appSettings = appSettings,
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope,
                currentSong = currentSong,
                currentRoute = currentRoute,
                isPlaying = isPlaying,
                progress = { progress },
                onPlayPause = onPlayPause,
                onPlayerClick = onPlayerClick,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                    onMiniPlayerDismiss = {
                        isMiniPlayerDismissed = true
                        if (isPlaying) {
                            onPlayPause()
                        }
                        viewModel.clearQueue()
                    },
                showMiniPlayer = showMiniPlayer,
                showBottomNav = showBottomNav,
                isTablet = false,
                startDestination = startDestination,
                songs = songs,
                allSongs = allSongs,
                albums = albums,
                artists = artists,
                playlists = playlists,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isFavorite = isFavorite,
                sortOrder = sortOrder,
                showLyrics = showLyrics,
                showOnlineOnlyLyrics = showOnlineOnlyLyrics,
                lyrics = lyrics,
                isLoadingLyrics = isLoadingLyrics,
                recentlyPlayed = recentlyPlayed,
                currentDevice = currentDevice,
                isMediaScanning = isMediaScanning,
                useSystemTheme = useSystemTheme,
                darkMode = darkMode,
                    useExperimentalPlayerUi = useExperimentalPlayerUi,
                libraryTabOrder = libraryTabOrder,
                hiddenLibraryTabs = hiddenLibraryTabs,
                firstVisibleLibraryTab = firstVisibleLibraryTab,
                onPlaySong = onPlaySong,
                onPlayAlbum = onPlayAlbum,
                onPlayAlbumShuffled = onPlayAlbumShuffled,
                onPlayArtist = onPlayArtist,
                onPlayPlaylist = onPlayPlaylist,
                onPlayPlaylistShuffled = onPlayPlaylistShuffled,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onToggleFavorite = onToggleFavorite,
                onSeek = onSeek,
                onLyricsSeek = onLyricsSeek
            )
        }
    }
    
    // Global Update Bottom Sheet - shows automatically when update is available
    val isOnUpdatesScreen = currentRoute == Screen.TunerUpdates.route
    if (showUpdateBottomSheet && !isOnUpdatesScreen) {
        UpdateBottomSheet(
            updaterViewModel = updaterViewModel,
            onDismiss = { showUpdateBottomSheet = false },
            onUpdateClick = { immediate ->
                showUpdateBottomSheet = false
                // Navigate to update settings
                navController.navigate(Screen.TunerUpdates.route) {
                    launchSingleTop = true
                }
            }
        )
    }
}

@Composable
private fun LocalNavigationContent(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: MusicViewModel,
    themeViewModel: ThemeViewModel,
    appSettings: chromahub.rhythm.app.shared.data.model.AppSettings,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    currentSong: chromahub.rhythm.app.shared.data.model.Song?,
    currentRoute: String,
    isPlaying: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onMiniPlayerDismiss: () -> Unit,
    showMiniPlayer: Boolean,
    showBottomNav: Boolean,
    isTablet: Boolean,
    startDestination: String,
    songs: List<chromahub.rhythm.app.shared.data.model.Song>,
    allSongs: List<chromahub.rhythm.app.shared.data.model.Song>,
    albums: List<chromahub.rhythm.app.shared.data.model.Album>,
    artists: List<chromahub.rhythm.app.shared.data.model.Artist>,
    playlists: List<chromahub.rhythm.app.shared.data.model.Playlist>,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    sortOrder: MusicViewModel.SortOrder,
    showLyrics: Boolean,
    showOnlineOnlyLyrics: Boolean,
    lyrics: chromahub.rhythm.app.shared.data.model.LyricsData?,
    isLoadingLyrics: Boolean,
    recentlyPlayed: List<chromahub.rhythm.app.shared.data.model.Song>,
    currentDevice: PlaybackLocation?,
    isMediaScanning: Boolean,
    useSystemTheme: Boolean,
    darkMode: Boolean,
    useExperimentalPlayerUi: Boolean,
    libraryTabOrder: List<String>,
    hiddenLibraryTabs: Set<String>,
    firstVisibleLibraryTab: LibraryTab,
    onPlaySong: (chromahub.rhythm.app.shared.data.model.Song) -> Unit,
    onPlayAlbum: (chromahub.rhythm.app.shared.data.model.Album) -> Unit,
    onPlayAlbumShuffled: (chromahub.rhythm.app.shared.data.model.Album) -> Unit,
    onPlayArtist: (chromahub.rhythm.app.shared.data.model.Artist) -> Unit,
    onPlayPlaylist: (chromahub.rhythm.app.shared.data.model.Playlist) -> Unit,
    onPlayPlaylistShuffled: (chromahub.rhythm.app.shared.data.model.Playlist) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeek: (Float) -> Unit,
    onLyricsSeek: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val showAddToPlaylistSheet = remember { mutableStateOf(false) }
    val showCreatePlaylistDialog = remember { mutableStateOf(false) }
    val songForCreatePlaylistDialog = remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }

    LaunchedEffect(viewModel.selectedSongForPlaylist.collectAsState().value) {
        if (viewModel.selectedSongForPlaylist.value != null) {
            showAddToPlaylistSheet.value = true
        }
    }

    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isLibraryRoute = remember(currentRoute) {
        currentRoute.substringBefore("?") == Screen.Library.route.substringBefore("?")
    }
    val isLibraryStartDestination = remember(startDestination) {
        startDestination.substringBefore("?") == Screen.Library.route.substringBefore("?")
    }

    BackHandler(enabled = isLibraryRoute && !isLibraryStartDestination) {
        navigateToTopLevel(Screen.Home.route)
    }

    val navigateBackOrToLanding: () -> Unit = {
        val popped = navController.popBackStack()
        if (!popped) {
            navController.navigate(startDestination) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val navigateToLanding: () -> Unit = {
        navController.navigate(startDestination) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val navigateBackOrToSettings: () -> Unit = {
        val popped = navController.popBackStack()
        if (!popped) {
            navigateToTopLevel(Screen.Settings.route)
        }
    }


    val miniPlayerThemeId by appSettings.miniPlayerThemeId.collectAsState()

    Scaffold(
        modifier = modifier,
        snackbarHost = {},
        bottomBar = {
            val bottomChromeVisible = showMiniPlayer || showBottomNav
            val bottomChromeAlpha by animateFloatAsState(
                targetValue = if (bottomChromeVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 220),
                label = "local_bottom_chrome_alpha"
            )
            val miniPlayerBottomOffset by animateDpAsState(
                targetValue = when {
                    showBottomNav -> MusicDimensions.bottomNavigationHeight + 12.dp
                    currentRoute == Screen.Search.route -> 88.dp // Height of search bar + padding
                    else -> 8.dp
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "local_miniplayer_bottom_offset"
            )

            val miniPlayerHeight = if (miniPlayerThemeId == "EXPRESSIVE") 84.dp else 110.dp
            val gradientHeight by animateDpAsState(
                targetValue = when {
                    showBottomNav && showMiniPlayer -> MusicDimensions.bottomNavigationHeight + 16.dp + miniPlayerHeight + 32.dp
                    showBottomNav -> MusicDimensions.bottomNavigationHeight + 32.dp
                    showMiniPlayer -> miniPlayerHeight + 32.dp
                    else -> 0.dp
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "local_navigation_gradient_height"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (gradientHeight > 0.dp && !isTablet) {
                    val systemNavBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gradientHeight + systemNavBarPadding)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = bottomChromeAlpha }
                            .background(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.25f to MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                                        0.62f to MaterialTheme.colorScheme.background.copy(alpha = 0.76f),
                                        1f to MaterialTheme.colorScheme.background.copy(alpha = 1f)
                                    )
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .align(Alignment.BottomCenter)
                ) {

                // Global RhythmPlayerSheet
                val showPlayerSheet = currentSong != null && (showMiniPlayer || currentRoute == Screen.Player.route)
                if (showPlayerSheet) {
                    val bottomBarHeightPx = with(LocalDensity.current) { MusicDimensions.bottomNavigationHeight.roundToPx() }
                    RhythmPlayerSheet(
                        isExpanded = currentRoute == Screen.Player.route,
                        onExpand = {
                            navController.navigate(Screen.Player.route)
                        },
                        onCollapse = {
                            navigateBackOrToLanding()
                        },
                        onMiniPlayerDismiss = {
                            onMiniPlayerDismiss()
                        },
                        song = currentSong,
                        isPlaying = isPlaying,
                        progress = progress,
                        location = currentDevice,
                        queuePosition = viewModel.currentQueue.collectAsState().value.currentIndex + 1,
                        queueTotal = viewModel.currentQueue.collectAsState().value.songs.size,
                        onPlayPause = onPlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeek = { position ->
                            viewModel.seekTo(position)
                        },
                        onLyricsSeek = onLyricsSeek,
                        onLocationClick = {
                            viewModel.showOutputSwitcherDialog()
                        },
                        onQueueClick = {
                            // Handled internally by PlayerScreen
                        },
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onAddToPlaylist = { showAddToPlaylistSheet.value = true },
                        showLyrics = showLyrics,
                        onlineOnlyLyrics = showOnlineOnlyLyrics,
                        lyrics = lyrics,
                        isLoadingLyrics = isLoadingLyrics,
                        onRetryLyrics = {
                            viewModel.retryFetchLyrics()
                        },
                        volume = viewModel.volume.collectAsState().value,
                        isMuted = viewModel.isMuted.collectAsState().value,
                        onVolumeChange = { volume ->
                            viewModel.setVolume(volume)
                        },
                        onToggleMute = {
                            viewModel.toggleMute()
                        },
                        onRefreshDevices = {
                            viewModel.startDeviceMonitoringOnDemand()
                        },
                        onStopDeviceMonitoring = {
                            viewModel.stopDeviceMonitoringOnDemand()
                        },
                        locations = viewModel.locations.collectAsState().value,
                        onLocationSelect = { location ->
                            viewModel.setCurrentDevice(location)
                        },
                        onMaxVolume = {
                            viewModel.maxVolume()
                        },
                        playlists = playlists,
                        queue = viewModel.currentQueue.collectAsState().value.songs,
                        onSongClick = { song ->
                            viewModel.playSong(song)
                        },
                        onSongClickAtIndex = { index ->
                            viewModel.playSongAtIndex(index)
                        },
                        onRemoveFromQueueAtIndex = { index ->
                            viewModel.removeFromQueueAtIndex(index)
                        },
                        onMoveQueueItem = { fromIndex, toIndex ->
                            viewModel.moveQueueItem(fromIndex, toIndex)
                        },
                        onAddSongsToQueue = {
                            viewModel.addSongsToQueue()
                        },
                        onNavigateToLibrary = { tab ->
                            navController.navigate(Screen.Library.createRoute(tab)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        showAddToPlaylistSheet = showAddToPlaylistSheet.value,
                        onAddToPlaylistSheetDismiss = {
                            showAddToPlaylistSheet.value = false
                            viewModel.clearSelectedSongForPlaylist()
                        },
                        onAddSongToPlaylist = { song, playlistId ->
                            viewModel.addSongToPlaylist(song, playlistId) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCreatePlaylist = { name ->
                            viewModel.createPlaylist(name)
                        },
                        onShowCreatePlaylistDialog = { song ->
                            songForCreatePlaylistDialog.value = song
                            showCreatePlaylistDialog.value = true
                        },
                        onClearQueue = {
                            viewModel.clearQueue()
                        },
                        isMediaLoading = viewModel.isBuffering.collectAsState().value,
                        isSeeking = viewModel.isSeeking.collectAsState().value,
                        songs = viewModel.filteredSongs.collectAsState().value,
                        albums = viewModel.albums.collectAsState().value,
                        artists = viewModel.artists.collectAsState().value,
                        onPlayAlbumSongs = { songs -> viewModel.playSongs(songs) },
                        onShuffleAlbumSongs = { songs -> viewModel.playShuffled(songs) },
                        onPlayArtistSongs = { songs -> viewModel.playSongs(songs) },
                        onShuffleArtistSongs = { songs -> viewModel.playShuffled(songs) },
                        appSettings = appSettings,
                        musicViewModel = viewModel,
                        navController = navController,
                        miniPlayerBottomOffset = miniPlayerBottomOffset
                    )
                }

                // Navigation bar shown only on specific routes with spring animation
                AnimatedVisibility(
                    visible = showBottomNav,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .align(Alignment.BottomCenter),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Expressive Navigation bar Surface with pill shape
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = ExpressiveShapes.Full, // Full pill shape for expressive design
                                tonalElevation = 3.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .height(MusicDimensions.bottomNavigationHeight)
                                    .weight(1f) // Make it take up available space
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Use first visible library tab based on user's tab order
                                    val libraryRoute =
                                        Screen.Library.createRoute(firstVisibleLibraryTab)
                                    val items = listOf(
                                         Triple(
                                             Screen.Home.route, context.getString(R.string.common_home),
                                             Pair(RhythmIcons.HomeFilled, RhythmIcons.Home)
                                         ),
                                         Triple(
                                             libraryRoute, context.getString(R.string.common_library),
                                             Pair(RhythmIcons.Navigation.Library, RhythmIcons.Navigation.LibraryOutlined)
                                         )
                                     )

                                     items.forEachIndexed { index, (route, title, icons) ->
                                         val isSelected = when (route) {
                                             Screen.Home.route -> currentRoute == Screen.Home.route
                                             libraryRoute -> currentRoute.startsWith("library")
                                             else -> false
                                         }

                                        val (selectedIcon, unselectedIcon) = icons

                                        // Enhanced animation values with spring physics
                                        val animatedScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.05f else 1.0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            label = "scale_$title"
                                        )

                                        val animatedAlpha by animateFloatAsState(
                                            targetValue = if (isSelected) 1f else 0.7f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            label = "alpha_$title"
                                        )

                                        // Background pill animation with spring
                                        val pillWidth by animateDpAsState(
                                            targetValue = if (isSelected) 120.dp else 0.dp,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            label = "pillWidth_$title"
                                        )

                                        // Icon color animation
                                        val iconColor by animateColorAsState(
                                            targetValue = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            animationSpec = tween(300),
                                            label = "iconColor_$title"
                                        )

                                        val haptic = LocalHapticFeedback.current

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable {
                                                    HapticUtils.performHapticFeedback(
                                                        context,
                                                        haptic,
                                                        HapticType.HEAVY
                                                    )
                                                    navController.navigate(route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Horizontal layout for icon and text with animated pill background
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        scaleX = animatedScale
                                                        scaleY = animatedScale
                                                        alpha = animatedAlpha
                                                    }
                                                    .then(
                                                        if (isSelected) Modifier
                                                            .clip(ExpressiveShapes.Full) // Expressive pill shape
                                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                                            .height(48.dp)
                                                            .widthIn(min = pillWidth) // Animated width
                                                            .padding(horizontal = 18.dp)
                                                        else Modifier.padding(horizontal = 16.dp)
                                                    )
                                            ) {
                                                // Animated icon with crossfade
                                                androidx.compose.animation.Crossfade(
                                                    targetState = isSelected,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessVeryLow
                                                    ),
                                                    label = "iconCrossfade_$title"
                                                ) { selected ->
                                                    Icon(
                                                        imageVector = if (selected) selectedIcon else unselectedIcon,
                                                        contentDescription = title,
                                                        tint = iconColor,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                AnimatedVisibility(
                                                    visible = isSelected,
                                                    enter = fadeIn(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    ) + expandHorizontally(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    ),
                                                    exit = fadeOut(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    ) + shrinkHorizontally(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                ) {
                                                    Row {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = title,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = iconColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp)) // Gap between nav bar and search icon

                            // Expressive Search Icon Button with bouncy animation
                            val searchInteractionSource = remember { MutableInteractionSource() }
                            val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
                            val searchScale by animateFloatAsState(
                                targetValue = if (isSearchPressed) 0.88f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "search_scale"
                            )
                            
                            FilledIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    navController.navigate(Screen.Search.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = ExpressiveShapes.Full,
                                interactionSource = searchInteractionSource,
                                modifier = Modifier
                                    .size(MusicDimensions.bottomNavigationHeight) // Match height of navigation bar
                                    .graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    }
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Search,
                                    contentDescription = stringResource(R.string.cd_search),
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                composable(
                    route = Screen.Home.route,
                    enterTransition = {
                        when {
                            initialState.destination.route?.startsWith("library") == true -> {
                                // Horizontal slide animation when coming from Library
                                fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Default animation for other sources
                                fadeIn(animationSpec = tween(300))
                            }
                        }
                    },
                    exitTransition = {
                        when {
                            targetState.destination.route?.startsWith("library") == true -> {
                                // Horizontal slide animation when going to Library
                                fadeOut(animationSpec = tween(300)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { -it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Default animation for other destinations
                                fadeOut(animationSpec = tween(300))
                            }
                        }
                    },
                    popEnterTransition = {
                        when {
                            initialState.destination.route?.startsWith("library") == true -> {
                                // Restore horizontal slide animation when popping back from Library
                                fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Simple faster fade animation when popping back from other screens
                                fadeIn(animationSpec = tween(200))
                            }
                        }
                    },
                    popExitTransition = {
                        // Simple faster fade animation when being popped from
                        fadeOut(animationSpec = tween(200))
                    }
                ) {
                    HomeScreen(
                        musicViewModel = viewModel,
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        recentlyPlayed = recentlyPlayed,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = onPlaySong,
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                        },
                        onArtistClick = onPlayArtist,
                        onPlayPause = onPlayPause,
                        onPlayerClick = {
                            navController.navigate(Screen.Player.route)
                        },
                        onViewAllSongs = {
                            // Navigate to songs screen
                            navController.navigate(Screen.Library.createRoute(LibraryTab.SONGS)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onViewAllAlbums = {
                            navController.navigate(Screen.Library.createRoute(LibraryTab.ALBUMS)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onViewAllArtists = {
                            navController.navigate(Screen.Library.createRoute(LibraryTab.ARTISTS)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onSkipNext = onSkipNext,
                        onSearchClick = {
                            navigateToTopLevel(Screen.Search.route)
                        },
                        onSettingsClick = {
                            // Navigate to the settings screen
                            navigateToTopLevel(Screen.Settings.route)
                        },
                        onNavigateToLibrary = {
                            // Navigate to library with playlists tab selected
                            navController.navigate(Screen.Library.createRoute(LibraryTab.PLAYLISTS)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToPlaylist = { playlistId ->
                            // Navigate to the specified playlist
                            // For "favorites", we'll use the ID "1" which is the favorites playlist
                            val id = if (playlistId == "favorites") "1" else playlistId
                            navController.navigate(Screen.PlaylistDetail.createRoute(id))
                        },
                        onAddToQueue = { song ->
                            viewModel.addSongToQueue(song)
                        },
                        onAddSongToPlaylist = { song, playlistId ->
                            viewModel.addSongToPlaylist(song, playlistId) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToStats = {
                            navigateToTopLevel(Screen.RhythmStats.route)
                        },
                        onNavigateToRhythmGuard = {
                            navController.navigate(Screen.TunerRhythmGuard.route)
                        },
                        onNavigateToArtist = { artist ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artist.name))
                        }
                    )
                }

                composable(
                    Screen.Search.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) {
                    val streamingViewModel: chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

                    SlideUpCornerWrapper {
                        chromahub.rhythm.app.shared.presentation.screens.UniversalSearchScreen(
                            localViewModel = viewModel,
                            streamingViewModel = streamingViewModel,
                            onLocalSongClick = { song ->
                                viewModel.playSongFromSearch(song, songs)
                                navController.navigate(Screen.Player.route)
                            },
                            onLocalAlbumClick = { album ->
                                navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                            },
                            onLocalArtistClick = { artist -> navController.navigate(Screen.ArtistDetail.createRoute(artist.name)) },
                            onLocalPlaylistClick = { playlist -> navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                            onStreamingSongClick = { song ->
                                streamingViewModel.playSong(song)
                                navController.navigate(Screen.Player.route)
                            },
                            onStreamingAlbumClick = { streamingAlbum ->
                                appSettings.setInitialStreamingRoute("streaming_search")
                            },
                            onStreamingArtistClick = { artist ->
                                appSettings.setInitialStreamingRoute("streaming_artist/${Uri.encode(artist.id)}?artistName=${Uri.encode(artist.name)}")
                            },
                            onStreamingPlaylistClick = { playlist ->
                                appSettings.setInitialStreamingRoute("streaming_playlist/${Uri.encode(playlist.id)}")
                            },
                            onBack = { navigateToLanding() }
                        )
                    }
                }


                composable(
                    Screen.Settings.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) {
                    SlideUpCornerWrapper {
                        // Use the settings screen (now the default)
                        SettingsScreenWrapper(
                            onBack = {
                                navigateBackOrToLanding()
                            },
                            appSettings = appSettings,
                            navController = navController,
                            musicViewModel = viewModel
                        )
                    }
                }
                // Tuner Settings Subroutes
                composable(Screen.TunerNotifications.route) {
                    NotificationsSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerExperimentalFeatures.route) {
                    ExperimentalFeaturesScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerAbout.route) {
                    chromahub.rhythm.app.shared.presentation.screens.settings.AboutScreen(
                        onBackClick = navigateBackOrToSettings,
                        onNavigateToUpdates = {
                            navController.navigate(Screen.TunerUpdates.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(Screen.TunerUpdates.route) {
                    UpdatesSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerMediaScan.route) {
                    MediaScanSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerPlaylists.route) {
                    PlaylistsSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerApiManagement.route) {
                    ApiManagementSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerCacheManagement.route) {
                    CacheManagementSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerBackupRestore.route) {
                    BackupRestoreSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerLibraryTabOrder.route) {
                    LibraryTabOrderSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerThemeCustomization.route) {
                    ThemeCustomizationSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(
                    route = Screen.Equalizer.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) {
                    SlideUpCornerWrapper {
                        EqualizerScreen(navController = navController, viewModel = viewModel)
                    }
                }

                composable(Screen.TunerSleepTimer.route) {
                    // Show sleep timer bottom sheet directly instead of placeholder screen
                    var showBottomSheet by remember { mutableStateOf(true) }
                    
                    if (showBottomSheet) {
                        SleepTimerBottomSheetNew(
                            onDismiss = {
                                showBottomSheet = false
                                navigateBackOrToSettings()
                            },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            musicViewModel = viewModel
                        )
                    } else {
                        // Navigate back if bottom sheet is dismissed without opening
                        LaunchedEffect(Unit) {
                            navigateBackOrToSettings()
                        }
                    }
                }

                composable(Screen.TunerCrashLogHistory.route) {
                    CrashLogHistorySettingsScreen(
                        onBackClick = navigateBackOrToSettings,
                        appSettings = appSettings
                    )
                }

                composable(Screen.TunerQueue.route) {
                    QueueSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerPlayback.route) {
                    PlaybackSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerHomeScreen.route) {
                    HomeScreenCustomizationSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerExpressiveShapes.route) {
                    ExpressiveShapesSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerPlayerCustomization.route) {
                    PlayerCustomizationSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerMiniPlayerCustomization.route) {
                    MiniPlayerCustomizationSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerBatterySaver.route) {
                    PerformanceSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerLibrarySettings.route) {
                    LibrarySettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerRhythmGuard.route) {
                    RhythmGuardSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerLyrics.route) {
                    LyricsSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerGestures.route) {
                    GesturesSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerWidget.route) {
                    WidgetSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerArtistSeparators.route) {
                    ArtistSeparatorsSettingsScreen(onBackClick = navigateBackOrToSettings)
                }

                composable(Screen.TunerGoSettings.route) {
                    chromahub.rhythm.app.features.streaming.presentation.screens.GoSettingsScreen(
                        onBackClick = navigateBackOrToSettings,
                        onConfigureCurrentProvider = { serviceId ->
                            appSettings.setInitialStreamingRoute("streaming_service_setup/$serviceId")
                            appSettings.setAppMode("STREAMING")
                            if (!navController.popBackStack()) {
                                navController.navigate("main") { launchSingleTop = true }
                            }
                        }
                    )
                }

                composable(
                    route = Screen.RhythmStats.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) {
                    SlideUpCornerWrapper {
                        RhythmStatsScreen(navController = navController, viewModel = viewModel)
                    }
                }

                composable(
                    route = Screen.Equalizer.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) {
                    SlideUpCornerWrapper {
                        EqualizerScreen(navController = navController, viewModel = viewModel)
                    }
                }

                composable(
                    route = Screen.Library.route,
                    arguments = listOf(
                        navArgument("tab") {
                            type = NavType.StringType
                            defaultValue = "songs"
                        }
                    ),
                    enterTransition = {
                        when (initialState.destination.route) {
                            Screen.Home.route -> {
                                // Horizontal slide animation when coming from Home
                                fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Default animation for other sources
                                fadeIn(animationSpec = tween(300))
                            }
                        }
                    },
                    exitTransition = {
                        when (targetState.destination.route) {
                            Screen.Home.route -> {
                                // Horizontal slide animation when going to Home
                                fadeOut(animationSpec = tween(300)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Default animation for other destinations
                                fadeOut(animationSpec = tween(300))
                            }
                        }
                    },
                    popEnterTransition = {
                        when (initialState.destination.route) {
                            Screen.Home.route -> {
                                // Restore horizontal slide animation when popping back from Home
                                fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Simple faster fade animation when popping back from other screens
                                fadeIn(animationSpec = tween(200))
                            }
                        }
                    },
                    popExitTransition = {
                        when (targetState.destination.route) {
                            Screen.Home.route -> {
                                // Restore horizontal slide animation when popping back to Home
                                fadeOut(animationSpec = tween(300)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(350, easing = EaseInOutQuart)
                                        )
                            }

                            else -> {
                                // Simple faster fade animation when being popped from for other destinations
                                fadeOut(animationSpec = tween(200))
                            }
                        }
                    }
                ) {
                    val tabArg = it.arguments?.getString("tab") ?: "songs"
                    val initialTab = when (tabArg) {
                        "playlists" -> LibraryTab.PLAYLISTS
                        "albums" -> LibraryTab.ALBUMS
                        "artists" -> LibraryTab.ARTISTS
                        "explorer" -> LibraryTab.EXPLORER
                        else -> LibraryTab.SONGS
                    }

                    LibraryScreen(
                        songs = songs,
                        albums = albums,
                        playlists = playlists,
                        artists = artists,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = onPlaySong,
                        onPlayPause = onPlayPause,
                        onPlayerClick = {
                            navController.navigate(Screen.Player.route)
                        },
                        onPlaylistClick = { playlist ->
                            // Navigate to playlist detail screen
                            navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                        },
                        onAddPlaylist = {
                            // This is now handled internally with the dialog
                        },
                        onAlbumClick = onPlayAlbum,
                        onArtistClick = { artist ->
                            // Handle artist click - could navigate to artist detail or show bottom sheet
                            // For now, we'll handle it within LibraryScreen
                        },
                        onAlbumShufflePlay = onPlayAlbumShuffled,
                        onPlayQueue = { songs ->
                            // Play queue (force replace) for explicit Play All action
                            viewModel.playSongs(songs)
                        },
                        onPlayQueueFromIndex = { songs, startIndex ->
                            // Play queue from specific index (force replace)
                            viewModel.playQueue(songs = songs, enableShuffle = false, startIndex = startIndex)
                        },
                        onShuffleQueue = { songs ->
                            // Shuffle using playShuffled to respect settings
                            viewModel.playShuffled(songs)
                        },
                        onAlbumBottomSheetClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                        },
                        onSort = {
                            // Implement sort functionality
                            viewModel.sortLibrary()
                        },
                        onAddSongToPlaylist = { song, playlistId ->
                            // Add song to playlist
                            viewModel.addSongToPlaylist(song, playlistId) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCreatePlaylist = { name ->
                            viewModel.createPlaylist(name)
                        },
                        onRefreshClick = {
                            viewModel.refreshLibrary(showMediaScanLoader = false)
                        }, // Added onRefreshClick
                        sortOrder = sortOrder,
                        onSkipNext = onSkipNext,
                        onAddToQueue = { song ->
                            // Add song to queue
                            viewModel.addSongToQueue(song)
                        },
                        initialTab = initialTab,
                        musicViewModel = viewModel, // Pass musicViewModel
                        onExportAllPlaylists = { format, includeDefault, userDirectoryUri, resultCallback ->
                            // Export all playlists with optional user-selected directory
                            viewModel.exportAllPlaylists(
                                format,
                                includeDefault,
                                userDirectoryUri
                            ) { result ->
                                result.fold(
                                    onSuccess = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                resultCallback(result)
                            }
                        },
                        onImportPlaylist = { uri, resultCallback, onRestartRequired ->
                            // Import playlist from URI with restart functionality
                            viewModel.importPlaylist(uri, { result ->
                                result.fold(
                                    onSuccess = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                resultCallback(result)
                            }, onRestartRequired = {
                                // Trigger restart dialog or function
                                onRestartRequired?.invoke()
                            })
                        },
                        onRestartApp = {
                            viewModel.restartApp()
                        },
                        onNavigateToArtist = { artist ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artist.name))
                        }
                    )
                }

                composable(
                    route = Screen.Player.route,
                    enterTransition = {
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 200)
                        )
                    },
                    exitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + scaleOut(
                            targetScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 200)
                        )
                    },
                    popExitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + scaleOut(
                            targetScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 200)
                        )
                    },
                    popEnterTransition = {
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 200)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                // Add playlist detail screen
                @OptIn(ExperimentalMaterial3Api::class)
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(
                        navArgument("playlistId") {
                            type = NavType.StringType
                        }
                    ),
                    // Enhanced transitions to match AboutScreen pattern
                    enterTransition = {
                        fadeIn(animationSpec = tween(350)) +
                                scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = tween(400, easing = EaseOutQuint)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(350)) +
                                scaleOut(
                                    targetScale = 0.85f,
                                    animationSpec = tween(300, easing = EaseInOutQuart)
                                )
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(350)) +
                                scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = tween(400, easing = EaseOutQuint)
                                )
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(350)) +
                                scaleOut(
                                    targetScale = 0.85f,
                                    animationSpec = tween(300, easing = EaseInOutQuart)
                                )
                    }
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                    val playlist = playlists.find { it.id == playlistId }
                    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

                    // Album/Artist data for bottom sheets
                    val allAlbums by viewModel.albums.collectAsState()
                    val playlistHaptics = LocalHapticFeedback.current

                    // Write permission launcher for Android 11+ metadata editing
                    val writePermissionLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
                    ) { result: androidx.activity.result.ActivityResult ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            if (viewModel.pendingBatchWriteRequest.value != null) {
                                viewModel.completeBatchMetadataWriteAfterPermission(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                viewModel.completeMetadataWriteAfterPermission(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        } else {
                            if (viewModel.pendingBatchWriteRequest.value != null) {
                                viewModel.cancelPendingBatchMetadataWrite()
                            } else {
                                viewModel.cancelPendingMetadataWrite()
                            }
                            android.widget.Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    if (playlist != null) {
                        PlaylistDetailScreen(
                            musicViewModel = viewModel,
                            playlist = playlist,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onPlayPause = onPlayPause,
                            onPlayerClick = {
                                navController.navigate(Screen.Player.route)
                            },
                            onPlayAll = {
                                onPlayPlaylist(playlist)
                            },
                            onShufflePlay = {
                                // Play shuffled playlist songs using the proper shuffled playlist playback
                                onPlayPlaylistShuffled(playlist)
                            },
                            onSongClick = onPlaySong,
                            onPlaySongFromPlaylist = { song, playlistSongs ->
                                viewModel.playSongFromContext(song, playlistSongs, playlist.name)
                            },
                            onBack = {
                                navigateBackOrToLanding()
                            },
                            onRemoveSong = { song, message ->
                                viewModel.removeSongFromPlaylist(
                                    song,
                                    playlistId
                                ) { snackbarMessage ->
                                    Toast.makeText(context, snackbarMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRenamePlaylist = { newName ->
                                viewModel.renamePlaylist(playlistId, newName)
                            },
                            onDeletePlaylist = {
                                viewModel.deletePlaylist(playlistId)
                                navigateBackOrToLanding()
                            },
                            onAddSongsToPlaylist = {},
                            onSkipNext = onSkipNext,
                            onReorderSongs = { fromIndex, toIndex ->
                                viewModel.reorderPlaylistSongs(playlistId, fromIndex, toIndex)
                            },
                            onUpdatePlaylistSongs = { newSongList ->
                                viewModel.updatePlaylistSongs(playlistId, newSongList)
                            },
                            onPlayNext = { song ->
                                viewModel.playNext(song)
                            },
                            onAddToQueue = { song ->
                                viewModel.addSongToQueue(song)
                            },
                            onGoToAlbum = { song ->
                                val album = allAlbums.findAlbumForSong(song)
                                if (album != null) {
                                    navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                                }
                            },
                            onGoToArtist = { song ->
                                val separatorEnabled = appSettings.artistSeparatorEnabled.value
                                val delimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { "/;,+&" }
                                val candidates = ArtistSeparator.splitArtistNames(
                                    song.artist,
                                    delimiters = delimiters,
                                    enabled = separatorEnabled
                                )
                                val artistRouteName = candidates.firstOrNull { candidate ->
                                    artists.any { it.name.equals(candidate, ignoreCase = true) }
                                } ?: candidates.firstOrNull()?.trim().orEmpty().ifBlank {
                                    song.artist.trim()
                                }

                                if (artistRouteName.isNotBlank()) {
                                    navController.navigate(Screen.ArtistDetail.createRoute(artistRouteName))
                                }
                            },
                            onShare = { song ->
                                try {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${song.title}"))
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                // Artist Detail Screen
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(
                        navArgument("artistName") {
                            type = NavType.StringType
                        }
                    ),
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) { backStackEntry ->
                    val artistName = backStackEntry.arguments?.getString("artistName")?.let { Uri.decode(it) } ?: ""
                    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
                    var pendingMetadataEditCompleteCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
                    
                    // Write permission launcher for Android 11+ metadata editing
                    val writePermissionLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
                    ) { result: ActivityResult ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            if (viewModel.pendingBatchWriteRequest.value != null) {
                                viewModel.completeBatchMetadataWriteAfterPermission(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                viewModel.completeMetadataWriteAfterPermission(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, android.widget.Toast.LENGTH_SHORT).show()
                                        pendingMetadataEditCompleteCallback?.invoke(true)
                                        pendingMetadataEditCompleteCallback = null
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                        pendingMetadataEditCompleteCallback?.invoke(false)
                                        pendingMetadataEditCompleteCallback = null
                                    }
                                )
                            }
                        } else {
                            if (viewModel.pendingBatchWriteRequest.value != null) {
                                viewModel.cancelPendingBatchMetadataWrite()
                            } else {
                                viewModel.cancelPendingMetadataWrite()
                                pendingMetadataEditCompleteCallback?.invoke(false)
                                pendingMetadataEditCompleteCallback = null
                            }
                            android.widget.Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    
                    // State for bottom sheets
                    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
                    var selectedSongForPlaylist by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }
                    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
                    var songForCreatePlaylistDialog by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }
                    var showSongInfoSheet by remember { mutableStateOf(false) }
                    var selectedSongForInfo by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }
                    
                    ArtistDetailScreen(
                        viewModel = viewModel,
                        artistName = artistName,
                        onBack = {
                            navigateBackOrToLanding()
                        },
                        onSongClick = onPlaySong,
                        onAlbumClick = { album ->
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                        },
                        onPlayAll = { songs ->
                            if (songs.isNotEmpty()) {
                                viewModel.playSongs(songs)
                            }
                        },
                        onShufflePlay = { songs ->
                            if (songs.isNotEmpty()) {
                                viewModel.playShuffled(songs)
                            }
                        },
                        onAddToQueue = { song ->
                            viewModel.addSongToQueue(song)
                        },
                        onAddToQueueAll = { songs ->
                            viewModel.addSongsToQueue(songs)
                        },
                        onAddSongToPlaylist = { song ->
                            selectedSongForPlaylist = song
                            showAddToPlaylistSheet = true
                        },
                        onPlayerClick = {
                            navController.navigate(Screen.Player.route)
                        },
                        onPlayNext = { song ->
                            viewModel.playNext(song)
                        },
                        onToggleFavorite = { song ->
                            viewModel.toggleFavorite(song)
                        },
                        favoriteSongs = favoriteSongs,
                        onShowSongInfo = { song ->
                            selectedSongForInfo = song
                            showSongInfoSheet = true
                        },
                        currentSong = currentSong,
                        isPlaying = isPlaying
                    )
                    
                    // Add to playlist bottom sheet
                    if (showAddToPlaylistSheet && selectedSongForPlaylist != null) {
                        AddToPlaylistBottomSheet(
                            song = selectedSongForPlaylist!!,
                            playlists = playlists,
                            onDismissRequest = { 
                                showAddToPlaylistSheet = false
                                selectedSongForPlaylist = null
                            },
                            onAddToPlaylist = { playlist ->
                                viewModel.addSongToPlaylist(selectedSongForPlaylist!!, playlist.id) { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                                showAddToPlaylistSheet = false
                                selectedSongForPlaylist = null
                            },
                            onCreateNewPlaylist = {
                                songForCreatePlaylistDialog = selectedSongForPlaylist
                                showCreatePlaylistDialog = true
                                showAddToPlaylistSheet = false
                            }
                        )
                    }

                    if (showCreatePlaylistDialog) {
                        CreatePlaylistDialog(
                            onDismiss = {
                                showCreatePlaylistDialog = false
                                songForCreatePlaylistDialog = null
                            },
                            onConfirm = { name ->
                                viewModel.createPlaylist(name)
                                showCreatePlaylistDialog = false
                                songForCreatePlaylistDialog = null
                            },
                            song = songForCreatePlaylistDialog,
                            onConfirmWithSong = { name ->
                                if (songForCreatePlaylistDialog != null) {
                                    viewModel.createPlaylist(name, listOf(songForCreatePlaylistDialog!!)) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.createPlaylist(name)
                                }
                                showCreatePlaylistDialog = false
                                songForCreatePlaylistDialog = null
                            }
                        )
                    }
                    
                    // Song info bottom sheet
                    if (showSongInfoSheet && selectedSongForInfo != null) {
                        SongInfoBottomSheet(
                            song = selectedSongForInfo!!,
                            onDismiss = { 
                                showSongInfoSheet = false
                                selectedSongForInfo = null
                            },
                            appSettings = appSettings,
                            onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                                pendingMetadataEditCompleteCallback = onComplete
                                viewModel.saveMetadataChanges(
                                    song = selectedSongForInfo!!,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    genre = genre,
                                    year = year,
                                    trackNumber = trackNumber,
                                    artworkUri = artworkUri,
                                    removeArtwork = removeArtwork,
                                    albumArtist = albumArtist,
                                    composer = composer,
                                    discNumber = discNumber,
                                    onSuccess = { fileWriteSucceeded ->
                                        if (fileWriteSucceeded) {
                                            android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully_to, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        pendingMetadataEditCompleteCallback?.invoke(true)
                                        pendingMetadataEditCompleteCallback = null
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                        pendingMetadataEditCompleteCallback?.invoke(false)
                                        pendingMetadataEditCompleteCallback = null
                                    },
                                    onPermissionRequired = { pendingRequest ->
                                        try {
                                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                                pendingRequest.intentSender
                                            ).build()
                                            writePermissionLauncher.launch(intentSenderRequest)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, R.string.localnavigation_permission_required_to_modify, android.widget.Toast.LENGTH_SHORT).show()
                                            pendingMetadataEditCompleteCallback?.invoke(false)
                                            pendingMetadataEditCompleteCallback = null
                                        }
                                    }
                                )
                            }
                        )
                    }

                }

                // Album Detail Screen
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(
                        navArgument("albumId") { type = NavType.StringType },
                        navArgument("albumName") { type = NavType.StringType }
                    ),
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getString("albumId")?.let { Uri.decode(it) } ?: ""
                    val albumName = backStackEntry.arguments?.getString("albumName")?.let { Uri.decode(it) } ?: ""
                    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

                    // Write permission launcher for Android 11+ metadata editing
                    val writePermissionLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
                    ) { result: androidx.activity.result.ActivityResult ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            if (viewModel.pendingBatchWriteRequest.value != null) {
                                viewModel.completeBatchMetadataWriteAfterPermission(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        } else {
                            viewModel.cancelPendingBatchMetadataWrite()
                            android.widget.Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
                    var selectedSongForPlaylist by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }
                    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
                    var songForCreatePlaylistDialog by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }
                    var showSongInfoSheet by remember { mutableStateOf(false) }
                    var selectedSongForInfo by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.Song?>(null) }

                    AlbumDetailScreen(
                        albumId = albumId,
                        albumName = albumName,
                        onBack = {
                            navigateBackOrToLanding()
                        },
                        onSongClick = onPlaySong,
                        onPlayAll = { songs ->
                            if (songs.isNotEmpty()) {
                                viewModel.playSongs(songs)
                            }
                        },
                        onShufflePlay = { songs ->
                            if (songs.isNotEmpty()) {
                                viewModel.playShuffled(songs)
                            }
                        },
                        onAddToQueue = { song ->
                            viewModel.addSongToQueue(song)
                        },
                        onAddSongToPlaylist = { song ->
                            selectedSongForPlaylist = song
                            showAddToPlaylistSheet = true
                        },
                        onPlayerClick = {
                            navController.navigate(Screen.Player.route)
                        },
                        onPlayNext = { song ->
                            viewModel.playNext(song)
                        },
                        onToggleFavorite = { song ->
                            viewModel.toggleFavorite(song)
                        },
                        favoriteSongs = favoriteSongs,
                        onShowSongInfo = { song ->
                            selectedSongForInfo = song
                            showSongInfoSheet = true
                        },
                        onAddToBlacklist = { song ->
                            appSettings.addToBlacklist(song.id)
                        },
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onEditAlbum = { title, artist, artworkUri, removeArtwork, onProgress, onComplete ->
                            val allAlbumsList = viewModel.albums.value
                            val activeAlbum = allAlbumsList.find { it.id == albumId }
                            if (activeAlbum != null) {
                                viewModel.batchEditMetadata(
                                    songs = activeAlbum.songs,
                                    artist = artist,
                                    album = title,
                                    genre = null,
                                    year = null,
                                    artworkUri = artworkUri,
                                    removeArtwork = removeArtwork,
                                    onProgress = onProgress,
                                    onComplete = { successCount, failCount ->
                                        onComplete(successCount, failCount)
                                    },
                                    onPermissionRequired = { pendingRequest ->
                                        try {
                                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                                pendingRequest.intentSender
                                            ).build()
                                            writePermissionLauncher.launch(intentSenderRequest)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Failed to request permission: ${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            viewModel.cancelPendingBatchMetadataWrite()
                                        }
                                    }
                                )
                            }
                        },
                        onGoToArtist = { song ->
                            val separatorEnabled = appSettings.artistSeparatorEnabled.value
                            val delimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { "/;,+&" }
                            val candidates = chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                                song.artist,
                                delimiters = delimiters,
                                enabled = separatorEnabled
                            )
                            val artistRouteName = candidates.firstOrNull { candidate ->
                                artists.any { it.name.equals(candidate, ignoreCase = true) }
                            } ?: candidates.firstOrNull()?.trim().orEmpty().ifBlank {
                                song.artist.trim()
                            }
                            if (artistRouteName.isNotBlank()) {
                                navController.navigate(Screen.ArtistDetail.createRoute(artistRouteName))
                            }
                        },
                        onShare = { song ->
                            try {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${song.title}"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        viewModel = viewModel
                    )

                    // Bottom sheets & Dialogs helpers
                    if (showAddToPlaylistSheet && selectedSongForPlaylist != null) {
                        AddToPlaylistBottomSheet(
                            song = selectedSongForPlaylist!!,
                            playlists = playlists,
                            onDismissRequest = { 
                                showAddToPlaylistSheet = false
                                selectedSongForPlaylist = null
                            },
                            onAddToPlaylist = { playlist ->
                                viewModel.addSongToPlaylist(selectedSongForPlaylist!!, playlist.id) { message ->
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showAddToPlaylistSheet = false
                                selectedSongForPlaylist = null
                            },
                            onCreateNewPlaylist = {
                                songForCreatePlaylistDialog = selectedSongForPlaylist
                                showCreatePlaylistDialog = true
                                showAddToPlaylistSheet = false
                            }
                        )
                    }

                    if (showCreatePlaylistDialog) {
                        CreatePlaylistDialog(
                            onDismiss = {
                                showCreatePlaylistDialog = false
                                songForCreatePlaylistDialog = null
                            },
                            onConfirm = { name ->
                                viewModel.createPlaylist(name)
                                showCreatePlaylistDialog = false
                                songForCreatePlaylistDialog = null
                            },
                            song = songForCreatePlaylistDialog,
                            onConfirmWithSong = { name ->
                                if (songForCreatePlaylistDialog != null) {
                                    viewModel.createPlaylist(name, listOf(songForCreatePlaylistDialog!!)) { message ->
                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.createPlaylist(name)
                                }
                                showCreatePlaylistDialog = false
                                songForCreatePlaylistDialog = null
                            }
                        )
                    }

                    if (showSongInfoSheet && selectedSongForInfo != null) {
                        SongInfoBottomSheet(
                            song = selectedSongForInfo!!,
                            onDismiss = { 
                                showSongInfoSheet = false
                                selectedSongForInfo = null
                            },
                            appSettings = appSettings,
                            onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                                viewModel.saveMetadataChanges(
                                    song = selectedSongForInfo!!,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    genre = genre,
                                    year = year,
                                    trackNumber = trackNumber,
                                    artworkUri = artworkUri,
                                    removeArtwork = removeArtwork,
                                    albumArtist = albumArtist,
                                    composer = composer,
                                    discNumber = discNumber,
                                    onSuccess = { fileWriteSucceeded ->
                                        if (fileWriteSucceeded) {
                                            android.widget.Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully_to, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        onComplete(true)
                                    },
                                    onError = { errorMessage ->
                                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                                        onComplete(false)
                                    },
                                    onPermissionRequired = { pendingRequest ->
                                        try {
                                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                                pendingRequest.intentSender
                                            ).build()
                                            writePermissionLauncher.launch(intentSenderRequest)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, R.string.localnavigation_permission_required_to_modify, android.widget.Toast.LENGTH_SHORT).show()
                                            onComplete(false)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                // Add to playlist screen
                @OptIn(ExperimentalMaterial3Api::class)
                composable(
                    Screen.AddToPlaylist.route,
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(350, easing = EaseInOutQuart)
                                )
                    }
                ) {
                    val songToAdd = viewModel.selectedSongForPlaylist.collectAsState().value
                    val targetPlaylistId = viewModel.targetPlaylistId.collectAsState().value

                    var searchQuery by remember { mutableStateOf("") }

                    // If we have a target playlist ID, we're adding songs to that playlist
                    if (targetPlaylistId != null) {
                        val targetPlaylist = playlists.find { it.id == targetPlaylistId }

                        if (targetPlaylist != null) {
                            // Filter available songs
                             val availableSongs =
                                remember(songs, targetPlaylist.songs, searchQuery) {
                                    songs.filter { song ->
                                        // Filter out songs that are already in the playlist
                                        !targetPlaylist.songs.any { it.id == song.id }
                                    }
                                }

                            if (availableSongs.isEmpty() && searchQuery.isBlank()) {
                                // No songs to add and no search query
                                AlertDialog(
                                    onDismissRequest = {
                                        viewModel.clearTargetPlaylistForAddingSongs()
                                        navigateBackOrToLanding()
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = RhythmIcons.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    },
                                    title = { Text(stringResource(R.string.playlist_no_songs_available)) },
                                    text = { Text(stringResource(R.string.playlist_all_in_playlist)) },
                                    confirmButton = {
                                        Button(onClick = {
                                            viewModel.clearTargetPlaylistForAddingSongs()
                                            navigateBackOrToLanding()
                                        }) {
                                            Icon(
                                                imageVector = RhythmIcons.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.ui_ok))
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp)
                                )
                            } else {
                                AddToPlaylistScreen(
                                    targetPlaylist = targetPlaylist,
                                    availableSongs = availableSongs,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    onBackClick = {
                                        viewModel.clearTargetPlaylistForAddingSongs()
                                        navigateBackOrToLanding()
                                    },
                                    onAddSongsToPlaylist = { songs ->
                                        if (songs.size == 1) {
                                            viewModel.addSongToPlaylist(
                                                songs[0],
                                                targetPlaylistId
                                            ) { message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val (successCount, playlistName) = viewModel.addSongsToPlaylist(
                                                songs,
                                                targetPlaylistId
                                            )
                                            val message = when {
                                                successCount == 0 -> "No songs added - they may already be in the playlist"
                                                successCount == songs.size -> "Added $successCount songs to $playlistName"
                                                else -> "Added $successCount of ${songs.size} songs to $playlistName"
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        } else {
                            // Playlist not found, go back
                            LaunchedEffect(Unit) {
                                viewModel.clearTargetPlaylistForAddingSongs()
                                navigateBackOrToLanding()
                            }
                        }
                    }
                    // If we have a song to add (from Player or Search), we're adding it to a playlist
                    else if (songToAdd != null) {
                        // Use a simpler approach without the bottom sheet state
                        var showCreatePlaylistDialog by remember { mutableStateOf(false) }

                        if (showCreatePlaylistDialog) {
                            CreatePlaylistDialog(
                                onDismiss = {
                                    showCreatePlaylistDialog = false
                                },
                                onConfirm = { name ->
                                    viewModel.createPlaylist(name)
                                    showCreatePlaylistDialog = false
                                },
                                song = songToAdd,
                                onConfirmWithSong = { name ->
                                    viewModel.createPlaylist(name)
                                    // The new playlist will be at the end of the list
                                    val newPlaylist = viewModel.playlists.value.last()
                                    viewModel.addSongToPlaylist(
                                        songToAdd,
                                        newPlaylist.id
                                    ) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                    viewModel.clearSelectedSongForPlaylist()
                                    navigateBackOrToLanding()
                                }
                            )
                        } else {
                            // Navigate back to the player screen and show the bottom sheet there
                            LaunchedEffect(Unit) {
                                navigateBackOrToLanding()
                            }
                        }
                    } else {
                        // No song selected and no target playlist, go back
                        LaunchedEffect(Unit) {
                            viewModel.clearSelectedSongForPlaylist()
                            viewModel.clearTargetPlaylistForAddingSongs()
                            navigateBackOrToLanding()
                        }
                    }
                }
            }
        }
    }

    // Queue action dialog - show at top level
    val queueActionRequest by viewModel.queueActionRequest.collectAsState()
    val queueListActionRequest by viewModel.queueListActionRequest.collectAsState()
    val currentQueueForDialog by viewModel.currentQueue.collectAsState()

    queueActionRequest?.let { request ->
        QueueActionDialog(
            song = request.song,
            queueSize = currentQueueForDialog.songs.size,
            onDismiss = { viewModel.dismissQueueActionDialog() },
            onClearAndPlay = {
                viewModel.handleQueueActionChoice(request.song, clearQueue = true)
            },
            onAddToQueue = {
                viewModel.handleQueueActionChoice(request.song, clearQueue = false)
            }
        )
    }

    queueListActionRequest?.let { request ->
        QueueListActionDialog(
            queueSize = currentQueueForDialog.songs.size,
            incomingCount = request.songs.size,
            sourceLabel = request.sourceLabel,
            onDismiss = { viewModel.dismissQueueListActionDialog() },
            onReplaceQueue = {
                viewModel.handleQueueListActionChoice("replace")
            },
            onPlayNext = {
                viewModel.handleQueueListActionChoice("play_next")
            },
            onAddToEnd = {
                viewModel.handleQueueListActionChoice("add_to_end")
            }
        )
    }

    // Media scan loader overlay for refresh operations
    AnimatedVisibility(
        visible = isMediaScanning,
        enter = fadeIn(
            animationSpec = tween(
                800,
                easing = androidx.compose.animation.core.EaseOutCubic
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                800,
                easing = androidx.compose.animation.core.EaseInCubic
            )
        )
    ) {
        MediaScanLoader(
            musicViewModel = viewModel,
            onScanComplete = {
                // Media scan loader will hide automatically when isMediaScanning becomes false
            }
        )
    }

    if (showCreatePlaylistDialog.value) {
        val songForDialog = songForCreatePlaylistDialog.value
        CreatePlaylistDialog(
            onDismiss = {
                showCreatePlaylistDialog.value = false
                songForCreatePlaylistDialog.value = null
            },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylistDialog.value = false
                songForCreatePlaylistDialog.value = null
            },
            song = songForDialog,
            onConfirmWithSong = { name ->
                if (songForDialog != null) {
                    viewModel.createPlaylist(name, listOf(songForDialog)) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createPlaylist(name)
                }
                showCreatePlaylistDialog.value = false
                songForCreatePlaylistDialog.value = null
            }
        )
    }
    }

/**
 * Navigation rail for tablets with Material 3 design - Local Navigation
 */
@Composable
private fun LocalNavigationRail(
    currentRoute: String,
    navController: NavHostController,
    firstVisibleLibraryTab: LibraryTab,
    context: android.content.Context,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Calculate rail height based on number of items (5 items * 64dp + padding)
    val railHeight = (5 * 64 + 32).dp // Increased padding from 24 to 32
    
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(8.dp),
        contentAlignment = Alignment.Center // Vertically center the rail
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            modifier = Modifier
                .height(railHeight)
                .width(80.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp), // Increased from 12.dp to 16.dp
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
            ) {
            val libraryRoute = Screen.Library.createRoute(firstVisibleLibraryTab)
            val items = listOf(
                LocalNavRailItem(
                    route = Screen.Home.route,
                    title = stringResource(R.string.settings_home_screen),
                    selectedIcon = RhythmIcons.HomeFilled,
                    unselectedIcon = RhythmIcons.Home,
                    onClick = {
                        navigateToTopLevel(Screen.Home.route)
                    }
                ),
                LocalNavRailItem(
                    route = libraryRoute,
                    title = stringResource(R.string.option_library),
                    selectedIcon = RhythmIcons.Navigation.Library,
                    unselectedIcon = RhythmIcons.Navigation.LibraryOutlined,
                    onClick = {
                        navigateToTopLevel(libraryRoute)
                    }
                ),
                LocalNavRailItem(
                    route = Screen.RhythmStats.route,
                    title = stringResource(R.string.localnavigation_stats),
                    selectedIcon = MaterialSymbolIcon("auto_graph", filled = true),
                    unselectedIcon = MaterialSymbolIcon("auto_graph"),
                    onClick = {
                        navigateToTopLevel(Screen.RhythmStats.route)
                    }
                ),
                LocalNavRailItem(
                    route = Screen.Search.route,
                    title = stringResource(R.string.cd_search),
                    selectedIcon = RhythmIcons.SearchFilled,
                    unselectedIcon = RhythmIcons.Search,
                    onClick = {
                        navigateToTopLevel(Screen.Search.route)
                    }
                ),
                LocalNavRailItem(
                    route = Screen.Settings.route,
                    title = stringResource(R.string.settings_backup_settings),
                    selectedIcon = RhythmIcons.SettingsFilled,
                    unselectedIcon = RhythmIcons.Settings,
                    onClick = {
                        navigateToTopLevel(Screen.Settings.route)
                    }
                )
            )
            
            items.forEach { item ->
                LocalNavigationRailItemWithAnimation(
                    item = item,
                    isSelected = when {
                        item.route == Screen.Home.route -> currentRoute == Screen.Home.route
                        item.route.substringBefore("?") == Screen.Library.route.substringBefore("?") -> currentRoute.substringBefore("?") == Screen.Library.route.substringBefore("?")
                        item.route == Screen.Search.route -> currentRoute == Screen.Search.route
                        item.route.contains("settings") -> currentRoute.contains("settings")
                        item.route == Screen.RhythmStats.route -> currentRoute == Screen.RhythmStats.route
                        else -> false
                    },
                    haptic = haptic,
                    context = context
                )
            }
        }
        }
    }
}

/**
 * Data class for local navigation rail items
 */
private data class LocalNavRailItem(
    val route: String,
    val title: String,
    val selectedIcon: MaterialSymbolIcon,
    val unselectedIcon: MaterialSymbolIcon,
    val onClick: () -> Unit
)

/**
 * Local navigation rail item with animated selection indicator
 */
@Composable
private fun LocalNavigationRailItemWithAnimation(
    item: LocalNavRailItem,
    isSelected: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: android.content.Context
) {
    // Enhanced animation values with spring physics
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale_${item.title}"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "alpha_${item.title}"
    )

    // Icon color animation
    val iconColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "iconColor_${item.title}"
    )

    // Indicator pill animation
    val indicatorHeight by animateDpAsState(
        targetValue = if (isSelected) 56.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicatorHeight_${item.title}"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                item.onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = animatedAlpha
                }
                .then(
                    if (isSelected) Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .size(64.dp, indicatorHeight.coerceAtLeast(0.dp))
                        .padding(vertical = 8.dp)
                    else Modifier.padding(8.dp)
                )
        ) {
            // Animated icon with crossfade
            androidx.compose.animation.Crossfade(
                targetState = isSelected,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow
                ),
                label = "iconCrossfade_${item.title}"
            ) { selected ->
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor
                )
            }
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun androidx.compose.animation.AnimatedVisibilityScope.SlideUpCornerWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cornerRadius by transition.animateDp(
        label = "slideUpCornerRadius",
        transitionSpec = { androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium) }
    ) { state ->
        if (state == androidx.compose.animation.EnterExitState.Visible) 0.dp else 28.dp
    }
    
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        content()
    }
}

