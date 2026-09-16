/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.SettingsPalettes
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.TabAnimation
import chromahub.rhythm.app.shared.presentation.components.common.SmallTabAnimation
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RhythmStatsScreen(
    navController: NavController,
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val ranges = StatsTimeRange.entries

    val pagerState = rememberPagerState(
        initialPage = 1, // Default to WEEK (index 1)
        pageCount = { ranges.size }
    )
    val tabRowState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val appSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()

    LaunchedEffect(pagerState.currentPage) {
        val index = pagerState.currentPage
        tabRowState.animateScrollToItem(index)
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.stats_title),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
            navController.popBackStack()
        },
        headerContent = {
            RhythmTimeRangeTabs(
                selectedRange = ranges[pagerState.currentPage],
                onRangeSelected = { range ->
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                    val index = ranges.indexOf(range).coerceAtLeast(0)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                tabRowState = tabRowState
            )
        }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                StatsPageContent(
                    range = ranges[page],
                    songs = songs,
                    artists = artists,
                    viewModel = viewModel,
                    useHoursFormat = useHoursFormat
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page Content Composable 
// ---------------------------------------------------------------------------

@Composable
private fun StatsPageContent(
    range: StatsTimeRange,
    songs: List<chromahub.rhythm.app.shared.data.model.Song>,
    artists: List<Artist>,
    viewModel: MusicViewModel,
    useHoursFormat: Boolean
) {
    var statsSummary by remember { mutableStateOf<PlaybackStatsRepository.PlaybackStatsSummary?>(null) }
    var previousSummary by remember { mutableStateOf<PlaybackStatsRepository.PlaybackStatsSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(range, songs) {
        isLoading = true
        statsSummary = viewModel.loadPlaybackStats(range)
        
        val now = System.currentTimeMillis()
        val prevNow = when (range) {
            StatsTimeRange.TODAY -> now - 24L * 60L * 60L * 1000L
            StatsTimeRange.WEEK -> now - 7L * 24L * 60L * 60L * 1000L
            StatsTimeRange.MONTH -> now - 30L * 24L * 60L * 60L * 1000L
            StatsTimeRange.ALL_TIME -> now
        }
        previousSummary = if (range != StatsTimeRange.ALL_TIME) {
            runCatching {
                viewModel.getPlaybackStatsRepository().loadSummary(range, songs, prevNow)
            }.getOrNull()
        } else {
            null
        }
        isLoading = false
    }

    val pageScrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "statsPageContentTransition"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    M3CircularLoader(modifier = Modifier.size(48.dp), strokeWidth = 4f)
                }
            } else if (statsSummary == null || statsSummary!!.totalPlayCount == 0) {
                EmptyStatsView()
            } else {
                val stats = statsSummary!!
                val isTablet = windowScreenWidthDp() >= 600
                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScrollState)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            ListeningOverviewCard(
                                stats = stats, 
                                previousStats = previousSummary,
                                useHoursFormat = useHoursFormat
                            )

                            ListeningHabitsCard(
                                stats = stats,
                                useHoursFormat = useHoursFormat
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            CategoryMetricsSection(
                                stats = stats, 
                                artists = artists,
                                useHoursFormat = useHoursFormat
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScrollState)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        ListeningOverviewCard(
                            stats = stats, 
                            previousStats = previousSummary,
                            useHoursFormat = useHoursFormat
                        )

                        CategoryMetricsSection(
                            stats = stats, 
                            artists = artists,
                            useHoursFormat = useHoursFormat
                        )

                        ListeningHabitsCard(
                            stats = stats,
                            useHoursFormat = useHoursFormat
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tabs & Hero Section
// ---------------------------------------------------------------------------

@Composable
private fun RhythmTimeRangeTabs(
    selectedRange: StatsTimeRange,
    onRangeSelected: (StatsTimeRange) -> Unit,
    tabRowState: androidx.compose.foundation.lazy.LazyListState
) {
    val context = LocalContext.current
    val tabNames = mapOf(
        StatsTimeRange.TODAY to context.getString(R.string.stats_today),
        StatsTimeRange.WEEK to context.getString(R.string.stats_this_week),
        StatsTimeRange.MONTH to context.getString(R.string.stats_this_month),
        StatsTimeRange.ALL_TIME to context.getString(R.string.stats_all_time)
    )

    LazyRow(
        state = tabRowState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        itemsIndexed(StatsTimeRange.entries, key = { _, range -> "tab_${range.name}" }) { index, range ->
            val isSelected = range == selectedRange
            val title = tabNames[range] ?: range.displayName

            TabAnimation(
                index = index,
                selectedIndex = StatsTimeRange.entries.indexOf(selectedRange),
                title = title,
                selectedColor = MaterialTheme.colorScheme.primary,
                onSelectedColor = MaterialTheme.colorScheme.onPrimary,
                unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                onUnselectedColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onRangeSelected(range) },
                modifier = Modifier.padding(all = 2.dp),
                content = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }
    }
}

@Composable
private fun ListeningOverviewCard(
    stats: PlaybackStatsRepository.PlaybackStatsSummary,
    previousStats: PlaybackStatsRepository.PlaybackStatsSummary?,
    useHoursFormat: Boolean
) {
    val timeline = stats.timeline.takeLast(3)
    val durationText = formatDurationAsMinutes(stats.totalDurationMs, useHoursFormat)

    val comparisonText = remember(stats.totalDurationMs, previousStats?.totalDurationMs, stats.range, useHoursFormat) {
        val currentMs = stats.totalDurationMs
        val previousMs = previousStats?.totalDurationMs ?: 0L
        
        if (stats.range == StatsTimeRange.ALL_TIME || previousStats == null) {
            "Compared with the previous period"
        } else {
            val diffMs = currentMs - previousMs
            val absDiffMs = kotlin.math.abs(diffMs)
            
            val seconds = absDiffMs / 1000
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            
            val timeStr = if (useHoursFormat && hours > 0) {
                if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            } else {
                val totalMinutes = seconds / 60
                if (totalMinutes > 0) "$totalMinutes min" else "less than a minute"
            }
            
            val relation = if (diffMs >= 0) "longer than" else "shorter than"
            val period = when (stats.range) {
                StatsTimeRange.TODAY -> "yesterday"
                StatsTimeRange.WEEK -> "last week"
                StatsTimeRange.MONTH -> "last month"
                StatsTimeRange.ALL_TIME -> ""
            }
            
            if (diffMs == 0L) {
                "Same as $period"
            } else {
                "$timeStr $relation $period"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = comparisonText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (timeline.isNotEmpty()) {
            val segmentColors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary
            )
            val maxPlays = timeline.maxOf { it.playCount }.coerceAtLeast(1)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                timeline.forEachIndexed { index, entry ->
                    val progress = (entry.playCount.toFloat() / maxPlays).coerceIn(0.28f, 1f)
                    Surface(
                        shape = when (index) {
                            0 -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                            timeline.lastIndex -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp)
                            else -> RoundedCornerShape(4.dp)
                        },
                        color = segmentColors[index % segmentColors.size],
                        modifier = Modifier
                            .weight(progress)
                            .fillMaxHeight()
                    ) {}
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.stats_most_active_at, timeline.firstOrNull()?.label ?: "9:30 am"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                timeline.drop(1).forEach { entry ->
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top Songs & Artists
// ---------------------------------------------------------------------------

@Composable
private fun TopSongsList(
    stats: PlaybackStatsRepository.PlaybackStatsSummary,
    useHoursFormat: Boolean
) {
    if (stats.topSongs.isEmpty()) return

    val items = stats.topSongs.take(5).mapIndexed { index, song ->
        val isTop = index == 0
        val songArtShape = rememberExpressiveShapeFor(
            ExpressiveShapeTarget.SONG_ART,
            fallbackShape = RoundedCornerShape(16.dp)
        )

        Material3SettingsItem(
            isHighlighted = isTop,
            leadingContent = {
                Surface(
                    shape = songArtShape,
                    color = if (isTop) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isTop) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(song.albumArtUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            title = {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            description = {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatDuration(song.totalDurationMs, useHoursFormat),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${song.playCount} plays",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.rhythmstatsscreen_top_songs),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Material3SettingsGroup(
            items = items,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}

@Composable
private fun TopArtistsList(
    stats: PlaybackStatsRepository.PlaybackStatsSummary,
    artists: List<Artist>
) {
    if (stats.topArtists.isEmpty()) return

    val artistByName = remember(artists) { artists.associateBy { it.name } }
    val artistArtShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.ARTIST_ART,
        fallbackShape = RoundedCornerShape(16.dp)
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_top_artists),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(stats.topArtists.take(5), key = { it.artist }) { artist ->
                val libraryArtist = artistByName[artist.artist]

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = artistArtShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(84.dp)
                    ) {
                        M3ImageUtils.ArtistImage(
                            imageUrl = libraryArtist?.artworkUri,
                            artistName = artist.artist,
                            modifier = Modifier.fillMaxSize(),
                            applyExpressiveShape = false
                        )
                    }
                    Text(
                        text = artist.artist,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category Metrics Section
// ---------------------------------------------------------------------------

private enum class CategoryDimension(val displayName: String) {
    SONG("Songs"),
    ARTIST("Artists"),
    ALBUM("Albums"),
    GENRE("Genres")
}

private data class CategoryMetricEntry(
    val label: String,
    val durationMs: Long,
    val plays: Int,
    val supporting: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryMetricsSection(
    stats: PlaybackStatsRepository.PlaybackStatsSummary,
    artists: List<Artist>,
    useHoursFormat: Boolean
) {
    var selectedDimension by remember { mutableStateOf(CategoryDimension.SONG) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(CategoryDimension.entries, key = { _, dimension -> "dim_${dimension.name}" }) { index, dimension ->
                val isSelected = dimension == selectedDimension
                val title = dimension.displayName

                SmallTabAnimation(
                    index = index,
                    selectedIndex = CategoryDimension.entries.indexOf(selectedDimension),
                    title = title,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    onSelectedColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                    onUnselectedColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { selectedDimension = dimension },
                    modifier = Modifier.padding(all = 2.dp),
                    content = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                )
            }
        }

        AnimatedContent(
            targetState = selectedDimension,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "categorySectionTransition"
        ) { targetDimension ->
            when (targetDimension) {
                CategoryDimension.SONG -> TopSongsList(stats = stats, useHoursFormat = useHoursFormat)
                CategoryDimension.ARTIST -> TopArtistsList(stats = stats, artists = artists)
                else -> {
                    val entries = when (targetDimension) {
                        CategoryDimension.ALBUM -> stats.topAlbums.map {
                            CategoryMetricEntry(it.album, it.totalDurationMs, it.playCount, "${it.uniqueSongs} tracks")
                        }
                        CategoryDimension.GENRE -> stats.topGenres.map {
                            CategoryMetricEntry(it.genre, 0L, (it.percentage * stats.totalPlayCount).toInt(), "Top Genre")
                        }
                    }.filter { it.plays > 0 || it.durationMs > 0 }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stats_top_dimension, targetDimension.displayName),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (entries.isEmpty()) {
                            Text(
                                "No data available for this category yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val maxVal = entries.maxOf { if (it.durationMs > 0) it.durationMs.toFloat() else it.plays.toFloat() }.coerceAtLeast(1f)

                            val settingsItems = entries.take(8).mapIndexed { index, entry ->
                                val isTop = index == 0
                                val rawVal = if (entry.durationMs > 0) entry.durationMs.toFloat() else entry.plays.toFloat()
                                val progress = (rawVal / maxVal).coerceIn(0f, 1f)

                                Material3SettingsItem(
                                    isHighlighted = isTop,
                                    leadingContent = {
                                        CategoryRankBadge(
                                            rank = index + 1,
                                            isTop = isTop
                                        )
                                    },
                                    title = {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    description = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = entry.supporting,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = if (isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (entry.durationMs > 0) {
                                                Text(
                                                    text = formatDuration(entry.durationMs, useHoursFormat),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "${entry.plays} plays",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }

                            Material3SettingsGroup(
                                items = settingsItems,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRankBadge(rank: Int, isTop: Boolean) {
    val containerColor = if (isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (isTop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Listening Habits & Timeline
// ---------------------------------------------------------------------------

@Composable
private fun ListeningHabitsCard(
    stats: PlaybackStatsRepository.PlaybackStatsSummary,
    useHoursFormat: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.rhythmstatsscreen_listening_habits),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val items = buildList {
            add(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("event_available"),
                    palette = SettingsPalettes.Emerald,
                    title = {
                        Text(
                            text = stringResource(R.string.stats_active_days),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "${stats.activeDays} days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            )

            add(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("local_fire_department"),
                    palette = SettingsPalettes.Coral,
                    title = {
                        Text(
                            text = stringResource(R.string.stats_longest_streak),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "${stats.longestStreakDays} days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            )

            add(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("history"),
                    palette = SettingsPalettes.SkyBlue,
                    title = {
                        Text(
                            text = stringResource(R.string.stats_total_sessions),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "${stats.totalSessions}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            )

            add(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("hourglass_empty"),
                    palette = SettingsPalettes.Amber,
                    title = {
                        Text(
                            text = stringResource(R.string.stats_avg_session),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        Text(
                            text = formatDuration(stats.averageSessionDurationMs, useHoursFormat),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            )

            stats.peakDayOfWeek?.let { peakDay ->
                add(
                    Material3SettingsItem(
                        icon = MaterialSymbolIcon("whatshot"),
                        palette = SettingsPalettes.Purple,
                        isHighlighted = true,
                        title = {
                            Text(
                                text = stringResource(R.string.stats_peak_day),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            Text(
                                text = peakDay,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                )
            }
        }

        Material3SettingsGroup(
            items = items,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun EmptyStatsView() {
    ExpressiveCookieEmptyState(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        title = stringResource(R.string.rhythmstatsscreen_no_stats_available_yet),
        subtitle = stringResource(R.string.rhythmstatsscreen_keep_listening_to_build),
        mainIcon = RhythmIcons.BarChart,
        accentIcon = RhythmIcons.TrendingUp,
        cornerIcon = RhythmIcons.MusicNote,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

private fun formatDuration(ms: Long, useHoursFormat: Boolean): String {
    val seconds = ms / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        useHoursFormat && hours > 0 -> "${hours}h ${minutes}m"
        else -> {
            val totalMinutes = seconds / 60
            if (totalMinutes > 0) "${totalMinutes}m" else "< 1m"
        }
    }
}

private fun formatDurationAsMinutes(ms: Long, useHoursFormat: Boolean): String {
    val seconds = ms / 1000
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return if (useHoursFormat && hours > 0) {
        if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
    } else {
        val displayMin = totalMinutes.coerceAtLeast(1)
        "$displayMin min"
    }
}
