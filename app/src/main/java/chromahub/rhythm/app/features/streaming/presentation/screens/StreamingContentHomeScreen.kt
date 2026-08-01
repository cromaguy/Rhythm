package chromahub.rhythm.app.features.streaming.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import chromahub.rhythm.app.shared.presentation.components.common.rhythmMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import java.util.Calendar
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCard
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceId
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.presentation.model.StreamingServiceOptions
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel as LocalMusicViewModel
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import androidx.compose.runtime.mutableIntStateOf
import chromahub.rhythm.app.shared.presentation.components.AudioQualityIcon
import chromahub.rhythm.app.features.streaming.presentation.components.settings.StreamingHomeSectionOrderBottomSheet

import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import android.net.Uri
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveElevatedCard
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STREAMING_SECTION_RHYTHM_GUARD = "RHYTHM_GUARD"
private const val STREAMING_SECTION_RHYTHM_STATS = "RHYTHM_STATS"
private const val STREAMING_SECTION_DISCOVER = "DISCOVER"
private const val STREAMING_SECTION_PLAYLISTS = "PLAYLISTS"
private const val STREAMING_SECTION_RECENTLY_PLAYED = "RECENTLY_PLAYED"
private const val STREAMING_SECTION_ARTISTS = "ARTISTS"
private const val STREAMING_SECTION_NEW_RELEASES = "NEW_RELEASES"

private val defaultStreamingHomeSections = listOf(
    STREAMING_SECTION_DISCOVER,
    STREAMING_SECTION_PLAYLISTS,
    STREAMING_SECTION_RECENTLY_PLAYED,
    STREAMING_SECTION_ARTISTS,
    STREAMING_SECTION_RHYTHM_GUARD,
    STREAMING_SECTION_RHYTHM_STATS,
    STREAMING_SECTION_NEW_RELEASES
)

@Composable
fun StreamingContentHomeScreen(
    viewModel: StreamingMusicViewModel,
    localMusicViewModel: LocalMusicViewModel,
    recentlyPlayedSongs: List<Song>,
    playbackStatsSummary: PlaybackStatsRepository.PlaybackStatsSummary?,
    listeningTimeMs: Long,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRhythmGuard: () -> Unit,
    onNavigateToRhythmStats: () -> Unit,
    onNavigateToArtist: (StreamingArtist) -> Unit,
    onNavigateToPlaylist: (chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist) -> Unit,
    onNavigateToAlbum: (StreamingAlbum) -> Unit,
    onConfigureService: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appSettings = remember { AppSettings.getInstance(context) }

    val selectedService by appSettings.streamingService.collectAsState()
    val sessions by viewModel.serviceSessions.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val newReleases by viewModel.newReleases.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val savedPlaylists by viewModel.savedPlaylists.collectAsState()
    val featuredPlaylists by viewModel.featuredPlaylists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadedHomeContent by viewModel.hasLoadedHomeContent.collectAsState()
    val hasLoadedLibrary by viewModel.hasLoadedLibrary.collectAsState()
    val error by viewModel.error.collectAsState()
    val sectionOrder by appSettings.streamingHomeSectionOrder.collectAsState()
    val showGreetingSection by appSettings.streamingHomeShowGreeting.collectAsState()
    val showPlaylistsSection by appSettings.streamingHomeShowPlaylists.collectAsState()
    val showRhythmGuardSection by appSettings.streamingHomeShowRhythmGuard.collectAsState()
    val showRhythmStatsSection by appSettings.streamingHomeShowRhythmStats.collectAsState()
    val showDiscoverSection by appSettings.streamingHomeShowRecommended.collectAsState()
    val discoverAutoScroll by appSettings.homeDiscoverAutoScroll.collectAsState()
    val discoverAutoScrollInterval by appSettings.homeDiscoverAutoScrollInterval.collectAsState()
    val showRecentSection by appSettings.streamingHomeShowRecentlyPlayed.collectAsState()
    val showArtistsSection by appSettings.streamingHomeShowArtists.collectAsState()
    val showNewReleasesSection by appSettings.streamingHomeShowNewReleases.collectAsState()
    val persistedSongsPlayed by appSettings.songsPlayed.collectAsState()
    val persistedUniqueArtists by appSettings.uniqueArtists.collectAsState()
    val dailyListeningStats by appSettings.dailyListeningStats.collectAsState()
    val rhythmGuardMode by appSettings.rhythmGuardMode.collectAsState()
    val rhythmGuardAge by appSettings.rhythmGuardAge.collectAsState()
    val rhythmGuardAlertThresholdMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val rhythmGuardTimeoutUntilMs by appSettings.rhythmGuardTimeoutUntilMs.collectAsState()
    val loadingSectionHeight = LocalConfiguration.current.screenHeightDp.dp

    var showSectionOrderBottomSheet by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = isLoading

    val handleAlbumClick: (StreamingAlbum) -> Unit = { album ->
        onNavigateToAlbum(album)
    }

    val selectedOption = remember(selectedService) {
        StreamingServiceOptions.defaults.firstOrNull { it.id == selectedService }
    }
    val selectedServiceName = selectedOption?.let { context.getString(it.nameRes) }
        ?: context.getString(R.string.streaming_not_selected)
    val selectedServiceId = selectedOption?.id ?: selectedService
    val fallbackServiceId = StreamingServiceOptions.defaults.firstOrNull()?.id.orEmpty()
    val serviceIdForConfig = selectedServiceId.ifBlank { fallbackServiceId }

    val isSelectedServiceConnected = sessions[selectedServiceId]?.isConnected == true
    val homeErrorMessage = error?.takeIf { it.isNotBlank() }

    val hasAnyWidgetContent =
        recommendations.isNotEmpty() ||
            newReleases.isNotEmpty() ||
            followedArtists.isNotEmpty()

    val resolvedSectionOrder = remember(sectionOrder) {
        (
            sectionOrder
                .map(::normalizeStreamingSectionId)
                .filter { it in defaultStreamingHomeSections } + defaultStreamingHomeSections
            ).distinct()
    }
    val sectionVisibility = remember(
        showDiscoverSection,
        showPlaylistsSection,
        showRhythmGuardSection,
        showRhythmStatsSection,
        showRecentSection,
        showArtistsSection,
        showNewReleasesSection
    ) {
        mapOf(
            STREAMING_SECTION_DISCOVER to showDiscoverSection,
            STREAMING_SECTION_PLAYLISTS to showPlaylistsSection,
            STREAMING_SECTION_RHYTHM_GUARD to showRhythmGuardSection,
            STREAMING_SECTION_RHYTHM_STATS to showRhythmStatsSection,
            STREAMING_SECTION_RECENTLY_PLAYED to showRecentSection,
            STREAMING_SECTION_ARTISTS to showArtistsSection,
            STREAMING_SECTION_NEW_RELEASES to showNewReleasesSection
        )
    }
    val orderedSections = remember(resolvedSectionOrder) {
        val fixedTopSections = listOf(STREAMING_SECTION_DISCOVER)
        fixedTopSections + resolvedSectionOrder.filterNot { it in fixedTopSections }
    }
    val visibleSections = remember(orderedSections, sectionVisibility) {
        orderedSections.filter { sectionVisibility[it] == true }
    }

    val recommendationWidgetSongs = recommendations.take(12)
    val streamingArtists = remember(followedArtists) {
        followedArtists
            .sortedBy { it.name.lowercase() }
            .take(12)
    }
    val streamingRecentSongs = remember(recentlyPlayedSongs, selectedServiceId) {
        recentlyPlayedSongs
            .mapNotNull { song -> song.toStreamingSongOrNull(selectedServiceId) }
            .distinctBy { it.id }
            .take(20)
    }
    val recentSongsForSelectedService = remember(streamingRecentSongs, selectedServiceId) {
        val serviceSongs = streamingRecentSongs.filter { it.id.startsWith("$selectedServiceId::") }
        if (serviceSongs.isNotEmpty()) serviceSongs else streamingRecentSongs
    }

    val totalListeningDurationMs = playbackStatsSummary?.totalDurationMs?.takeIf { it > 0L } ?: listeningTimeMs
    val songsPlayedForStats = playbackStatsSummary?.totalPlayCount?.takeIf { it > 0 } ?: persistedSongsPlayed
    val uniqueArtistsForStats = playbackStatsSummary?.uniqueArtists?.takeIf { it > 0 } ?: persistedUniqueArtists
    val rhythmGuardPolicy = remember(rhythmGuardAge) { appSettings.getRhythmGuardPolicy(rhythmGuardAge) }
    val rhythmGuardRecommendedMinutes = when (rhythmGuardMode) {
        AppSettings.RHYTHM_GUARD_MODE_MANUAL -> rhythmGuardAlertThresholdMinutes
            .takeIf { it > 0 }
            ?: rhythmGuardPolicy.recommendedDailyMinutes
        else -> rhythmGuardPolicy.recommendedDailyMinutes
    }
    val playbackStatsRepository = remember(context) { PlaybackStatsRepository.getInstance(context) }
    var todayListeningMinutes by remember { mutableIntStateOf(0) }

    val currentProgress by localMusicViewModel.progress.collectAsState()
    val currentDurationMs by localMusicViewModel.duration.collectAsState()
    val currentIsPlaying by localMusicViewModel.isPlaying.collectAsState()

    LaunchedEffect(dailyListeningStats, songsPlayedForStats, totalListeningDurationMs, currentProgress, currentDurationMs, currentIsPlaying) {
        val todaySummary = runCatching {
            playbackStatsRepository.loadSummary(StatsTimeRange.TODAY)
        }.getOrNull()
        val dbDurationMs = todaySummary?.totalDurationMs ?: 0L
        val activeSessionDurationMs = if (currentIsPlaying && currentDurationMs > 0) {
            (currentProgress * currentDurationMs).toLong()
        } else {
            0L
        }
        val totalMs = dbDurationMs + activeSessionDurationMs
        todayListeningMinutes = (totalMs / 60000L).toInt().coerceAtLeast(0)
    }
    val rhythmGuardTimeoutRemainingMs = (rhythmGuardTimeoutUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
    val isRhythmGuardTimeoutActive = rhythmGuardTimeoutRemainingMs > 0L

    LaunchedEffect(selectedServiceId, isSelectedServiceConnected, hasLoadedHomeContent) {
        if (isSelectedServiceConnected && !hasLoadedHomeContent) {
            viewModel.loadHomeContent()
        }
    }

    LaunchedEffect(isSelectedServiceConnected, showArtistsSection, hasLoadedLibrary) {
        if (isSelectedServiceConnected && showArtistsSection && !hasLoadedLibrary) {
            viewModel.loadLibrary()
        }
    }

    if (showSectionOrderBottomSheet) {
        StreamingHomeSectionOrderBottomSheet(
            appSettings = appSettings,
            onDismiss = { showSectionOrderBottomSheet = false }
        )
    }

    CollapsibleHeaderScreen(
        title = stringResource(id = R.string.streaming_integration_title),
        headerDisplayMode = 1,
        actions = {
            if (isSelectedServiceConnected) {
                ExpressiveFilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        showSectionOrderBottomSheet = true
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("reorder", filled = true),
                        contentDescription = stringResource(id = R.string.cd_reorder_home_sections),
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            ExpressiveFilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onNavigateToSettings()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.padding(start = 8.dp, end = 16.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Settings,
                    contentDescription = stringResource(id = R.string.home_settings_cd),
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    ) { contentModifier ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadHomeContent() },
            state = pullToRefreshState,
            modifier = modifier
                .then(contentModifier)
                .fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(40.dp),
                contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding())
            ) {

                when {
                    !isSelectedServiceConnected -> {
                        item {
                            StreamingHomeStateCard(
                                title = stringResource(id = R.string.streaming_home_selected_service_unavailable),
                                subtitle = stringResource(
                                    id = R.string.streaming_home_connect_selected_service,
                                    selectedServiceName
                                ),
                                icon = MaterialSymbolIcon("cloud_off", filled = true),
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                actionText = stringResource(id = R.string.streaming_manage_service),
                                onAction = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    if (serviceIdForConfig.isNotBlank()) {
                                        onConfigureService(serviceIdForConfig)
                                    } else {
                                        onNavigateToSettings()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            )
                        }
                    }

                    isSelectedServiceConnected && (isLoading || !hasLoadedHomeContent) -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .height(loadingSectionHeight)
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StreamingHomeStateCard(
                                    title = stringResource(id = R.string.streaming_library_syncing),
                                    subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint),
                                    icon = MaterialSymbolIcon("history", filled = true),
                                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    showProgressIndicator = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }

                    !homeErrorMessage.isNullOrBlank() && !hasAnyWidgetContent -> {
                        item {
                            StreamingHomeStateCard(
                                title = stringResource(id = R.string.streaming_home_selected_service_unavailable),
                                subtitle = homeErrorMessage,
                                icon = RhythmIcons.Info,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                actionText = stringResource(id = R.string.streaming_manage_service),
                                onAction = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    if (serviceIdForConfig.isNotBlank()) {
                                        onConfigureService(serviceIdForConfig)
                                    } else {
                                        onNavigateToSettings()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            )
                        }
                    }

                    isSelectedServiceConnected && !hasAnyWidgetContent -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StreamingHomeStateCard(
                                    title = stringResource(id = R.string.streaming_home_no_content_title),
                                    subtitle = stringResource(id = R.string.streaming_home_no_content_hint),
                                    icon = RhythmIcons.HomeFilled,
                                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    actionText = stringResource(id = R.string.streaming_manage_service),
                                    onAction = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        if (serviceIdForConfig.isNotBlank()) {
                                            onConfigureService(serviceIdForConfig)
                                        } else {
                                            onNavigateToSettings()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        visibleSections.forEach { sectionId ->
                            when (sectionId) {
                                STREAMING_SECTION_PLAYLISTS -> {
                                    item(key = "streaming_section_playlists") {
                                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                            val playlists = (savedPlaylists + featuredPlaylists).distinctBy { it.id }
                                            Column(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(20.dp)
                                            ) {
                                                StreamingWidgetSectionTitle(
                                                    title = stringResource(id = R.string.library_tab_playlists),
                                                    subtitle = stringResource(R.string.streamingcontenthomescreen_your_connected_streaming_service)
                                                )

                                                if (playlists.isEmpty()) {
                                                    StreamingSectionEmptyCard(
                                                        icon = MaterialSymbolIcon("playlist_play"),
                                                        title = stringResource(R.string.streamingcontenthomescreen_no_playlists_found),
                                                        subtitle = stringResource(R.string.streamingcontenthomescreen_create_playlists_on_your)
                                                    )
                                                } else {
                                                    LazyRow(
                                                        contentPadding = PaddingValues(0.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        items(
                                                            items = playlists,
                                                            key = { playlist -> playlist.id }
                                                        ) { playlist ->
                                                            StreamingPlaylistWidgetCard(
                                                                playlist = playlist,
                                                                onClick = { onNavigateToPlaylist(playlist) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                STREAMING_SECTION_DISCOVER -> {
                                    item(key = "streaming_section_discover") {
                                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                            if (recommendationWidgetSongs.isEmpty()) {
                                                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                                    StreamingSectionEmptyCard(
                                                        icon = RhythmIcons.HeadphonesFilled,
                                                        title = stringResource(id = R.string.streaming_home_widget_recommended_empty),
                                                        subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint)
                                                    )
                                                }
                                            } else {
                                                StreamingRecommendationsCarousel(
                                                    songs = recommendationWidgetSongs,
                                                    autoScrollEnabled = discoverAutoScroll,
                                                    autoScrollIntervalSeconds = discoverAutoScrollInterval,
                                                    onPlaySong = { _, index ->
                                                        viewModel.playQueue(
                                                            queue = recommendationWidgetSongs,
                                                            startIndex = index,
                                                            shuffle = false
                                                        )
                                                    },
                                                    onOpenAlbum = { song ->
                                                        song.albumId?.let { albumId ->
                                                            handleAlbumClick(
                                                                chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum(
                                                                    id = albumId,
                                                                    title = song.album,
                                                                    artist = song.albumArtist ?: song.artist,
                                                                    artworkUri = song.artworkUri,
                                                                    songCount = 0,
                                                                    year = null,
                                                                    sourceType = song.sourceType
                                                                )
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                STREAMING_SECTION_RHYTHM_GUARD -> {
                                    item(key = "streaming_section_rhythm_guard") {
                                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                StreamingWidgetSectionTitle(
                                                    title = stringResource(id = R.string.settings_rhythm_guard),
                                                    subtitle = stringResource(id = R.string.settings_rhythm_guard_list_desc)
                                                )

                                                StreamingRhythmGuardPill(
                                                    rhythmGuardMode = rhythmGuardMode,
                                                    recommendedMinutes = rhythmGuardRecommendedMinutes,
                                                    todayMinutes = todayListeningMinutes,
                                                    isTimeoutActive = isRhythmGuardTimeoutActive,
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                        onNavigateToRhythmGuard()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                STREAMING_SECTION_ARTISTS -> {
                                    item(key = "streaming_section_artists") {
                                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                StreamingWidgetSectionTitle(
                                                    title = stringResource(id = R.string.home_top_artists),
                                                    subtitle = stringResource(id = R.string.home_top_artists_subtitle)
                                                )

                                                if (streamingArtists.isEmpty()) {
                                                    StreamingSectionEmptyCard(
                                                        icon = RhythmIcons.ArtistFilled,
                                                        title = stringResource(id = R.string.home_no_artists),
                                                        subtitle = stringResource(id = R.string.home_no_artists_desc)
                                                    )
                                                } else {
                                                    StreamingArtistsSection(
                                                        artists = streamingArtists,
                                                        onArtistClick = onNavigateToArtist
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                STREAMING_SECTION_RHYTHM_STATS -> {
                                    item(key = "streaming_section_rhythm_stats") {
                                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                StreamingWidgetSectionTitle(
                                                    title = stringResource(id = R.string.settings_rhythm_stats),
                                                    subtitle = stringResource(id = R.string.settings_rhythm_stats_desc)
                                                )

                                                StreamingRhythmStatsCard(
                                                    listeningDurationMs = totalListeningDurationMs,
                                                    songsPlayed = songsPlayedForStats,
                                                    uniqueArtists = uniqueArtistsForStats,
                                                    onCardClick = {
                                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                        onNavigateToRhythmStats()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                STREAMING_SECTION_RECENTLY_PLAYED -> {
                                    item(key = "streaming_section_recent") {
                                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                StreamingWidgetSectionTitle(
                                                    title = stringResource(id = R.string.home_section_recently_played),
                                                    subtitle = stringResource(id = R.string.home_recently_played_subtitle),
                                                    onPlayAll = recentSongsForSelectedService.takeIf { it.isNotEmpty() }
                                                        ?.let { songs ->
                                                            {
                                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                                viewModel.playQueue(
                                                                    queue = songs,
                                                                    startIndex = 0,
                                                                    shuffle = false
                                                                )
                                                            }
                                                        },
                                                    onShufflePlay = recentSongsForSelectedService.takeIf { it.size > 1 }
                                                        ?.let { songs ->
                                                            {
                                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                                viewModel.playQueue(
                                                                    queue = songs,
                                                                    startIndex = 0,
                                                                    shuffle = true
                                                                )
                                                            }
                                                        }
                                                )

                                                if (recentSongsForSelectedService.isEmpty()) {
                                                    StreamingSectionEmptyCard(
                                                        icon = MaterialSymbolIcon("history"),
                                                        title = stringResource(id = R.string.home_no_recent_activity),
                                                        subtitle = stringResource(id = R.string.home_no_recent_activity_desc)
                                                    )
                                                } else {
                                                    LazyRow(
                                                        contentPadding = PaddingValues(0.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        itemsIndexed(
                                                            items = recentSongsForSelectedService,
                                                            key = { _, song -> song.id }
                                                        ) { index, song ->
                                                            StreamingSongWidgetCard(
                                                                song = song,
                                                                onPlaySong = {
                                                                    viewModel.playQueue(
                                                                        queue = recentSongsForSelectedService,
                                                                        startIndex = index,
                                                                        shuffle = false
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                STREAMING_SECTION_NEW_RELEASES -> {
                                    item(key = "streaming_section_new_releases") {
                                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                StreamingWidgetSectionTitle(
                                                    title = stringResource(id = R.string.streaming_home_widget_new_releases_title),
                                                    subtitle = stringResource(
                                                        id = R.string.streaming_home_widget_new_releases_subtitle,
                                                        selectedServiceName
                                                    )
                                                )

                                                if (newReleases.isEmpty()) {
                                                    StreamingSectionEmptyCard(
                                                        icon = MaterialSymbolIcon("new_releases", filled = true),
                                                        title = stringResource(id = R.string.streaming_home_widget_new_releases_empty),
                                                        subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint)
                                                    )
                                                } else {
                                                    LazyRow(
                                                        contentPadding = PaddingValues(0.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        items(
                                                            items = newReleases.take(12),
                                                            key = { it.id }
                                                        ) { album ->
                                                            StreamingAlbumWidgetCard(
                                                                album = album,
                                                                onPlayAlbum = { viewModel.playAlbum(album) },
                                                                onAlbumClick = { handleAlbumClick(album) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }


    }
}

@Composable
private fun StreamingHomeStateCard(
    title: String,
    subtitle: String,
    icon: MaterialSymbolIcon,
    iconContainerColor: Color,
    iconTint: Color,
    showProgressIndicator: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = ExpressiveShapes.Large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (showProgressIndicator) {
                        androidx.compose.material3.ContainedLoadingIndicator()
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (actionText != null && onAction != null) {                    RhythmGroupedButton(
                        size = RhythmButtonSize.Large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RhythmButtonWeighted(
                            onClick = onAction,
                            weight = 1f,
                            text = actionText,
                            isFirst = true,
                            isLast = true
                        )
                    }
            }
        }
    }
}

@Composable
private fun StreamingWidgetSectionTitle(
    title: String,
    subtitle: String,
    onPlayAll: (() -> Unit)? = null,
    onShufflePlay: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onPlayAll != null || onShufflePlay != null) {
            RhythmGroupedButton(size = RhythmButtonSize.Small, isFillMaxWidth = false, modifier = Modifier.widthIn(max = 120.dp)) {
                val playAction = onPlayAll
                val shuffleAction = onShufflePlay
                if (playAction != null) {
                    RhythmButtonWeighted(
                        onClick = playAction,
                        weight = 1f,
                        text = stringResource(id = R.string.action_play),
                        isFirst = true,
                        isLast = shuffleAction == null
                    )
                }
                if (shuffleAction != null) {
                    RhythmButtonWeighted(
                        onClick = shuffleAction,
                        weight = 1f,
                        icon = RhythmIcons.Shuffle,
                        text = if (playAction == null) stringResource(id = R.string.action_shuffle) else null,
                        isFirst = playAction == null,
                        isLast = true
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingRhythmGuardPill(
    rhythmGuardMode: String,
    recommendedMinutes: Int,
    todayMinutes: Int,
    isTimeoutActive: Boolean,
    onClick: () -> Unit
) {
    val modeLabel = remember(rhythmGuardMode) {
        when (rhythmGuardMode) {
            AppSettings.RHYTHM_GUARD_MODE_AUTO -> "Adaptive Guard"
            AppSettings.RHYTHM_GUARD_MODE_MANUAL -> "Manual Guard"
            else -> "Guard Off"
        }
    }

    val progressFraction = remember(todayMinutes, recommendedMinutes) {
        (todayMinutes.toFloat() / recommendedMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        label = "guard_progress"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "guard_scale"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = animatedProgress)
                            .background(
                                if (isTimeoutActive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val controlsShape = rememberExpressiveShapeFor(
                    ExpressiveShapeTarget.PLAYER_CONTROLS,
                    fallbackShape = CircleShape
                )

                Surface(
                    shape = controlsShape,
                    color = if (isTimeoutActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isTimeoutActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTimeoutActive) MaterialSymbolIcon("hourglass_empty", filled = true) else RhythmIcons.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isTimeoutActive) "Listening limit exceeded" else "$todayMinutes min / $recommendedMinutes min limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = MaterialSymbolIcon("chevron_right"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StreamingRhythmStatsCard(
    listeningDurationMs: Long,
    songsPlayed: Int,
    uniqueArtists: Int,
    onCardClick: () -> Unit
) {
    val listeningTimeHours = remember(listeningDurationMs) {
        val hours = listeningDurationMs / (1000 * 60 * 60)
        if (hours < 1) 0 else hours.toInt()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExpressiveElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCardClick() },
            shape = ExpressiveShapes.SquircleLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Player.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(bottom = 8.dp)
                )
                Text(
                    text = if (listeningTimeHours < 1) "< 1h" else "${listeningTimeHours}h",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.stats_total_listening_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCardClick() },
                shape = ExpressiveShapes.Large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Music.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        text = "$songsPlayed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.homescreen_plays),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            ExpressiveCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCardClick() },
                shape = ExpressiveShapes.Large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Artist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(bottom = 4.dp)
                    )
                    Text(
                        text = "$uniqueArtists",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.settings_tab_artists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun StreamingRecommendationsCarousel(
    songs: List<StreamingSong>,
    autoScrollEnabled: Boolean,
    autoScrollIntervalSeconds: Int,
    onPlaySong: (StreamingSong, Int) -> Unit,
    onOpenAlbum: (StreamingSong) -> Unit
) {
    val carouselState = rememberCarouselState(
        initialItem = 0,
        itemCount = { songs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (songs.isNotEmpty() && !carouselState.isScrollInProgress) {
                    coroutineScope.launch {
                        carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, songs.lastIndex))
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(songs.size, autoScrollEnabled, autoScrollIntervalSeconds) {
        if (autoScrollEnabled && songs.size > 1) {
            if (!carouselState.isScrollInProgress) {
                carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, songs.lastIndex))
            }
            while (true) {
                delay(autoScrollIntervalSeconds.coerceIn(2, 20) * 1000L)
                if (!carouselState.isScrollInProgress) {
                    val currentItem = carouselState.currentItem
                    val nextItem = (currentItem + 1) % songs.size
                    carouselState.animateScrollToItem(
                        nextItem,
                        animationSpec = tween(durationMillis = 900)
                    )
                }
            }
        }
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val widthSizeClass = windowSizeClass.widthSizeClass
    val heightSizeClass = windowSizeClass.heightSizeClass

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val headerHeight = when (heightSizeClass) {
        WindowHeightSizeClass.Compact -> 280.dp
        else -> when (widthSizeClass) {
            WindowWidthSizeClass.Medium -> 500.dp
            WindowWidthSizeClass.Expanded -> 600.dp
            else -> screenWidth
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
    ) {
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = screenWidth,
            itemSpacing = 0.dp,
            contentPadding = PaddingValues(0.dp),
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val song = songs[page]
            val hasAlbumId = !song.albumId.isNullOrBlank()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape)
                    .clickable {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (hasAlbumId) {
                            onOpenAlbum(song)
                        } else {
                            onPlaySong(song, page)
                        }
                    }
            ) {
                M3ImageUtils.TrackImage(
                    imageUrl = song.artworkUri,
                    trackName = song.title,
                    modifier = Modifier.fillMaxSize(),
                    applyExpressiveShape = false
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val releaseYear = song.releaseDate?.take(4)?.toIntOrNull() ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RhythmGroupedButton(
                            size = RhythmButtonSize.Large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RhythmButtonWeighted(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    onPlaySong(song, page)
                                },
                                weight = 1f,
                                text = context.getString(R.string.action_play),
                                isFirst = true,
                                isLast = true
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (releaseYear > 0) {
                                Text(
                                    text = releaseYear.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            AudioQualityIcon(song = song)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingSectionEmptyCard(
    icon: MaterialSymbolIcon,
    title: String,
    subtitle: String
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = ExpressiveShapes.Large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = rememberExpressiveShapeFor(target = ExpressiveShapeTarget.ALBUM_ART),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StreamingSongWidgetCard(
    song: StreamingSong,
    onPlaySong: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    ExpressiveCard(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onPlaySong()
        },
        modifier = Modifier
            .width(190.dp)
            .height(88.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = ExpressiveShapes.Large
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.TrackImage(
                imageUrl = song.artworkUri,
                trackName = song.title,
                modifier = Modifier.size(64.dp),
                applyExpressiveShape = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = RhythmIcons.Play,
                contentDescription = stringResource(id = R.string.cd_play),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StreamingAlbumWidgetCard(
    album: StreamingAlbum,
    onPlayAlbum: () -> Unit,
    onAlbumClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    ExpressiveCard(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onAlbumClick()
        },
        modifier = Modifier
            .width(160.dp)
            .height(252.dp),
        shape = ExpressiveShapes.SquircleLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Box {
                M3ImageUtils.AlbumArt(
                    imageUrl = album.artworkUri,
                    albumName = album.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = rememberExpressiveShapeFor(ExpressiveShapeTarget.ALBUM_ART)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    ExpressiveFilledIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            onPlayAlbum()
                        },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Play,
                            contentDescription = stringResource(id = R.string.cd_play_album),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .rhythmMarquee(),
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )

                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                album.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingArtistWidgetCard(
    artist: StreamingArtist,
    onOpenArtist: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val cardSize = 120.dp

    Column(
        modifier = Modifier
            .width(cardSize)
            .clickable {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                onOpenArtist()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(cardSize)) {
            M3ImageUtils.ArtistImage(
                imageUrl = artist.artworkUri,
                artistName = artist.name,
                modifier = Modifier.fillMaxSize()
            )

            ExpressiveFilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onOpenArtist()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Play,
                    contentDescription = stringResource(id = R.string.cd_play_artist, artist.name),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StreamingArtistsSection(
    artists: List<StreamingArtist>,
    onArtistClick: (StreamingArtist) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        val gridColumns = if (configuration.screenWidthDp >= 840) 6 else 4
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.height(320.dp)
        ) {
            items(
                items = artists,
                key = { it.id },
                contentType = { "artist" }
            ) { artist ->
                StreamingArtistWidgetCard(
                    artist = artist,
                    onOpenArtist = { onArtistClick(artist) }
                )
            }
        }
    } else {
        val artistsListState = rememberLazyListState()
        LazyRow(
            state = artistsListState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = artists,
                key = { it.id },
                contentType = { "artist" }
            ) { artist ->
                StreamingArtistWidgetCard(
                    artist = artist,
                    onOpenArtist = { onArtistClick(artist) }
                )
            }
        }
    }
}

private fun normalizeStreamingSectionId(sectionId: String): String {
    return when (sectionId.trim()) {
        "STATS" -> STREAMING_SECTION_RHYTHM_STATS
        "RECOMMENDED" -> STREAMING_SECTION_DISCOVER
        "PLAYLISTS" -> STREAMING_SECTION_PLAYLISTS
        else -> sectionId.trim()
    }
}

private fun Song.toStreamingSongOrNull(defaultServiceId: String): StreamingSong? {
    val uriValue = uri.toString()
    val hasPrefixedServiceId = id.contains("::")
    val isNetworkStream =
        uriValue.startsWith("https://", ignoreCase = true) ||
            uriValue.startsWith("http://", ignoreCase = true)

    if (!hasPrefixedServiceId && !isNetworkStream) {
        return null
    }

    val embeddedServiceId = id.substringBefore("::", missingDelimiterValue = "")
    val resolvedServiceId = when {
        embeddedServiceId in StreamingServiceId.all -> embeddedServiceId
        defaultServiceId in StreamingServiceId.all -> defaultServiceId
        else -> StreamingServiceId.SUBSONIC
    }
    val normalizedId = if (hasPrefixedServiceId) id else "$resolvedServiceId::$id"

    return StreamingSong(
        id = normalizedId,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        artworkUri = artworkUri?.toString(),
        sourceType = resolvedServiceId.toSourceType(),
        streamingUrl = uriValue.takeIf { isNetworkStream },
        previewUrl = null,
        albumId = albumId.takeIf { it.isNotBlank() },
        albumArtist = albumArtist?.takeIf { it.isNotBlank() },
        externalId = normalizedId.substringAfter("::", missingDelimiterValue = id)
    )
}

private fun String.toSourceType(): SourceType {
    return when (this) {
        StreamingServiceId.SUBSONIC -> SourceType.SUBSONIC
        StreamingServiceId.JELLYFIN -> SourceType.JELLYFIN
        else -> SourceType.UNKNOWN
    }
}

@Composable
private fun StreamingPlaylistWidgetCard(
    playlist: chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    ExpressiveCard(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onClick()
        },
        modifier = Modifier
            .width(140.dp)
            .height(190.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = ExpressiveShapes.Large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            M3ImageUtils.TrackImage(
                imageUrl = playlist.artworkUri,
                trackName = playlist.name,
                modifier = Modifier
                    .size(116.dp)
                    .fillMaxWidth(),
                applyExpressiveShape = true
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val songCount = playlist.getTracks().size
                Text(
                    text = if (songCount == 1) "$songCount song" else "$songCount songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}