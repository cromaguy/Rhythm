/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.R

import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.findAlbumForRoute
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.AudioQualityIcon
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.player.CanvasArtworkPlayer
import chromahub.rhythm.app.network.AppleMusicCanvasProvider
import chromahub.rhythm.app.network.WikipediaProvider
import chromahub.rhythm.app.network.CanvasArtwork
import chromahub.rhythm.app.shared.data.model.CanvasNetworkMode
import chromahub.rhythm.app.core.utils.NetworkUtils
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistChooserBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaylistSongOptionsBottomSheet
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortOption
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.util.ArtistSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

private enum class AlbumSortOrder {
    TRACK_NUMBER,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC
}

private data class AlbumSongDisplayState(
    val visibleSongs: List<Song> = emptyList(),
    val availableDiscs: List<Int> = emptyList(),
    val selectedDisc: Int = 0,
    val totalDuration: Long = 0L
)

private fun String.toAlbumSortOrder(): AlbumSortOrder {
    return runCatching { AlbumSortOrder.valueOf(this) }.getOrDefault(AlbumSortOrder.TRACK_NUMBER)
}

private fun prepareAlbumSongDisplayState(
    songs: List<Song>,
    sortOrder: AlbumSortOrder,
    libraryCombineDiscs: Boolean,
    savedDiscFilter: Int
): AlbumSongDisplayState {
    fun getEffectiveTrack(s: Song): Int = if (s.trackNumber >= 1000) s.trackNumber % 1000 else s.trackNumber
    fun getEffectiveDisc(s: Song): Int = if (s.trackNumber >= 1000) s.trackNumber / 1000 else s.discNumber.coerceAtLeast(1)

    val trackComparator = Comparator<Song> { a, b ->
        val aTrack = getEffectiveTrack(a)
        val bTrack = getEffectiveTrack(b)
        when {
            aTrack > 0 && bTrack > 0 -> aTrack.compareTo(bTrack)
            aTrack > 0 -> -1
            bTrack > 0 -> 1
            else -> a.title.compareTo(b.title, ignoreCase = true)
        }
    }

    fun sortByOrder(albumSongs: List<Song>): List<Song> {
        return when (sortOrder) {
            AlbumSortOrder.TRACK_NUMBER -> albumSongs.sortedWith(trackComparator)
            AlbumSortOrder.TITLE_ASC -> albumSongs.sortedBy { it.title.lowercase() }
            AlbumSortOrder.TITLE_DESC -> albumSongs.sortedByDescending { it.title.lowercase() }
            AlbumSortOrder.DURATION_ASC -> albumSongs.sortedBy { it.duration }
            AlbumSortOrder.DURATION_DESC -> albumSongs.sortedByDescending { it.duration }
        }
    }

    val sortedSongs = if (libraryCombineDiscs) {
        sortByOrder(songs)
    } else {
        songs
            .groupBy { getEffectiveDisc(it) }
            .toSortedMap()
            .values
            .flatMap { discSongs -> sortByOrder(discSongs) }
    }

    val availableDiscs = songs
        .map { getEffectiveDisc(it) }
        .distinct()
        .sorted()
    val shouldShowDiscFilter = !libraryCombineDiscs && availableDiscs.size > 1
    val selectedDisc = if (shouldShowDiscFilter && savedDiscFilter in availableDiscs) {
        savedDiscFilter
    } else {
        0
    }
    val visibleSongs = if (selectedDisc == 0) {
        sortedSongs
    } else {
        sortedSongs.filter { getEffectiveDisc(it) == selectedDisc }
    }
    return AlbumSongDisplayState(
        visibleSongs = visibleSongs,
        availableDiscs = availableDiscs,
        selectedDisc = selectedDisc,
        totalDuration = sortedSongs.sumOf { it.duration }
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongClickInContext: (Song, List<Song>) -> Unit = { song, _ -> onSongClick(song) },
    onPlayAll: (List<Song>) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddSongToPlaylist: (Song) -> Unit,
    onPlayerClick: () -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    favoriteSongs: Set<String> = emptySet(),
    onShowSongInfo: (Song) -> Unit = {},
    onAddToBlacklist: (Song) -> Unit = {},
    onShare: (Song) -> Unit = {},
    onGoToAlbum: (Song) -> Unit = {},
    onGoToArtist: (Song) -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    albumOverride: Album? = null,
    songsOverride: List<Song>? = null,
    isContentLoadingOverride: Boolean? = null,
    onEditAlbum: ((
        title: String,
        artist: String,
        artworkUri: Uri?,
        removeArtwork: Boolean,
        onProgress: (Int, Int) -> Unit,
        onComplete: (successCount: Int, failCount: Int) -> Unit
    ) -> Unit)? = null,
    isStreamingMode: Boolean = false,
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isTablet = windowScreenWidthDp() >= 600
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()

    val appSettings = remember { AppSettings.getInstance(context) }
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()
    val savedSortOrder by appSettings.albumSortOrder.collectAsState()
    val savedDiscFilter by appSettings.albumBottomSheetDiscFilter.collectAsState()
    val libraryCombineDiscs by appSettings.libraryCombineDiscs.collectAsState()
    val albumScreenGradientBlur by appSettings.albumBottomSheetGradientBlur.collectAsState()

    val allAlbums by viewModel.albums.collectAsState()
    val allArtists by viewModel.artists.collectAsState()
    val allLibrarySongs by viewModel.filteredSongs.collectAsState()
    val album = remember(allAlbums, albumId, albumName, albumOverride) {
        albumOverride ?: allAlbums.findAlbumForRoute(albumId, albumName)
    }

    val allDisplaySongs = remember(album, songsOverride, allLibrarySongs, albumId, albumName) {
        songsOverride ?: album?.songs?.takeIf { it.isNotEmpty() } ?: run {
            val matchingSongs = allLibrarySongs.filter { song ->
                (albumId.isNotBlank() && song.albumId.trim() == albumId.trim()) ||
                (albumName.isNotBlank() && song.album.trim().equals(albumName.trim(), ignoreCase = true))
            }
            matchingSongs.takeIf { it.isNotEmpty() } ?: album?.songs ?: emptyList()
        }
    }

    val sortOrder = remember(savedSortOrder) { savedSortOrder.toAlbumSortOrder() }
    val songDisplayState = remember(allDisplaySongs, sortOrder, libraryCombineDiscs, savedDiscFilter) {
        prepareAlbumSongDisplayState(
            songs = allDisplaySongs,
            sortOrder = sortOrder,
            libraryCombineDiscs = libraryCombineDiscs,
            savedDiscFilter = savedDiscFilter
        )
    }
    val displaySongs = songDisplayState.visibleSongs
    val availableDiscs = songDisplayState.availableDiscs
    val selectedDisc = songDisplayState.selectedDisc
    val shouldShowDiscFilter = !libraryCombineDiscs && availableDiscs.size > 1

    // Multi-artist picker state
    var showArtistPicker by remember { mutableStateOf(false) }
    var artistPickerCandidates by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var artistPickerSong by remember { mutableStateOf<Song?>(null) }

    val effectiveDelimiters = artistSeparatorDelimiters.ifBlank { AppSettings.DEFAULT_ARTIST_SEPARATOR_DELIMITERS }

    fun handleArtistTap(song: Song) {
        val candidates = ArtistSeparator.splitArtistNames(
            song.artist,
            delimiters = effectiveDelimiters,
            enabled = artistSeparatorEnabled
        )
        if (candidates.size > 1) {
            artistPickerCandidates = candidates.map { artistName ->
                allArtists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                    ?: Artist(
                        id = artistName,
                        name = artistName,
                        songs = listOf(song),
                        numberOfTracks = 1
                    )
            }
            artistPickerSong = song
            showArtistPicker = true
        } else {
            onGoToArtist(song)
        }
    }

    val wikipediaApiEnabled by appSettings.wikipediaApiEnabled.collectAsState()
    var description by remember(albumId) { mutableStateOf<String?>(null) }
    var isDescriptionLoading by remember(albumId) { mutableStateOf(false) }

    LaunchedEffect(albumId, albumName, album?.artist, allDisplaySongs, wikipediaApiEnabled) {
        val fallbackArtist = allDisplaySongs.firstOrNull()?.artist
        val effectiveArtistName = album?.artist?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: fallbackArtist?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }

        if (albumName.isNotBlank()) {
            isDescriptionLoading = true
            withContext(Dispatchers.IO) {
                var desc: String? = null
                if (effectiveArtistName != null) {
                    desc = AppleMusicCanvasProvider.getAlbumDescription(albumName, effectiveArtistName)
                }
                if (desc.isNullOrBlank() && wikipediaApiEnabled) {
                    desc = WikipediaProvider.getAlbumDescription(albumName, effectiveArtistName)
                }
                withContext(Dispatchers.Main) {
                    description = desc
                    isDescriptionLoading = false
                }
            }
        }
    }

    val appleCanvasEnabled by appSettings.appleCanvasEnabled.collectAsState()
    val appleCanvasNetworkMode by appSettings.appleCanvasNetworkMode.collectAsState()
    var canvasArtwork by remember(albumId) { mutableStateOf<CanvasArtwork?>(null) }
    var canvasLoading by remember(albumId) { mutableStateOf(false) }

    LaunchedEffect(albumId, albumName, album?.artist, allDisplaySongs, appleCanvasEnabled, appleCanvasNetworkMode) {
        canvasArtwork = null
        canvasLoading = false

        val artistName = album?.artist ?: allDisplaySongs.firstOrNull()?.artist
        if (albumName.isNotBlank() && artistName != null && appleCanvasEnabled) {
            val hasNetwork = if (appleCanvasNetworkMode == CanvasNetworkMode.WIFI_ONLY) {
                NetworkUtils.isWifiConnected(context)
            } else {
                NetworkUtils.isNetworkAvailable(context)
            }
            if (hasNetwork) {
                canvasLoading = true
                val result = withContext(Dispatchers.IO) {
                    AppleMusicCanvasProvider.getByAlbumArtist(album = albumName, artist = artistName)
                }
                canvasLoading = false
                canvasArtwork = result
            }
        }
    }

    var showSongOptionsSheet by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    var addToQueuePressed by remember { mutableStateOf(false) }
    val addToQueueScale by animateFloatAsState(targetValue = if (addToQueuePressed) 0.96f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "addToQueueScale")

    LaunchedEffect(addToQueuePressed) { if (addToQueuePressed) { delay(150); addToQueuePressed = false } }

    val totalDuration = songDisplayState.totalDuration
    val aggregatedArtists = remember(allDisplaySongs, effectiveDelimiters, artistSeparatorEnabled) {
        allDisplaySongs.flatMap { song ->
            ArtistSeparator.splitArtistNames(
                song.artist,
                delimiters = effectiveDelimiters,
                enabled = artistSeparatorEnabled
            )
        }.distinct().sorted()
    }
    val displayArtist = aggregatedArtists.joinToString(", ").ifEmpty {
        album?.artist ?: allDisplaySongs.firstOrNull()?.artist ?: "Unknown Artist"
    }
    val displayArtworkUri = album?.artworkUri ?: allDisplaySongs.firstNotNullOfOrNull { it.artworkUri }
    val hasCanvas = appleCanvasEnabled && canvasArtwork != null
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLoading = isContentLoadingOverride ?: (album == null && allDisplaySongs.isEmpty())

    if (isLandscapeTablet) {
        // Animated infinite transition for backdrop orbs (like full-screen lyrics view)
        val infiniteTransition = rememberInfiniteTransition(label = "tabletBackdrop")
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
                // Full-screen blurred backdrop (like FullScreenLyricsView)
                if (albumScreenGradientBlur) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(56.dp)
                            .alpha(0.68f)
                    ) {
                        // Blurred base album art
                        if (displayArtworkUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).apply(ImageUtils.buildImageRequest(displayArtworkUri, albumName, context.cacheDir, M3PlaceholderType.ALBUM)).build(),
                                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Animated canvas blurred along with backdrop
                        if (hasCanvas) {
                            CanvasArtworkPlayer(
                                primaryUrl = canvasArtwork?.animated,
                                fallbackUrl = canvasArtwork?.videoUrl,
                                alwaysPlay = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Moving gradient aura 1
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
                        // Moving gradient aura 2
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
                // Dim layer for readability
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
                    Surface(modifier = Modifier.weight(0.4f).fillMaxHeight(), color = Color.Transparent) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(50))
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
                            }
                            Spacer(modifier = Modifier.height(24.dp))

                            // Artwork card — canvas used as full backdrop, not inside the card
                            Surface(
                                modifier = Modifier.size(300.dp),
                                shape = RoundedCornerShape(32.dp),
                                shadowElevation = 16.dp
                            ) {
                                if (displayArtworkUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).apply(ImageUtils.buildImageRequest(displayArtworkUri, albumName, context.cacheDir, M3PlaceholderType.ALBUM)).build(),
                                        contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))))
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                            Text(text = albumName, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = displayArtist,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable {
                                    val song = allDisplaySongs.firstOrNull()
                                    if (song != null) handleArtistTap(song)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${allDisplaySongs.size} tracks • ${formatDuration(allDisplaySongs.sumOf { it.duration }, useHoursFormat)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                allDisplaySongs.firstOrNull()?.let { firstSong ->
                                    AudioQualityIcon(
                                        song = firstSong,
                                        iconSize = 20.dp,
                                        padding = 0.dp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RhythmDetailActionButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onPlayAll(displaySongs)
                                    },
                                    height = 50.dp,
                                    isFirst = true,
                                    isLast = false,
                                    icon = RhythmIcons.Play,
                                    text = stringResource(R.string.action_play_all),
                                    fontWeight = FontWeight.Bold
                                )

                                RhythmDetailActionButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onShufflePlay(displaySongs)
                                    },
                                    height = 50.dp,
                                    type = RhythmButtonType.Tonal,
                                    isFirst = false,
                                    isLast = true,
                                    icon = RhythmIcons.Shuffle,
                                    text = stringResource(R.string.action_shuffle),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Surface(modifier = Modifier.weight(0.6f).fillMaxHeight(), color = Color.Transparent) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                M3CircularLoader(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 5f)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 32.dp, bottom = 32.dp)
                            ) {
                                if (description != null) {
                                    AboutAlbumSection(
                                        description = description!!,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                AlbumListControls(
                                    shouldShowDiscFilter = shouldShowDiscFilter,
                                    selectedDisc = selectedDisc,
                                    availableDiscs = availableDiscs,
                                    onDiscSelected = { disc ->
                                        appSettings.setAlbumBottomSheetDiscFilter(disc)
                                    },
                                    sortOrder = sortOrder,
                                    showSortMenu = showSortMenu,
                                    onShowSortMenu = { showSortMenu = true },
                                    onDismissSortMenu = { showSortMenu = false },
                                    onSortOrderSelected = { newOrder ->
                                        appSettings.setAlbumSortOrder(newOrder.name)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    itemsIndexed(displaySongs, key = { _, song -> song.id }) { index, song ->
                                        AnimateIn {
                                            AlbumSongItem(
                                                song = song,
                                                index = index,
                                                totalCount = displaySongs.size,
                                                currentSong = currentSong,
                                                isPlaying = isPlaying,
                                                useHoursFormat = useHoursFormat,
                                                onClick = { onSongClickInContext(song, displaySongs) },
                                                onMoreClick = {
                                                    selectedSongForOptions = song
                                                    showSongOptionsSheet = true
                                                }
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
    } else {
        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            topAppBarState,
            canScroll = { true }
        )
        Box(modifier = Modifier.fillMaxSize()) {
            val collapsedFraction = scrollBehavior.state.collapsedFraction

            // Fixed background artwork — smoothly fades out on scroll
            val expandedAlpha = ((0.65f - collapsedFraction) / 0.4f).coerceIn(0f, 1f)
            if (expandedAlpha > 0.01f && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = expandedAlpha
                            // Zoom in effect: art scales up as user scrolls down
                            scaleX = 1f + collapsedFraction * 0.15f
                            scaleY = 1f + collapsedFraction * 0.15f
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    ) {
                        if (displayArtworkUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .apply(ImageUtils.buildImageRequest(displayArtworkUri, albumName, context.cacheDir, M3PlaceholderType.ALBUM))
                                    .build(),
                                contentDescription = null,
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

                        if (hasCanvas) {
                            CanvasArtworkPlayer(
                                primaryUrl = canvasArtwork?.animated,
                                fallbackUrl = canvasArtwork?.videoUrl,
                                alwaysPlay = true,
                                modifier = Modifier.fillMaxSize()
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
                    }

                    // Album info — bottom aligned, slides up with collapse
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(24.dp)
                            .graphicsLayer {
                                translationY = -collapsedFraction * 120f
                            }
                    ) {
                        // Album name — multiline handled with maxLines=3
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Merge artist + tracks together, year+quality BIG on right spanning both lines
                        val albumYear = album?.year ?: allDisplaySongs.firstNotNullOfOrNull { it.year.takeIf { y -> y > 0 } }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left column: artist on top, tracks below (entire column clickable)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val song = allDisplaySongs.firstOrNull()
                                        if (song != null) handleArtistTap(song)
                                    }
                            ) {
                                // Artist name — accent/primary color
                                Text(
                                    text = displayArtist,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // Track count + duration
                                Text(
                                    text = "${allDisplaySongs.size} tracks • ${formatDuration(totalDuration, useHoursFormat)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Right side: BIG year + quality icon, spanning both lines
                            if (albumYear != null && albumYear > 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = albumYear.toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f),
                                    )
                                }
                            }
                            allDisplaySongs.firstOrNull()?.let { firstSong ->
                                Spacer(modifier = Modifier.width(6.dp))
                                AudioQualityIcon(
                                    song = firstSong,
                                    iconSize = 32.dp,
                                    padding = 0.dp
                                )
                            }
                        }
                    }
                }
            }

            // Loading state
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    M3CircularLoader(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 5f)
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

            // Sort menu state
            val currentKey = when (sortOrder) {
                AlbumSortOrder.TRACK_NUMBER -> "TRACK_NUMBER"
                AlbumSortOrder.TITLE_ASC, AlbumSortOrder.TITLE_DESC -> "TITLE"
                AlbumSortOrder.DURATION_ASC, AlbumSortOrder.DURATION_DESC -> "DURATION"
            }
            val isAscending = when (sortOrder) {
                AlbumSortOrder.TITLE_DESC, AlbumSortOrder.DURATION_DESC -> false
                else -> true
            }
            val sortOptions = remember(context) {
                listOf(
                    RhythmSortOption("TRACK_NUMBER", context.getString(R.string.sort_track_number), RhythmIcons.FormatListNumbered),
                    RhythmSortOption("TITLE", context.getString(R.string.library_sort_title), RhythmIcons.SortByAlpha),
                    RhythmSortOption("DURATION", context.getString(R.string.sort_duration), RhythmIcons.AccessTime)
                )
            }

            // Scaffold with transparent topBar
            Scaffold(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        LargeTopAppBar(
                            title = {
                                // Smoothly fade in the collapsed title — starts at ~20% collapse, full at ~60%
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
                                            text = albumName,
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
                                    onClick = onBack,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(50))
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
                                                selectedKey = currentKey,
                                                isAscending = isAscending,
                                                options = sortOptions,
                                                onKeySelected = { key ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    val newOrder = when (key) {
                                                        "TRACK_NUMBER" -> AlbumSortOrder.TRACK_NUMBER
                                                        "TITLE" -> if (isAscending) AlbumSortOrder.TITLE_ASC else AlbumSortOrder.TITLE_DESC
                                                        "DURATION" -> if (isAscending) AlbumSortOrder.DURATION_ASC else AlbumSortOrder.DURATION_DESC
                                                        else -> AlbumSortOrder.TRACK_NUMBER
                                                    }
                                                    appSettings.setAlbumSortOrder(newOrder.name)
                                                    showSortMenu = false
                                                },
                                                onDirectionToggled = { asc ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    val newOrder = when (currentKey) {
                                                        "TRACK_NUMBER" -> AlbumSortOrder.TRACK_NUMBER
                                                        "TITLE" -> if (asc) AlbumSortOrder.TITLE_ASC else AlbumSortOrder.TITLE_DESC
                                                        "DURATION" -> if (asc) AlbumSortOrder.DURATION_ASC else AlbumSortOrder.DURATION_DESC
                                                        else -> AlbumSortOrder.TRACK_NUMBER
                                                    }
                                                    appSettings.setAlbumSortOrder(newOrder.name)
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
                val density = LocalDensity.current
                val collapsedTopPadding = paddingValues.calculateTopPadding()
                // Interpolate content top padding between artwork height (expanded) and top bar height (collapsed)
                val dynamicTopPadding = 450.dp + (collapsedTopPadding - 450.dp) * collapsedFraction

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = dynamicTopPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = (LocalMiniPlayerPadding.current.calculateBottomPadding() + 24.dp).coerceAtLeast(100.dp)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!isLoading) {
                            // Play/Shuffle buttons
                            item {
                                AnimatedVisibility(
                                    visible = allDisplaySongs.isNotEmpty(),
                                    enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(animationSpec = tween(300)),
                                    exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(animationSpec = tween(200))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .padding(top = 12.dp, bottom = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            RhythmDetailActionButton(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    onPlayAll(displaySongs)
                                                },
                                                height = 52.dp,
                                                isFirst = true,
                                                isLast = false,
                                                icon = RhythmIcons.Play,
                                                text = stringResource(R.string.action_play_all),
                                                fontWeight = FontWeight.Bold
                                            )

                                            RhythmDetailActionButton(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    onShufflePlay(displaySongs)
                                                },
                                                height = 52.dp,
                                                type = RhythmButtonType.Tonal,
                                                isFirst = false,
                                                isLast = true,
                                                icon = RhythmIcons.Shuffle,
                                                text = stringResource(R.string.action_shuffle),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                addToQueuePressed = true
                                                displaySongs.forEach { onAddToQueue(it) }
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
                                            enabled = displaySongs.isNotEmpty()
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
                            }

                            // Disc filter
                            if (shouldShowDiscFilter) {
                                item {
                                    AlbumDiscFilterChips(
                                        selectedDisc = selectedDisc,
                                        availableDiscs = availableDiscs,
                                        onDiscSelected = { disc ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            appSettings.setAlbumBottomSheetDiscFilter(disc)
                                        }
                                    )
                                }
                            }

                            // About Album Section
                            if (description != null) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AboutAlbumSection(
                                        description = description!!,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }

                            // Songs Section Header
                            if (displaySongs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AlbumSongsSectionHeader(
                                        songsCount = displaySongs.size,
                                        context = context
                                    )
                                }
                            }

                            // Songs
                            itemsIndexed(displaySongs, key = { _, song -> song.id }) { index, song ->
                                AnimateIn {
                                    AlbumSongItem(
                                        song = song,
                                        index = index,
                                        totalCount = displaySongs.size,
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        useHoursFormat = useHoursFormat,
                                        onClick = { onSongClickInContext(song, displaySongs) },
                                        onMoreClick = {
                                            selectedSongForOptions = song
                                            showSongOptionsSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val headerBlendAlpha by animateFloatAsState(
                        targetValue = ((collapsedFraction - 0.65f) / 0.35f).coerceIn(0f, 1f),
                        animationSpec = tween(250),
                        label = "albumHeaderBlendAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(24.dp)
                            .graphicsLayer { alpha = headerBlendAlpha }
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        backgroundColor,
                                        backgroundColor.copy(alpha = 0.72f),
                                        backgroundColor.copy(alpha = 0.32f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    // Multi-artist picker
    if (showArtistPicker && artistPickerCandidates.isNotEmpty()) {
        ArtistChooserBottomSheet(
            candidateArtists = artistPickerCandidates,
            onDismiss = { showArtistPicker = false },
            onArtistSelected = { artist ->
                showArtistPicker = false
                val song = artistPickerSong
                if (song != null) {
                    onGoToArtist(song.copy(artist = artist.name))
                }
            },
            haptic = haptics
        )
    }

    if (showSongOptionsSheet && selectedSongForOptions != null) {
        PlaylistSongOptionsBottomSheet(
            song = selectedSongForOptions!!,
            onDismiss = { showSongOptionsSheet = false },
            onShare = {
                onShare(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            onRemoveFromPlaylist = { },
            onPlayNext = {
                onPlayNext(selectedSongForOptions!!)
                showSongOptionsSheet = false
                Toast.makeText(context, context.getString(R.string.will_play_next, selectedSongForOptions!!.title), Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                onAddToQueue(selectedSongForOptions!!)
                showSongOptionsSheet = false
                Toast.makeText(context, context.getString(R.string.added_to_queue, selectedSongForOptions!!.title), Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                onAddSongToPlaylist(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            onShowSongInfo = {
                onShowSongInfo(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            onGoToAlbum = { },
            onGoToArtist = {
                val song = selectedSongForOptions!!
                showSongOptionsSheet = false
                handleArtistTap(song)
            },
            showRemoveFromPlaylist = false,
            showGoToAlbum = false,
            isStreamingMode = isStreamingMode,
            onDeleteSong = {
                viewModel.deleteSong(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            haptics = haptics
        )
    }
}

@Composable
private fun AlbumDiscFilterChips(
    selectedDisc: Int,
    availableDiscs: List<Int>,
    onDiscSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp)
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedDisc == 0,
                onClick = { onDiscSelected(0) },
                label = {
                    Text(
                        text = stringResource(R.string.bottomsheet_all_discs),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = if (selectedDisc == 0) {
                    {
                        Icon(
                            imageVector = RhythmIcons.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }

        items(availableDiscs) { disc ->
            FilterChip(
                selected = selectedDisc == disc,
                onClick = { onDiscSelected(disc) },
                label = {
                    Text(
                        text = stringResource(R.string.bottomsheet_disc_option, disc),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = if (selectedDisc == disc) {
                    {
                        Icon(
                            imageVector = RhythmIcons.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun AlbumListControls(
    shouldShowDiscFilter: Boolean,
    selectedDisc: Int,
    availableDiscs: List<Int>,
    onDiscSelected: (Int) -> Unit,
    sortOrder: AlbumSortOrder,
    showSortMenu: Boolean,
    onShowSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortOrderSelected: (AlbumSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val currentKey = when (sortOrder) {
        AlbumSortOrder.TRACK_NUMBER -> "TRACK_NUMBER"
        AlbumSortOrder.TITLE_ASC, AlbumSortOrder.TITLE_DESC -> "TITLE"
        AlbumSortOrder.DURATION_ASC, AlbumSortOrder.DURATION_DESC -> "DURATION"
    }
    val isAscending = when (sortOrder) {
        AlbumSortOrder.TITLE_DESC, AlbumSortOrder.DURATION_DESC -> false
        else -> true
    }
    val sortOptions = remember(context) {
        listOf(
            RhythmSortOption("TRACK_NUMBER", context.getString(R.string.sort_track_number), RhythmIcons.FormatListNumbered),
            RhythmSortOption("TITLE", context.getString(R.string.library_sort_title), RhythmIcons.SortByAlpha),
            RhythmSortOption("DURATION", context.getString(R.string.sort_duration), RhythmIcons.AccessTime)
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (shouldShowDiscFilter) {
            AlbumDiscFilterChips(
                selectedDisc = selectedDisc,
                availableDiscs = availableDiscs,
                onDiscSelected = { disc ->
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onDiscSelected(disc)
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Box {
            FilledTonalIconButton(
                onClick = onShowSortMenu,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
                onDismissRequest = onDismissSortMenu,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .widthIn(min = 250.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp)
            ) {
                RhythmSortMenuContent(
                    selectedKey = currentKey,
                    isAscending = isAscending,
                    options = sortOptions,
                    onKeySelected = { key ->
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        val newOrder = when (key) {
                            "TRACK_NUMBER" -> AlbumSortOrder.TRACK_NUMBER
                            "TITLE" -> if (isAscending) AlbumSortOrder.TITLE_ASC else AlbumSortOrder.TITLE_DESC
                            "DURATION" -> if (isAscending) AlbumSortOrder.DURATION_ASC else AlbumSortOrder.DURATION_DESC
                            else -> AlbumSortOrder.TRACK_NUMBER
                        }
                        onSortOrderSelected(newOrder)
                    },
                    onDirectionToggled = { asc ->
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        val newOrder = when (currentKey) {
                            "TRACK_NUMBER" -> AlbumSortOrder.TRACK_NUMBER
                            "TITLE" -> if (asc) AlbumSortOrder.TITLE_ASC else AlbumSortOrder.TITLE_DESC
                            "DURATION" -> if (asc) AlbumSortOrder.DURATION_ASC else AlbumSortOrder.DURATION_DESC
                            else -> AlbumSortOrder.TRACK_NUMBER
                        }
                        onSortOrderSelected(newOrder)
                    }
                )
            }
        }
    }
}

@Composable
private fun AboutAlbumSection(
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
                text = stringResource(R.string.albumdetail_about_album),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumSongItem(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    useHoursFormat: Boolean = false,
    index: Int = 0,
    totalCount: Int = 0,
    itemShape: RoundedCornerShape? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isCurrentSong = currentSong?.id == song.id

    val containerColor by animateColorAsState(
        targetValue = when {
            isCurrentSong -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "containerColor"
    )

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = itemShape ?: groupedAlbumItemShape(index, totalCount),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art with expressive shape — matches library song items
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
                        text = song.artist,
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

            // 3-dot button matching library screen style: FilledIconButton, primaryContainer, 32×44dp, pill shape
            FilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onMoreClick()
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
        }
    }
}

private fun groupedAlbumItemShape(index: Int, totalCount: Int): RoundedCornerShape {
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

@Composable
private fun AlbumSongsSectionHeader(
    songsCount: Int,
    context: android.content.Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
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
