package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import chromahub.rhythm.app.util.ArtistSeparator
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSongMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistBottomSheet(
    artist: Artist,
    onDismiss: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddSongToPlaylist: (Song) -> Unit,
    onPlayerClick: () -> Unit,
    sheetState: SheetState,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onPlayNext: (Song) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    favoriteSongs: Set<String> = emptySet(),
    onShowSongInfo: (Song) -> Unit = {},
    onAddToBlacklist: (Song) -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    songs: List<Song>? = null,
    albums: List<Album>? = null,
    onAddToQueueAll: ((List<Song>) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: MusicViewModel = viewModel()
    val appSettings = remember { AppSettings.getInstance(context) }
    val groupByAlbumArtist by appSettings.groupByAlbumArtist.collectAsState()
    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val albumArtworkShape = rememberExpressiveShapeFor(
        target = ExpressiveShapeTarget.ALBUM_ART,
        fallbackShape = RoundedCornerShape(24.dp)
    )
    val songArtworkShape = rememberExpressiveShapeFor(
        target = ExpressiveShapeTarget.SONG_ART,
        fallbackShape = RoundedCornerShape(12.dp)
    )
    val configuration = LocalConfiguration.current

    val isTablet = configuration.screenWidthDp >= 600
    val isLandscapeTablet = isTablet && configuration.screenWidthDp > configuration.screenHeightDp

    val allSongs by viewModel.filteredSongs.collectAsState()
    val allAlbums by viewModel.albums.collectAsState()
    val displaySongs = songs ?: allSongs
    val displayAlbums = albums ?: allAlbums

    fun filterSongsForArtist(
        songs: List<Song>, artist: Artist, groupByAlbumArtist: Boolean,
        delimiters: String, enabled: Boolean
    ): List<Song> = songs.filter { song ->
        if (groupByAlbumArtist) {
            val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
            val songArtistNames = if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                ArtistSeparator.splitArtistNames(explicitAlbumArtist, delimiters, enabled)
            } else {
                ArtistSeparator.splitArtistNames(song.artist, delimiters, enabled)
            }
            songArtistNames.any { it.equals(artist.name, ignoreCase = true) }
        } else {
            ArtistSeparator.splitArtistNames(song.artist, delimiters, enabled)
                .any { it.equals(artist.name, ignoreCase = true) }
        }
    }

    fun filterAlbumsForArtist(
        albums: List<Album>, artist: Artist, groupByAlbumArtist: Boolean,
        delimiters: String, enabled: Boolean
    ): List<Album> = if (groupByAlbumArtist) {
        albums.filter { album ->
            album.songs.any { song ->
                val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
                val songArtistNames = if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                    ArtistSeparator.splitArtistNames(explicitAlbumArtist, delimiters, enabled)
                } else {
                    ArtistSeparator.splitArtistNames(song.artist, delimiters, enabled)
                }
                songArtistNames.any { it.equals(artist.name, ignoreCase = true) }
            }
        }
    } else {
        albums.filter { album ->
            album.songs.any { song ->
                ArtistSeparator.splitArtistNames(song.artist, delimiters, enabled)
                    .any { it.equals(artist.name, ignoreCase = true) }
            }
        }
    }

    val initialFilteredSongs = remember(displaySongs, artist, groupByAlbumArtist, artistSeparatorDelimiters, artistSeparatorEnabled) {
        filterSongsForArtist(displaySongs, artist, groupByAlbumArtist, artistSeparatorDelimiters, artistSeparatorEnabled)
    }
    val initialFilteredAlbums = remember(displayAlbums, artist, groupByAlbumArtist, artistSeparatorDelimiters, artistSeparatorEnabled) {
        filterAlbumsForArtist(displayAlbums, artist, groupByAlbumArtist, artistSeparatorDelimiters, artistSeparatorEnabled)
    }

    val artistSongs by produceState(initialValue = initialFilteredSongs,
        displaySongs, artist, groupByAlbumArtist, artistSeparatorEnabled, artistSeparatorDelimiters
    ) {
        value = withContext(Dispatchers.Default) {
            filterSongsForArtist(displaySongs, artist, groupByAlbumArtist, artistSeparatorDelimiters, artistSeparatorEnabled)
        }
    }

    val artistAlbums by produceState(initialValue = initialFilteredAlbums,
        displayAlbums, artist, groupByAlbumArtist, artistSeparatorEnabled, artistSeparatorDelimiters
    ) {
        value = withContext(Dispatchers.Default) {
            filterAlbumsForArtist(displayAlbums, artist, groupByAlbumArtist, artistSeparatorDelimiters, artistSeparatorEnabled)
        }
    }

    if (isLandscapeTablet) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.weight(0.4f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                modifier = Modifier.size(240.dp),
                                shape = RoundedCornerShape(32.dp),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .apply(ImageUtils.buildImageRequest(artist.artworkUri, artist.name, context.cacheDir, M3PlaceholderType.ARTIST))
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("${artistSongs.size} songs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text("${artistAlbums.size} albums", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                        color = Color.Transparent
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.artist_options),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = onDismiss,
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Close,
                                            contentDescription = "Close"
                                        )
                                    }
                                }
                            }

                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RhythmGroupedButton(
                                        size = RhythmButtonSize.Large
                                    ) {
                                        RhythmButtonWeighted(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                if (artistSongs.isNotEmpty()) {
                                                    onPlayAll(artistSongs)
                                                    onDismiss()
                                                    onPlayerClick()
                                                }
                                            },
                                            weight = 1f,
                                            isFirst = true,
                                            icon = RhythmIcons.Play,
                                            text = "Play All"
                                        )
                                        RhythmButtonWeighted(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                if (artistSongs.isNotEmpty()) {
                                                    onShufflePlay(artistSongs)
                                                    onDismiss()
                                                    onPlayerClick()
                                                }
                                            },
                                            weight = 1f,
                                            isLast = true,
                                            type = RhythmButtonType.Tonal,
                                            icon = RhythmIcons.Shuffle,
                                            text = stringResource(R.string.action_shuffle)
                                        )
                                    }

                                    if (onAddToQueueAll != null) {
                                        RhythmGroupedButton(
                                            size = RhythmButtonSize.Large
                                        ) {
                                            RhythmButtonWeighted(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    if (artistSongs.isNotEmpty()) {
                                                        onAddToQueueAll(artistSongs)
                                                        onDismiss()
                                                    }
                                                },
                                                weight = 1f,
                                                isFirst = true,
                                                isLast = true,
                                                type = RhythmButtonType.Tonal,
                                                icon = RhythmIcons.Queue,
                                                text = "Add to queue"
                                            )
                                        }
                                    }
                                }
                            }

                            if (artistAlbums.isNotEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp)
                                    ) {
                                        Text(
                                            text = context.getString(R.string.bottomsheet_albums),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(
                                                items = artistAlbums,
                                                key = { "artistalbum_tablet_${it.id}" }
                                            ) { album ->
                                                ArtistAlbumCard(
                                                    album = album,
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                        onAlbumClick(album)
                                                        onDismiss()
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
                            }

                            if (artistSongs.isNotEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 24.dp)
                                    ) {
                                        val songSettingsItems = artistSongs.map { song ->
                                            val isCurrentSong = currentSong?.id == song.id

                                            Material3SettingsItem(
                                                title = {
                                                    Text(
                                                        text = song.title,
                                                        color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                description = {
                                                    Text(
                                                        text = "${song.album} • ${formatDuration(song.duration, useHoursFormat)}",
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                leadingContent = {
                                                    Box {
                                                        Surface(
                                                            shape = songArtworkShape,
                                                            modifier = Modifier.size(48.dp),
                                                            tonalElevation = 2.dp
                                                        ) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .apply(ImageUtils.buildImageRequest(
                                                                        song.artworkUri,
                                                                        song.title,
                                                                        context.cacheDir,
                                                                        M3PlaceholderType.TRACK
                                                                    ))
                                                                    .build(),
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }

                                                        if (isCurrentSong && isPlaying) {
                                                            Surface(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .size(20.dp)
                                                                    .offset(x = 4.dp, y = 4.dp),
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shadowElevation = 2.dp
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    PlayingEqIcon(
                                                                        modifier = Modifier.size(width = 12.dp, height = 10.dp),
                                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                                        isPlaying = isPlaying,
                                                                        bars = 3
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                trailingContent = {
                                                    SongTrailingMenu(
                                                        song = song,
                                                        isFavorite = favoriteSongs.contains(song.id),
                                                        onPlayNext = { onPlayNext(song) },
                                                        onAddToQueue = { onAddToQueue(song) },
                                                        onToggleFavorite = { onToggleFavorite(song) },
                                                        onAddToPlaylist = { onAddSongToPlaylist(song) },
                                                        onShowSongInfo = { onShowSongInfo(song) },
                                                        haptics = haptics
                                                    )
                                                },
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    onSongClick(song)
                                                    onDismiss()
                                                    onPlayerClick()
                                                }
                                            )
                                        }

                                        Material3SettingsGroup(
                                            title = context.getString(R.string.bottomsheet_songs),
                                            items = songSettingsItems,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .fillMaxHeight()
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(390.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artist.artworkUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .apply(ImageUtils.buildImageRequest(
                                        artist.artworkUri,
                                        artist.name,
                                        context.cacheDir,
                                        M3PlaceholderType.ARTIST
                                    ))
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(20.dp)
                                    .graphicsLayer { alpha = 0.25f }
                            )
                        }

                        // Drag handle with animation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp),
                                shape = RoundedCornerShape(2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ) {}
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.6f to MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        1.0f to MaterialTheme.colorScheme.surface
                                    )
                                )
                        )

                        val artistArtworkShape = rememberExpressiveShapeFor(
                            target = ExpressiveShapeTarget.ARTIST_ART,
                            fallbackShape = CircleShape
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(130.dp),
                                shape = artistArtworkShape,
                                border = BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ),
                                shadowElevation = 12.dp
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .apply(ImageUtils.buildImageRequest(
                                            artist.artworkUri,
                                            artist.name,
                                            context.cacheDir,
                                            M3PlaceholderType.ARTIST
                                        ))
                                        .build(),
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = "${artistSongs.size} songs",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = "${artistAlbums.size} albums",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RhythmGroupedButton(
                                    size = RhythmButtonSize.Large
                                ) {
                                    RhythmButtonWeighted(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayAll(artistSongs)
                                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                    onDismiss()
                                                    onPlayerClick()
                                                }
                                            }
                                        },
                                        weight = 1f,
                                        isFirst = true,
                                        icon = RhythmIcons.Play,
                                        text = "Play All"
                                    )
                                    RhythmButtonWeighted(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            if (artistSongs.isNotEmpty()) {
                                                onShufflePlay(artistSongs)
                                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                    onDismiss()
                                                    onPlayerClick()
                                                }
                                            }
                                        },
                                        weight = 1f,
                                        isLast = true,
                                        type = RhythmButtonType.Tonal,
                                        icon = RhythmIcons.Shuffle,
                                        text = stringResource(R.string.action_shuffle)
                                    )
                                }

                                if (onAddToQueueAll != null) {
                                    RhythmGroupedButton(
                                        size = RhythmButtonSize.Large
                                    ) {
                                        RhythmButtonWeighted(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                if (artistSongs.isNotEmpty()) {
                                                    onAddToQueueAll(artistSongs)
                                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                        onDismiss()
                                                    }
                                                }
                                            },
                                            weight = 1f,
                                            isFirst = true,
                                            isLast = true,
                                            type = RhythmButtonType.Tonal,
                                            icon = RhythmIcons.Queue,
                                            text = "Add to queue"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (artistAlbums.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.bottomsheet_albums),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 12.dp)
                            )

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
                }

                if (artistSongs.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            val songSettingsItems = artistSongs.map { song ->
                                val isCurrentSong = currentSong?.id == song.id

                                Material3SettingsItem(
                                    title = {
                                        Text(
                                            text = song.title,
                                            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    description = {
                                        Text(
                                            text = "${song.album} • ${formatDuration(song.duration, useHoursFormat)}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        Box {
                                            Surface(
                                                shape = songArtworkShape,
                                                modifier = Modifier.size(48.dp),
                                                tonalElevation = 2.dp
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .apply(ImageUtils.buildImageRequest(
                                                            song.artworkUri,
                                                            song.title,
                                                            context.cacheDir,
                                                            M3PlaceholderType.TRACK
                                                        ))
                                                        .build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            if (isCurrentSong && isPlaying) {
                                                Surface(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(20.dp)
                                                        .offset(x = 4.dp, y = 4.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shadowElevation = 2.dp
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        PlayingEqIcon(
                                                            modifier = Modifier.size(width = 12.dp, height = 10.dp),
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            isPlaying = isPlaying,
                                                            bars = 3
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        SongTrailingMenu(
                                            song = song,
                                            isFavorite = favoriteSongs.contains(song.id),
                                            onPlayNext = { onPlayNext(song) },
                                            onAddToQueue = { onAddToQueue(song) },
                                            onToggleFavorite = { onToggleFavorite(song) },
                                            onAddToPlaylist = { onAddSongToPlaylist(song) },
                                            onShowSongInfo = { onShowSongInfo(song) },
                                            haptics = haptics
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        onSongClick(song)
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            onDismiss()
                                            onPlayerClick()
                                        }
                                    }
                                )
                            }

                            Material3SettingsGroup(
                                title = context.getString(R.string.bottomsheet_songs),
                                items = songSettingsItems,
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
private fun SongTrailingMenu(
    song: Song,
    isFavorite: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowSongInfo: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    var showDropdown by remember { mutableStateOf(false) }

    Box {
        FilledIconButton(
            onClick = {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                showDropdown = true
            },
            modifier = Modifier.size(width = 40.dp, height = 36.dp),
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = RhythmIcons.More,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
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
                onPlayNext = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showDropdown = false
                    onPlayNext()
                },
                onAddToQueue = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showDropdown = false
                    onAddToQueue()
                },
                isFavorite = isFavorite,
                onToggleFavorite = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showDropdown = false
                    onToggleFavorite()
                },
                onAddToPlaylist = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showDropdown = false
                    onAddToPlaylist()
                },
                onShowSongInfo = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showDropdown = false
                    onShowSongInfo()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current

    val albumArtworkShape = rememberExpressiveShapeFor(
        target = ExpressiveShapeTarget.ALBUM_ART,
        fallbackShape = RoundedCornerShape(24.dp)
    )

    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onClick()
        },
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(24.dp),
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
                        .apply(ImageUtils.buildImageRequest(
                            album.artworkUri,
                            album.title,
                            context.cacheDir,
                            M3PlaceholderType.ALBUM
                        ))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(albumArtworkShape)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    FilledIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onPlay()
                        },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${album.numberOfSongs} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}