@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.shared.presentation.components.AudioQualityIcon
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSongMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortOption
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.network.WikipediaProvider
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.M3ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ArtistSortOrder {
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC
}

private data class ArtistDetailContent(
    val songs: List<Song>,
    val albums: List<Album>
)

@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onAddToQueueAll: (List<Song>) -> Unit = {},
    onAddToQueue: (Song) -> Unit,
    onAddSongToPlaylist: (Song) -> Unit,
    onPlayerClick: () -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    favoriteSongs: Set<String> = emptySet(),
    onShowSongInfo: (Song) -> Unit = {},
    showPlayNextAction: Boolean = true,
    showAddToQueueAction: Boolean = true,
    showToggleFavoriteAction: Boolean = true,
    showAddToPlaylistAction: Boolean = true,
    showSongInfoAction: Boolean = true,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    artistOverride: Artist? = null,
    songsOverride: List<Song>? = null,
    albumsOverride: List<Album>? = null,
    isContentLoadingOverride: Boolean? = null,
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscapeTablet = isTablet && configuration.screenWidthDp > configuration.screenHeightDp

    val appSettings = remember { AppSettings.getInstance(context) }
    val groupByAlbumArtist by appSettings.groupByAlbumArtist.collectAsState()
    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val wikipediaApiEnabled by appSettings.wikipediaApiEnabled.collectAsState()
    val albumScreenGradientBlur by appSettings.albumBottomSheetGradientBlur.collectAsState()
    
    // Get songs and albums from viewModel
    val allSongs by viewModel.filteredSongs.collectAsState()
    val allAlbums by viewModel.albums.collectAsState()
    val allArtists by viewModel.artists.collectAsState()
    
    // Find the artist
    val artist = remember(allArtists, artistName, artistOverride) {
        artistOverride ?: allArtists.find { it.name == artistName }
    }

    val artistContent by produceState<ArtistDetailContent?>(
        initialValue = if (songsOverride != null && albumsOverride != null) {
            ArtistDetailContent(songs = songsOverride, albums = albumsOverride)
        } else {
            null
        },
        allSongs,
        allAlbums,
        artistName,
        groupByAlbumArtist,
        artistSeparatorEnabled,
        artistSeparatorDelimiters,
        songsOverride,
        albumsOverride
    ) {
        if (songsOverride != null && albumsOverride != null) {
            value = ArtistDetailContent(songs = songsOverride, albums = albumsOverride)
            return@produceState
        }

        value = withContext(Dispatchers.Default) {
            fun splitArtistNames(artistNameStr: String): List<String> {
                return chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                    artistName = artistNameStr,
                    delimiters = artistSeparatorDelimiters,
                    enabled = artistSeparatorEnabled
                )
            }

            fun songMatchesArtist(song: Song): Boolean {
                val artistField = if (groupByAlbumArtist) {
                    val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
                    if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                        explicitAlbumArtist
                    } else {
                        song.artist
                    }
                } else {
                    song.artist
                }

                return splitArtistNames(artistField).any { it.equals(artistName, ignoreCase = true) }
            }

            val songs = allSongs.filter(::songMatchesArtist)
            val albums = allAlbums.filter { album -> album.songs.any { song -> songMatchesArtist(song) } }
            ArtistDetailContent(songs = songs, albums = albums)
        }
    }

    val rawArtistSongs = songsOverride ?: artistContent?.songs.orEmpty()
    val artistAlbums = albumsOverride ?: artistContent?.albums.orEmpty()
    val isArtistContentLoading = isContentLoadingOverride ?: (
        if (songsOverride != null && albumsOverride != null) {
            false
        } else {
            artistContent == null
        }
    )

    // Sort State
    var sortOrder by remember { mutableStateOf(ArtistSortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    val artistSongs = remember(rawArtistSongs, sortOrder) {
        when (sortOrder) {
            ArtistSortOrder.DEFAULT -> rawArtistSongs
            ArtistSortOrder.TITLE_ASC -> rawArtistSongs.sortedBy { it.title.lowercase() }
            ArtistSortOrder.TITLE_DESC -> rawArtistSongs.sortedByDescending { it.title.lowercase() }
            ArtistSortOrder.DURATION_ASC -> rawArtistSongs.sortedBy { it.duration }
            ArtistSortOrder.DURATION_DESC -> rawArtistSongs.sortedByDescending { it.duration }
        }
    }

    // Artist description from Wikipedia if enabled
    var artistDescription by remember(artistName) { mutableStateOf<String?>(null) }
    var isDescriptionLoading by remember(artistName) { mutableStateOf(false) }

    LaunchedEffect(artistName, wikipediaApiEnabled) {
        if (artistName.isNotBlank() && wikipediaApiEnabled) {
            isDescriptionLoading = true
            withContext(Dispatchers.IO) {
                val desc = WikipediaProvider.getAlbumDescription(artistName, null)
                withContext(Dispatchers.Main) {
                    artistDescription = desc
                    isDescriptionLoading = false
                }
            }
        } else {
            artistDescription = null
        }
    }

    val imageRefreshRequestedArtistIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(artist?.id, artist?.artworkUri, artistOverride) {
        if (artistOverride != null) {
            return@LaunchedEffect
        }
        val currentArtist = artist ?: return@LaunchedEffect
        if (currentArtist.artworkUri == null && !imageRefreshRequestedArtistIds.contains(currentArtist.id)) {
            imageRefreshRequestedArtistIds.add(currentArtist.id)
            viewModel.refreshArtistImage(currentArtist.id)
        }
    }

    val totalDuration = remember(rawArtistSongs) { rawArtistSongs.sumOf { it.duration } }
    val displayArtworkUri = artist?.artworkUri
    val backgroundColor = MaterialTheme.colorScheme.background

    if (isLandscapeTablet) {
        // Animated infinite transition for backdrop orbs (matching AlbumDetailScreen)
        val infiniteTransition = rememberInfiniteTransition(label = "tabletArtistBackdrop")
        val translationX1 by infiniteTransition.animateFloat(
            initialValue = -60f, targetValue = 60f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
            label = "tx1"
        )
        val translationY1 by infiniteTransition.animateFloat(
            initialValue = -40f, targetValue = 40f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
            label = "ty1"
        )
        val pulseScale1 by infiniteTransition.animateFloat(
            initialValue = 0.92f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
            label = "ps1"
        )
        val pulseScale2 by infiniteTransition.animateFloat(
            initialValue = 1.05f, targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Reverse),
            label = "ps2"
        )
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
            label = "rot"
        )

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (albumScreenGradientBlur) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(56.dp)
                            .alpha(0.68f)
                    ) {
                        if (displayArtworkUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .apply(ImageUtils.buildImageRequest(displayArtworkUri, artistName, context.cacheDir, M3PlaceholderType.ARTIST))
                                    .build(),
                                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(340.dp)
                                .align(Alignment.TopStart)
                                .graphicsLayer {
                                    translationX = translationX1
                                    translationY = translationY1
                                    scaleX = pulseScale1
                                    scaleY = pulseScale1
                                    rotationZ = rotationAngle
                                }
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(420.dp)
                                .align(Alignment.BottomEnd)
                                .graphicsLayer {
                                    translationX = -translationX1 * 0.8f
                                    translationY = -translationY1 * 0.9f
                                    scaleX = pulseScale2
                                    scaleY = pulseScale2
                                    rotationZ = -rotationAngle * 1.2f
                                }
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.52f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
                                )
                            )
                        )
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Panel: Info & Artwork (40%)
                    Surface(modifier = Modifier.weight(0.4f).fillMaxHeight(), color = Color.Transparent) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            IconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    onBack()
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Back,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(280.dp),
                                    shape = RoundedCornerShape(32.dp),
                                    shadowElevation = 12.dp
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .apply(
                                                ImageUtils.buildImageRequest(
                                                    artist?.artworkUri,
                                                    artistName,
                                                    context.cacheDir,
                                                    M3PlaceholderType.ARTIST
                                                )
                                            )
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(28.dp))

                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                // Clean details text (no raw chips below artist name)
                                val songsCount = rawArtistSongs.size.takeIf { it > 0 } ?: artist?.numberOfTracks ?: 0
                                val albumsCount = artistAlbums.size.takeIf { it > 0 } ?: artist?.numberOfAlbums ?: 0
                                val detailsText = buildString {
                                    append("$albumsCount albums • $songsCount tracks")
                                    if (totalDuration > 0) {
                                        append(" • ${formatDuration(totalDuration, useHoursFormat)}")
                                    }
                                }
                                Text(
                                    text = detailsText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Right Panel: Actions, Albums & Songs (60%)
                    Surface(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                        color = Color.Transparent
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 24.dp,
                                bottom = (LocalMiniPlayerPadding.current.calculateBottomPadding() + 24.dp).coerceAtLeast(100.dp),
                                start = 16.dp,
                                end = 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item {
                                if (isArtistContentLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    ArtistActionButtons(
                                        artistSongs = artistSongs,
                                        onPlayAll = {
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayAll(artistSongs)
                                                onPlayerClick()
                                            }
                                        },
                                        onShufflePlay = {
                                            if (artistSongs.isNotEmpty()) {
                                                onShufflePlay(artistSongs)
                                                onPlayerClick()
                                            }
                                        },
                                        onAddToQueueAll = {
                                            if (artistSongs.isNotEmpty()) {
                                                onAddToQueueAll(artistSongs)
                                            }
                                        },
                                        haptics = haptics
                                    )
                                }
                            }

                            if (artistDescription != null) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AboutArtistSection(
                                        description = artistDescription!!,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }

                            if (!isArtistContentLoading && artistAlbums.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    ArtistAlbumsSection(
                                        artistAlbums = artistAlbums,
                                        viewModel = viewModel,
                                        onAlbumClick = onAlbumClick,
                                        haptics = haptics
                                    )
                                }
                            }

                            if (!isArtistContentLoading && artistSongs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    ArtistSongsSectionHeader(
                                        songsCount = artistSongs.size,
                                        context = context
                                    )
                                }

                                itemsIndexed(artistSongs, key = { _, song -> "artist_song_${song.id}" }) { index, song ->
                                    AnimateIn {
                                        ArtistSongItem(
                                            song = song,
                                            index = index,
                                            totalCount = artistSongs.size,
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onSongClick(song)
                                                onPlayerClick()
                                            },
                                            onAddToQueue = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onAddToQueue(song)
                                            },
                                            onAddToPlaylist = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onAddSongToPlaylist(song)
                                            },
                                            onPlayNext = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onPlayNext(song)
                                            },
                                            onToggleFavorite = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onToggleFavorite(song)
                                            },
                                            isFavorite = favoriteSongs.contains(song.id),
                                            onShowSongInfo = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onShowSongInfo(song)
                                            },
                                            showPlayNextAction = showPlayNextAction,
                                            showAddToQueueAction = showAddToQueueAction,
                                            showToggleFavoriteAction = showToggleFavoriteAction,
                                            showAddToPlaylistAction = showAddToPlaylistAction,
                                            showSongInfoAction = showSongInfoAction,
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            useHoursFormat = useHoursFormat,
                                            haptics = haptics
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Mobile layout with exitUntilCollapsedScrollBehavior
        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            topAppBarState,
            canScroll = { true }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            val expandedAlpha = ((0.65f - collapsedFraction) / 0.4f).coerceIn(0f, 1f)

            if (expandedAlpha > 0.01f && !isArtistContentLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .graphicsLayer {
                            alpha = expandedAlpha
                            scaleX = 1f + collapsedFraction * 0.15f
                            scaleY = 1f + collapsedFraction * 0.15f
                        }
                ) {
                    if (displayArtworkUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .apply(ImageUtils.buildImageRequest(displayArtworkUri, artistName, context.cacheDir, M3PlaceholderType.ARTIST))
                                .build(),
                            contentDescription = stringResource(R.string.artist_artwork_description, artistName),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        backgroundColor.copy(alpha = 0.6f),
                                        backgroundColor
                                    )
                                )
                            )
                    )

                    // Hero Artist Details - bottom aligned cleanly without raw chips
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(24.dp)
                            .graphicsLayer {
                                translationY = -collapsedFraction * 120f
                            }
                    ) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val songsCount = rawArtistSongs.size.takeIf { it > 0 } ?: artist?.numberOfTracks ?: 0
                        val albumsCount = artistAlbums.size.takeIf { it > 0 } ?: artist?.numberOfAlbums ?: 0
                        val detailsText = buildString {
                            append("$albumsCount albums • $songsCount tracks")
                            if (totalDuration > 0) {
                                append(" • ${formatDuration(totalDuration, useHoursFormat)}")
                            }
                        }
                        Text(
                            text = detailsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isArtistContentLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 8.dp)
                        .size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.Back,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val currentSortKey = when (sortOrder) {
                ArtistSortOrder.DEFAULT -> "DEFAULT"
                ArtistSortOrder.TITLE_ASC, ArtistSortOrder.TITLE_DESC -> "TITLE"
                ArtistSortOrder.DURATION_ASC, ArtistSortOrder.DURATION_DESC -> "DURATION"
            }
            val isAscending = when (sortOrder) {
                ArtistSortOrder.TITLE_DESC, ArtistSortOrder.DURATION_DESC -> false
                else -> true
            }
            val sortOptions = remember(context) {
                listOf(
                    RhythmSortOption("DEFAULT", "Default Order", RhythmIcons.FormatListNumbered),
                    RhythmSortOption("TITLE", context.getString(R.string.library_sort_title), RhythmIcons.SortByAlpha),
                    RhythmSortOption("DURATION", context.getString(R.string.sort_duration), RhythmIcons.AccessTime)
                )
            }

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        LargeTopAppBar(
                            title = {
                                val titleAlpha = ((collapsedFraction - 0.2f) / 0.4f).coerceIn(0f, 1f)
                                if (titleAlpha > 0.01f) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .padding(start = 14.dp)
                                            .graphicsLayer { alpha = titleAlpha }
                                    ) {
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (24 + (32 - 24) * (1 - collapsedFraction)).sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        onBack()
                                    },
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Back,
                                            contentDescription = stringResource(R.string.cd_back),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                }
                            },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Box {
                                        FilledIconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier.size(40.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Icon(
                                                imageVector = RhythmIcons.Actions.Sort,
                                                contentDescription = stringResource(R.string.content_desc_sort_songs),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier
                                                .widthIn(min = 250.dp)
                                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                                .padding(8.dp)
                                        ) {
                                            RhythmSortMenuContent(
                                                selectedKey = currentSortKey,
                                                isAscending = isAscending,
                                                options = sortOptions,
                                                onKeySelected = { key ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    sortOrder = when (key) {
                                                        "DEFAULT" -> ArtistSortOrder.DEFAULT
                                                        "TITLE" -> if (isAscending) ArtistSortOrder.TITLE_ASC else ArtistSortOrder.TITLE_DESC
                                                        "DURATION" -> if (isAscending) ArtistSortOrder.DURATION_ASC else ArtistSortOrder.DURATION_DESC
                                                        else -> ArtistSortOrder.DEFAULT
                                                    }
                                                    showSortMenu = false
                                                },
                                                onDirectionToggled = { asc ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    sortOrder = when (currentSortKey) {
                                                        "DEFAULT" -> ArtistSortOrder.DEFAULT
                                                        "TITLE" -> if (asc) ArtistSortOrder.TITLE_ASC else ArtistSortOrder.TITLE_DESC
                                                        "DURATION" -> if (asc) ArtistSortOrder.DURATION_ASC else ArtistSortOrder.DURATION_DESC
                                                        else -> ArtistSortOrder.DEFAULT
                                                    }
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            scrollBehavior = scrollBehavior,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            ) { paddingValues ->
                val collapsedTopPadding = paddingValues.calculateTopPadding()
                val dynamicTopPadding = 450.dp + (collapsedTopPadding - 450.dp) * collapsedFraction

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = dynamicTopPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = (LocalMiniPlayerPadding.current.calculateBottomPadding() + 24.dp).coerceAtLeast(100.dp)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!isArtistContentLoading) {
                            // Action Buttons (Play / Shuffle / Queue)
                            item {
                                AnimatedVisibility(
                                    visible = rawArtistSongs.isNotEmpty(),
                                    enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(animationSpec = tween(300)),
                                    exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(animationSpec = tween(200))
                                ) {
                                    ArtistActionButtons(
                                        artistSongs = artistSongs,
                                        onPlayAll = {
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayAll(artistSongs)
                                                onPlayerClick()
                                            }
                                        },
                                        onShufflePlay = {
                                            if (artistSongs.isNotEmpty()) {
                                                onShufflePlay(artistSongs)
                                                onPlayerClick()
                                            }
                                        },
                                        onAddToQueueAll = {
                                            if (artistSongs.isNotEmpty()) {
                                                onAddToQueueAll(artistSongs)
                                            }
                                        },
                                        haptics = haptics
                                    )
                                }
                            }

                            // About Artist Section
                            if (artistDescription != null) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AboutArtistSection(
                                        description = artistDescription!!,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }

                            // Albums Section
                            if (artistAlbums.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    ArtistAlbumsSection(
                                        artistAlbums = artistAlbums,
                                        viewModel = viewModel,
                                        onAlbumClick = onAlbumClick,
                                        haptics = haptics
                                    )
                                }
                            }

                            // Songs Section Header
                            if (artistSongs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    ArtistSongsSectionHeader(
                                        songsCount = artistSongs.size,
                                        context = context
                                    )
                                }

                                itemsIndexed(artistSongs, key = { _, song -> "artist_song_${song.id}" }) { index, song ->
                                    AnimateIn {
                                        ArtistSongItem(
                                            song = song,
                                            index = index,
                                            totalCount = artistSongs.size,
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onSongClick(song)
                                                onPlayerClick()
                                            },
                                            onAddToQueue = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onAddToQueue(song)
                                            },
                                            onAddToPlaylist = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onAddSongToPlaylist(song)
                                            },
                                            onPlayNext = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onPlayNext(song)
                                            },
                                            onToggleFavorite = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onToggleFavorite(song)
                                            },
                                            isFavorite = favoriteSongs.contains(song.id),
                                            onShowSongInfo = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                onShowSongInfo(song)
                                            },
                                            showPlayNextAction = showPlayNextAction,
                                            showAddToQueueAction = showAddToQueueAction,
                                            showToggleFavoriteAction = showToggleFavoriteAction,
                                            showAddToPlaylistAction = showAddToPlaylistAction,
                                            showSongInfoAction = showSongInfoAction,
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            useHoursFormat = useHoursFormat,
                                            haptics = haptics
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

@Composable
private fun AboutArtistSection(
    description: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About Artist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded }
            )
            if (description.length > 150) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (expanded) "Show Less" else "Show More",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun ArtistActionButtons(
    artistSongs: List<Song>,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onAddToQueueAll: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    var addToQueuePressed by remember { mutableStateOf(false) }

    val addToQueueScale by animateFloatAsState(
        targetValue = if (addToQueuePressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "addToQueueScale"
    )

    LaunchedEffect(addToQueuePressed) {
        if (addToQueuePressed) {
            delay(150)
            addToQueuePressed = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Play All Button - Connected Shape
            RhythmDetailActionButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onPlayAll()
                },
                isFirst = true,
                isLast = false,
                enabled = artistSongs.isNotEmpty(),
                icon = RhythmIcons.Play,
                text = stringResource(R.string.action_play_all),
                fontWeight = FontWeight.Bold,
                contentPadding = PaddingValues(horizontal = 24.dp)
            )
            
            // Shuffle Button - Connected Shape
            RhythmDetailActionButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onShufflePlay()
                },
                type = RhythmButtonType.Tonal,
                isFirst = false,
                isLast = true,
                enabled = artistSongs.isNotEmpty(),
                icon = RhythmIcons.Shuffle,
                text = stringResource(R.string.cd_shuffle),
                fontWeight = FontWeight.SemiBold,
                contentPadding = PaddingValues(horizontal = 24.dp)
            )
        }

        // Add to Queue Button
        FilledTonalButton(
            onClick = {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                addToQueuePressed = true
                onAddToQueueAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer {
                    scaleX = addToQueueScale
                    scaleY = addToQueueScale
                },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            enabled = artistSongs.isNotEmpty()
        ) {
            Icon(
                imageVector = RhythmIcons.Queue,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_add_to_queue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ArtistAlbumsSection(
    artistAlbums: List<Album>,
    viewModel: MusicViewModel,
    onAlbumClick: (Album) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.bottomsheet_albums),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RhythmIcons.Album,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${artistAlbums.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = artistAlbums,
                key = { "artistalbum_${it.id}" }
            ) { album ->
                ArtistAlbumCard(
                    album = album,
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onAlbumClick(album)
                    },
                    onPlay = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        viewModel.playAlbum(album)
                    },
                    haptics = haptics
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val albumArtShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.ALBUM_ART,
        fallbackShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    )
    
    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onClick()
        },
        modifier = Modifier.width(135.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .apply(
                            ImageUtils.buildImageRequest(
                                album.artworkUri,
                                album.title,
                                context.cacheDir,
                                M3PlaceholderType.ALBUM
                            )
                        )
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(albumArtShape)
                )
                
                // Play button overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    FilledIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onPlay()
                        },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Play,
                            contentDescription = stringResource(R.string.content_desc_play_album),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = "${album.numberOfSongs} ${if (album.numberOfSongs == 1) "song" else "songs"}${if (album.year > 0) " • ${album.year}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistSongsSectionHeader(
    songsCount: Int,
    context: android.content.Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.bottomsheet_songs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = RhythmIcons.Music.Song,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "$songsCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistSongItem(
    song: Song,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
    onShowSongInfo: () -> Unit,
    showPlayNextAction: Boolean = true,
    showAddToQueueAction: Boolean = true,
    showToggleFavoriteAction: Boolean = true,
    showAddToPlaylistAction: Boolean = true,
    showSongInfoAction: Boolean = true,
    currentSong: Song?,
    isPlaying: Boolean,
    useHoursFormat: Boolean,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    index: Int = 0,
    totalCount: Int = 0,
    itemShape: RoundedCornerShape? = null
) {
    val context = LocalContext.current
    var showDropdown by remember { mutableStateOf(false) }
    
    val isCurrentSong = currentSong?.id == song.id
    
    val containerColor by animateColorAsState(
        targetValue = when {
            isCurrentSong -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "containerColor"
    )

    val hasOverflowActions =
        showPlayNextAction ||
            showAddToQueueAction ||
            showToggleFavoriteAction ||
            showAddToPlaylistAction ||
            showSongInfoAction
    
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = itemShape ?: groupedArtistItemShape(index, totalCount),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = rememberExpressiveShapeFor(
                        ExpressiveShapeTarget.SONG_ART,
                        fallbackShape = MaterialTheme.shapes.large
                    ),
                    border = if (isCurrentSong) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    M3ImageUtils.TrackImage(
                        imageUrl = song.artworkUri,
                        trackName = song.title,
                        modifier = Modifier.fillMaxSize(),
                        applyExpressiveShape = false
                    )
                }
                if (isCurrentSong && isPlaying) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .offset(x = 4.dp, y = 4.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PlayingEqIcon(
                                modifier = Modifier.size(width = 10.dp, height = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                isPlaying = isPlaying,
                                bars = 3
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    AudioQualityIcon(
                        song = song,
                        iconSize = 16.dp,
                        padding = 0.dp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (song.duration > 0) {
                Text(
                    text = formatDuration(song.duration, useHoursFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            if (hasOverflowActions) {
                Box {
                    FilledIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            showDropdown = true
                        },
                        modifier = Modifier
                            .width(32.dp)
                            .height(44.dp),
                        shape = RoundedCornerShape(50),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.More,
                            contentDescription = stringResource(R.string.content_desc_more_options),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            showDropdown = false
                        },
                        modifier = Modifier
                            .widthIn(min = 220.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        RhythmSongMenuContent(
                            song = song,
                            onPlayNext = if (showPlayNextAction) {
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    onPlayNext()
                                }
                            } else null,
                            onAddToQueue = if (showAddToQueueAction) {
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    onAddToQueue()
                                }
                            } else null,
                            isFavorite = isFavorite,
                            onToggleFavorite = if (showToggleFavoriteAction) {
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    onToggleFavorite()
                                }
                            } else null,
                            onAddToPlaylist = if (showAddToPlaylistAction) {
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    onAddToPlaylist()
                                }
                            } else null,
                            onShowSongInfo = if (showSongInfoAction) {
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    onShowSongInfo()
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

private fun groupedArtistItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp,
            bottomStart = 6.dp, bottomEnd = 6.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 6.dp, topEnd = 6.dp,
            bottomStart = 24.dp, bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun AnimateIn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(durationMillis = 300, delayMillis = 50), label = "alpha")
    val scale by animateFloatAsState(targetValue = if (visible) 1f else 0.98f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "scale")

    Box(modifier = modifier.graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale)) {
        content()
    }
}
