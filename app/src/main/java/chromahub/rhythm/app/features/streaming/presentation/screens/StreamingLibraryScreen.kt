@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package chromahub.rhythm.app.features.streaming.presentation.screens

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import chromahub.rhythm.app.features.local.presentation.screens.calculateSongCategories
import chromahub.rhythm.app.features.local.presentation.screens.filterSongsByCategory
import kotlin.math.abs

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSongMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortOption
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import chromahub.rhythm.app.shared.data.model.AlbumViewType
import chromahub.rhythm.app.shared.data.model.ArtistViewType
import chromahub.rhythm.app.shared.data.model.PlaylistViewType
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.screens.SingleCardAlbumsContent
import chromahub.rhythm.app.features.local.presentation.screens.SingleCardArtistsContent
import chromahub.rhythm.app.features.local.presentation.screens.PlaylistFabMenu
import chromahub.rhythm.app.features.local.presentation.screens.SingleCardPlaylistsContent
import chromahub.rhythm.app.features.local.presentation.screens.SingleCardSongsContent

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.activity.compose.BackHandler
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.MultiSelectionBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.features.local.presentation.viewmodel.MultiSelectionStateHolder
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.presentation.model.StreamingServiceOptions
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.TabAnimation
import chromahub.rhythm.app.util.ArtistSeparator
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.ui.platform.LocalConfiguration
import chromahub.rhythm.app.ui.theme.MusicDimensions
import kotlin.random.Random

private enum class StreamingLibraryTab(@param:StringRes val titleRes: Int, val icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon) {
    SONGS(R.string.library_tab_songs, MaterialSymbolIcon("history", filled = true)),
    ALBUMS(R.string.library_tab_albums, RhythmIcons.AlbumFilled),
    ARTISTS(R.string.library_tab_artists, RhythmIcons.ArtistFilled),
    PLAYLISTS(R.string.library_tab_playlists, RhythmIcons.Queue)
}

private enum class StreamingSongSortOrder(
    @param:StringRes val labelRes: Int,
    val ascending: Boolean,
    val icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
) {
    TITLE_ASC(R.string.sort_title, true, RhythmIcons.Sort),
    TITLE_DESC(R.string.sort_title, false, RhythmIcons.Sort),
    ARTIST_ASC(R.string.sort_artist, true, RhythmIcons.ArtistFilled),
    ARTIST_DESC(R.string.sort_artist, false, RhythmIcons.ArtistFilled),
    ALBUM_ASC(R.string.metadata_album, true, RhythmIcons.AlbumFilled),
    ALBUM_DESC(R.string.metadata_album, false, RhythmIcons.AlbumFilled),
    DURATION_ASC(R.string.sort_duration_short_first, true, MaterialSymbolIcon("history", filled = true)),
    DURATION_DESC(R.string.sort_duration_long_first, false, MaterialSymbolIcon("history", filled = true))
}

private enum class StreamingAlbumSortOrder(
    @param:StringRes val labelRes: Int,
    val ascending: Boolean,
    val icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
) {
    TITLE_ASC(R.string.sort_title, true, RhythmIcons.Sort),
    TITLE_DESC(R.string.sort_title, false, RhythmIcons.Sort),
    ARTIST_ASC(R.string.sort_artist, true, RhythmIcons.ArtistFilled),
    ARTIST_DESC(R.string.sort_artist, false, RhythmIcons.ArtistFilled),
    YEAR_ASC(R.string.metadata_year, true, MaterialSymbolIcon("history", filled = true)),
    YEAR_DESC(R.string.metadata_year, false, MaterialSymbolIcon("history", filled = true)),
    TRACK_COUNT_ASC(R.string.sort_song_count, true, RhythmIcons.Queue),
    TRACK_COUNT_DESC(R.string.sort_song_count, false, RhythmIcons.Queue)
}

private enum class StreamingArtistSortOrder(
    @param:StringRes val labelRes: Int,
    val icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    val ascending: Boolean
) {
    NAME_ASC(R.string.sort_name, RhythmIcons.ArtistFilled, true),
    NAME_DESC(R.string.sort_name, RhythmIcons.ArtistFilled, false),
    SONG_COUNT_ASC(R.string.sort_song_count, RhythmIcons.Queue, true),
    SONG_COUNT_DESC(R.string.sort_song_count, RhythmIcons.Queue, false),
    ALBUM_COUNT_ASC(R.string.bottomsheet_albums, RhythmIcons.AlbumFilled, true),
    ALBUM_COUNT_DESC(R.string.bottomsheet_albums, RhythmIcons.AlbumFilled, false),
    POPULARITY_DESC(R.string.bottomsheet_sort_by, RhythmIcons.TrendingUp, false),
    POPULARITY_ASC(R.string.bottomsheet_sort_by, RhythmIcons.TrendingUp, true)
}

private enum class StreamingPlaylistSortOrder(
    @param:StringRes val labelRes: Int,
    val ascending: Boolean,
    val icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
) {
    NAME_ASC(R.string.sort_name, true, RhythmIcons.Queue),
    NAME_DESC(R.string.sort_name, false, RhythmIcons.Queue),
    TRACK_COUNT_ASC(R.string.sort_song_count, true, RhythmIcons.Queue),
    TRACK_COUNT_DESC(R.string.sort_song_count, false, RhythmIcons.Queue)
}

@Composable
fun StreamingLibraryScreen(
    viewModel: StreamingMusicViewModel,
    localMusicViewModel: chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel? = null,
    onConfigureService: (String) -> Unit,
    onNavigateToArtist: (StreamingArtist) -> Unit,
    onNavigateToPlaylist: (StreamingPlaylist) -> Unit,
    onNavigateToAlbum: (StreamingAlbum) -> Unit,
    onAddSongToPlaylist: (StreamingSong) -> Unit = {},
    activeSongId: String? = null,
    isPlayerPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val scope = rememberCoroutineScope()

    val selectedService by appSettings.streamingService.collectAsState()
    val sessions by viewModel.serviceSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadedLibrary by viewModel.hasLoadedLibrary.collectAsState()
    val hasLoadedHomeContent by viewModel.hasLoadedHomeContent.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentStreamingSong by viewModel.currentSong.collectAsState()
    val isPlayerPlaying by viewModel.isPlaying.collectAsState()

    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val savedAlbums by viewModel.savedAlbums.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val savedPlaylists by viewModel.savedPlaylists.collectAsState()
    val featuredPlaylists by viewModel.featuredPlaylists.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val newReleases by viewModel.newReleases.collectAsState()
    val groupByAlbumArtist by appSettings.groupByAlbumArtist.collectAsState()
    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()

    val resolvedServiceId = remember(selectedService, sessions) {
        when {
            sessions[selectedService]?.isConnected == true -> selectedService
            else -> sessions.entries.firstOrNull { it.value.isConnected }?.key ?: selectedService
        }
    }

    val selectedOption = remember(resolvedServiceId) {
        StreamingServiceOptions.defaults.firstOrNull { it.id == resolvedServiceId }
    }
    val selectedServiceName = selectedOption?.let { context.getString(it.nameRes) }
        ?: context.getString(R.string.streaming_not_selected)
    val isSelectedServiceConnected = sessions[resolvedServiceId]?.isConnected == true
    val configureTargetServiceId = remember(resolvedServiceId) {
        if (resolvedServiceId.isNotBlank()) {
            resolvedServiceId
        } else {
            StreamingServiceOptions.defaults.firstOrNull()?.id.orEmpty()
        }
    }

    val allSongs by viewModel.allSongs.collectAsState()
    val librarySongs = remember(allSongs, likedSongs, downloadedSongs, recommendations) {
        if (allSongs.isNotEmpty()) {
            allSongs
        } else {
            (likedSongs + downloadedSongs + recommendations).distinctBy { it.id }
        }
    }
    val libraryAlbums = remember(savedAlbums, newReleases) {
        if (savedAlbums.isNotEmpty()) {
            savedAlbums
        } else {
            newReleases
        }
    }
    val libraryArtists = followedArtists
    val libraryPlaylists = remember(savedPlaylists, featuredPlaylists) {
        (savedPlaylists + featuredPlaylists).distinctBy { it.id }
    }
    // Use provider albums directly - do NOT derive from songs to preserve year and prevent splitting
    val resolvedLibraryAlbums = remember(libraryAlbums) {
        libraryAlbums
    }

    // Get miniplayer padding for bottom content alignment
    val miniPlayerBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val isTabletLayout = LocalConfiguration.current.screenWidthDp >= 600
    val baseLibraryBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val fabBottomPaddingVal = if (isTabletLayout) {
        (baseLibraryBottomPadding + 12.dp).coerceAtLeast(12.dp)
    } else {
        (baseLibraryBottomPadding - 4.dp).coerceAtLeast(0.dp)
    }
    val libraryBottomOverlayPadding = baseLibraryBottomPadding
    val contentBottomPadding = 24.dp

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = StreamingLibraryTab.entries
    val selectedTab = tabs[selectedTabIndex.coerceIn(0, tabs.lastIndex)]
    val tabRowState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { tabs.size }
    )

    var songSortOrder by rememberSaveable { mutableStateOf(StreamingSongSortOrder.TITLE_ASC) }
    var albumSortOrder by rememberSaveable { mutableStateOf(StreamingAlbumSortOrder.TITLE_ASC) }
    var artistSortOrder by rememberSaveable { mutableStateOf(StreamingArtistSortOrder.NAME_ASC) }
    var playlistSortOrder by rememberSaveable { mutableStateOf(StreamingPlaylistSortOrder.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showPlaylistFabMenu by remember { mutableStateOf(false) }
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }

    val songsListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()
    val playlistsGridState = rememberLazyGridState()
    val albumsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    val artistsGridState = rememberLazyGridState()

    val playlistViewType by appSettings.playlistViewType.collectAsState()
    val albumViewType by appSettings.albumViewType.collectAsState()
    val artistViewType by appSettings.artistViewType.collectAsState()

    val isListAtTop by remember(
        selectedTabIndex, playlistViewType, albumViewType, artistViewType
    ) {
        derivedStateOf {
            when (tabs.getOrNull(selectedTabIndex)) {
                StreamingLibraryTab.SONGS -> songsListState.firstVisibleItemIndex == 0 && songsListState.firstVisibleItemScrollOffset == 0
                StreamingLibraryTab.PLAYLISTS -> {
                    if (playlistViewType == PlaylistViewType.GRID) {
                        playlistsGridState.firstVisibleItemIndex == 0 && playlistsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        playlistsListState.firstVisibleItemIndex == 0 && playlistsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                StreamingLibraryTab.ALBUMS -> {
                    if (albumViewType == AlbumViewType.GRID) {
                        albumsGridState.firstVisibleItemIndex == 0 && albumsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        albumsListState.firstVisibleItemIndex == 0 && albumsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                StreamingLibraryTab.ARTISTS -> {
                    if (artistViewType == ArtistViewType.GRID) {
                        artistsGridState.firstVisibleItemIndex == 0 && artistsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        artistsListState.firstVisibleItemIndex == 0 && artistsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                else -> true
            }
        }
    }

    val multiSelectionState = remember { chromahub.rhythm.app.features.local.presentation.viewmodel.MultiSelectionStateHolder() }
    val selectedSongs by multiSelectionState.selectedSongs.collectAsState()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsState()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsState()
    
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var songsToAddToPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }

    val sortedSongs = remember(librarySongs, songSortOrder) {
        when (songSortOrder) {
            StreamingSongSortOrder.TITLE_ASC -> librarySongs.sortedBy { it.title.lowercase() }
            StreamingSongSortOrder.TITLE_DESC -> librarySongs.sortedByDescending { it.title.lowercase() }
            StreamingSongSortOrder.ARTIST_ASC -> librarySongs.sortedBy { it.artist.lowercase() }
            StreamingSongSortOrder.ARTIST_DESC -> librarySongs.sortedByDescending { it.artist.lowercase() }
            StreamingSongSortOrder.ALBUM_ASC -> librarySongs.sortedBy { it.album.lowercase() }
            StreamingSongSortOrder.ALBUM_DESC -> librarySongs.sortedByDescending { it.album.lowercase() }
            StreamingSongSortOrder.DURATION_ASC -> librarySongs.sortedBy { it.duration }
            StreamingSongSortOrder.DURATION_DESC -> librarySongs.sortedByDescending { it.duration }
        }
    }
    val sortedAlbums = remember(resolvedLibraryAlbums, albumSortOrder) {
        when (albumSortOrder) {
            StreamingAlbumSortOrder.TITLE_ASC -> resolvedLibraryAlbums.sortedBy { it.title.lowercase() }
            StreamingAlbumSortOrder.TITLE_DESC -> resolvedLibraryAlbums.sortedByDescending { it.title.lowercase() }
            StreamingAlbumSortOrder.ARTIST_ASC -> resolvedLibraryAlbums.sortedBy { it.artist.lowercase() }
            StreamingAlbumSortOrder.ARTIST_DESC -> resolvedLibraryAlbums.sortedByDescending { it.artist.lowercase() }
            StreamingAlbumSortOrder.YEAR_ASC -> resolvedLibraryAlbums.sortedBy { it.year ?: 0 }
            StreamingAlbumSortOrder.YEAR_DESC -> resolvedLibraryAlbums.sortedByDescending { it.year ?: 0 }
            StreamingAlbumSortOrder.TRACK_COUNT_ASC -> resolvedLibraryAlbums.sortedBy { it.songCount }
            StreamingAlbumSortOrder.TRACK_COUNT_DESC -> resolvedLibraryAlbums.sortedByDescending { it.songCount }
        }
    }
    val sortedArtists = remember(libraryArtists, artistSortOrder) {
        when (artistSortOrder) {
            StreamingArtistSortOrder.NAME_ASC -> libraryArtists.sortedBy { it.name.lowercase() }
            StreamingArtistSortOrder.NAME_DESC -> libraryArtists.sortedByDescending { it.name.lowercase() }
            StreamingArtistSortOrder.SONG_COUNT_ASC -> libraryArtists.sortedBy { it.songCount }
            StreamingArtistSortOrder.SONG_COUNT_DESC -> libraryArtists.sortedByDescending { it.songCount }
            StreamingArtistSortOrder.ALBUM_COUNT_ASC -> libraryArtists.sortedBy { it.albumCount }
            StreamingArtistSortOrder.ALBUM_COUNT_DESC -> libraryArtists.sortedByDescending { it.albumCount }
            StreamingArtistSortOrder.POPULARITY_DESC -> libraryArtists.sortedByDescending { it.popularity ?: Int.MIN_VALUE }
            StreamingArtistSortOrder.POPULARITY_ASC -> libraryArtists.sortedBy { it.popularity ?: Int.MIN_VALUE }
        }
    }
    val sortedPlaylists = remember(libraryPlaylists, playlistSortOrder) {
        when (playlistSortOrder) {
            StreamingPlaylistSortOrder.NAME_ASC -> libraryPlaylists.sortedBy { it.name.lowercase() }
            StreamingPlaylistSortOrder.NAME_DESC -> libraryPlaylists.sortedByDescending { it.name.lowercase() }
            StreamingPlaylistSortOrder.TRACK_COUNT_ASC -> libraryPlaylists.sortedBy { it.songCount }
            StreamingPlaylistSortOrder.TRACK_COUNT_DESC -> libraryPlaylists.sortedByDescending { it.songCount }
        }
    }

    val sortedSongsById = remember(sortedSongs) { sortedSongs.associateBy { it.id } }
    val localSongs = remember(sortedSongs) {
        sortedSongs.map { it.toLibrarySong() }
    }
    val localSongsById = remember(localSongs) { localSongs.associateBy { it.id } }
    val localAlbums = remember(sortedAlbums) {
        sortedAlbums.map { it.toLibraryAlbum(localSongs) }
    }
    val localArtists = remember(
        sortedArtists,
        localSongs,
        localAlbums,
        artistSeparatorEnabled,
        artistSeparatorDelimiters
    ) {
        sortedArtists.map {
            it.toLibraryArtist(
                librarySongs = localSongs,
                libraryAlbums = localAlbums,
                separatorEnabled = artistSeparatorEnabled,
                separatorDelimiters = artistSeparatorDelimiters
            )
        }
    }
    val localPlaylists = remember(sortedPlaylists) {
        sortedPlaylists.map { it.toLibraryPlaylist() }
    }
    val localPlaylistsById = remember(localPlaylists, sortedPlaylists) {
        sortedPlaylists.associateBy { it.id }
    }
    val currentLocalSong = remember(activeSongId, localSongsById, currentStreamingSong) {
        activeSongId?.let(localSongsById::get) ?: currentStreamingSong?.toLibrarySong()
    }
    val streamingFavoriteSongIds = remember(sortedSongs, likedSongs) {
        (sortedSongs.filter { it.isFavorite }.map { it.id } + likedSongs.map { it.id }).toSet()
    }
    val enableRatingSystem by appSettings.enableRatingSystem.collectAsState()
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    
    val categories = remember(localSongs, streamingFavoriteSongIds, enableRatingSystem) {
        calculateSongCategories(
            preparedSongs = localSongs,
            favoriteSongs = streamingFavoriteSongIds,
            enableRatingSystem = enableRatingSystem,
            ratingDistribution = emptyMap()
        )
    }

    val filteredSongs = remember(localSongs, selectedCategory, streamingFavoriteSongIds) {
        filterSongsByCategory(
            preparedSongs = localSongs,
            selectedCategory = selectedCategory,
            favoriteSongs = streamingFavoriteSongIds,
            ratedSongIdsProvider = { emptySet() }
        )
    }
    val adjustedSongsBottomPadding = baseLibraryBottomPadding

    val openAlbumBottomSheet: (StreamingAlbum) -> Unit = { album ->
        onNavigateToAlbum(album)
    }
    val openAlbumForSong: (Song) -> Unit = { localSong ->
        val resolvedStreamingSong = sortedSongsById[localSong.id]
        val albumArtist = localSong.albumArtist?.takeIf { it.isNotBlank() } ?: localSong.artist
        val resolvedAlbum = sortedAlbums.firstOrNull { album ->
            val albumMatchesByTrack = album.tracks.any { it.id == localSong.id }
            val albumMatchesById = resolvedStreamingSong?.albumId?.let { album.id == it } == true
            val albumMatchesByMetadata = album.title.equals(localSong.album, ignoreCase = true) &&
                album.artist.equals(albumArtist, ignoreCase = true)
            albumMatchesByTrack || albumMatchesById || albumMatchesByMetadata
        } ?: sortedAlbums.firstOrNull { album ->
            album.title.equals(localSong.album, ignoreCase = true)
        }

        resolvedAlbum?.let(openAlbumBottomSheet)
    }
    val hasLibraryContent = remember(
        localSongs,
        localArtists,
        localPlaylists,
        recommendations,
        featuredPlaylists
    ) {
        localSongs.isNotEmpty() ||
            localAlbums.isNotEmpty() ||
            localArtists.isNotEmpty() ||
            localPlaylists.isNotEmpty() ||
            recommendations.isNotEmpty() ||
            featuredPlaylists.isNotEmpty()
    }
    val libraryErrorMessage = error?.takeIf { it.isNotBlank() }

    val mapLocalSongsToStreaming: (List<Song>) -> List<StreamingSong> = remember(sortedSongsById) {
        { localQueue ->
            localQueue
                .mapNotNull { sortedSongsById[it.id] }
                .distinctBy { it.id }
        }
    }

    LaunchedEffect(selectedService, resolvedServiceId) {
        if (resolvedServiceId.isNotBlank() && resolvedServiceId != selectedService) {
            appSettings.setStreamingService(resolvedServiceId)
        }
    }

    LaunchedEffect(resolvedServiceId, isSelectedServiceConnected) {
        if (isSelectedServiceConnected) {
            if (!hasLoadedLibrary) {
                viewModel.loadLibrary()
            }
            if (!hasLoadedHomeContent) {
                viewModel.loadHomeContent()
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && selectedTabIndex != pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }
    }

    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
        tabRowState.animateScrollToItem(selectedTabIndex.coerceAtLeast(0))
    }

    val currentSortLabelRes = when (selectedTab) {
        StreamingLibraryTab.SONGS -> songSortOrder.labelRes
        StreamingLibraryTab.ALBUMS -> albumSortOrder.labelRes
        StreamingLibraryTab.ARTISTS -> artistSortOrder.labelRes
        StreamingLibraryTab.PLAYLISTS -> playlistSortOrder.labelRes
    }
    val isCurrentSortAscending = when (selectedTab) {
        StreamingLibraryTab.SONGS -> songSortOrder.ascending
        StreamingLibraryTab.ALBUMS -> albumSortOrder.ascending
        StreamingLibraryTab.ARTISTS -> artistSortOrder.ascending
        StreamingLibraryTab.PLAYLISTS -> playlistSortOrder.ascending
    }
    val currentSortIcon = when (selectedTab) {
        StreamingLibraryTab.SONGS -> songSortOrder.icon
        StreamingLibraryTab.ALBUMS -> albumSortOrder.icon
        StreamingLibraryTab.ARTISTS -> artistSortOrder.icon
        StreamingLibraryTab.PLAYLISTS -> playlistSortOrder.icon
    }
    val random = remember { Random(System.currentTimeMillis()) }
    
    BackHandler(enabled = isSelectionMode) {
        multiSelectionState.clearSelection()
    }
    val libraryTitle = remember(selectedServiceName, isSelectedServiceConnected) {
        if (isSelectedServiceConnected && selectedServiceName.isNotBlank()) {
            "$selectedServiceName ${context.getString(R.string.library_title)}"
        } else {
            context.getString(R.string.library_title)
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = isLoading

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Spacer(modifier = Modifier.height(5.dp))
                
                LargeTopAppBar(
                    navigationIcon = { },
                    title = {
                        val collapsedFraction = scrollBehavior.state.collapsedFraction
                        val fontSize = (24 + (32 - 24) * (1 - collapsedFraction)).sp

                        Text(
                            text = libraryTitle,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize
                            ),
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    },
                    actions = {
                        if (isSelectedServiceConnected) {
                            Box {
                                val sortButtonScale by animateFloatAsState(
                                    targetValue = if (showSortMenu) 0.95f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "sortButtonScale"
                                )
                                
                                FilledTonalButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        showSortMenu = true
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .graphicsLayer {
                                        scaleX = sortButtonScale
                                        scaleY = sortButtonScale
                                    }
                                ) {
                                    Icon(
                                        imageVector = currentSortIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = stringResource(id = currentSortLabelRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    Icon(
                                        imageVector = if (isCurrentSortAscending) RhythmIcons.ArrowUpward else RhythmIcons.ArrowDownward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
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
                                    when (selectedTab) {
                                        StreamingLibraryTab.SONGS -> {
                                            val currentKey = when (songSortOrder) {
                                                StreamingSongSortOrder.TITLE_ASC, StreamingSongSortOrder.TITLE_DESC -> "TITLE"
                                                StreamingSongSortOrder.ARTIST_ASC, StreamingSongSortOrder.ARTIST_DESC -> "ARTIST"
                                                StreamingSongSortOrder.ALBUM_ASC, StreamingSongSortOrder.ALBUM_DESC -> "ALBUM"
                                                StreamingSongSortOrder.DURATION_ASC, StreamingSongSortOrder.DURATION_DESC -> "DURATION"
                                            }
                                            val isAscending = songSortOrder.ascending
                                            val songOptions = listOf(
                                                RhythmSortOption("TITLE", context.getString(R.string.sort_title), RhythmIcons.SortByAlpha),
                                                RhythmSortOption("ARTIST", context.getString(R.string.sort_artist), RhythmIcons.ArtistFilled),
                                                RhythmSortOption("ALBUM", context.getString(R.string.library_sort_album), RhythmIcons.AlbumFilled),
                                                RhythmSortOption("DURATION", context.getString(R.string.sort_duration_short_first), MaterialSymbolIcon("timer", filled = true))
                                            )
                                            fun getOrder(key: String, asc: Boolean): StreamingSongSortOrder {
                                                return when (key) {
                                                    "TITLE" -> if (asc) StreamingSongSortOrder.TITLE_ASC else StreamingSongSortOrder.TITLE_DESC
                                                    "ARTIST" -> if (asc) StreamingSongSortOrder.ARTIST_ASC else StreamingSongSortOrder.ARTIST_DESC
                                                    "ALBUM" -> if (asc) StreamingSongSortOrder.ALBUM_ASC else StreamingSongSortOrder.ALBUM_DESC
                                                    "DURATION" -> if (asc) StreamingSongSortOrder.DURATION_ASC else StreamingSongSortOrder.DURATION_DESC
                                                    else -> StreamingSongSortOrder.TITLE_ASC
                                                }
                                            }
                                            RhythmSortMenuContent(
                                                selectedKey = currentKey,
                                                isAscending = isAscending,
                                                options = songOptions,
                                                onKeySelected = { key ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    songSortOrder = getOrder(key, isAscending)
                                                    showSortMenu = false
                                                },
                                                onDirectionToggled = { asc ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    songSortOrder = getOrder(currentKey, asc)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                        StreamingLibraryTab.ALBUMS -> {
                                            val currentKey = when (albumSortOrder) {
                                                StreamingAlbumSortOrder.TITLE_ASC, StreamingAlbumSortOrder.TITLE_DESC -> "TITLE"
                                                StreamingAlbumSortOrder.ARTIST_ASC, StreamingAlbumSortOrder.ARTIST_DESC -> "ARTIST"
                                                StreamingAlbumSortOrder.YEAR_ASC, StreamingAlbumSortOrder.YEAR_DESC -> "YEAR"
                                                StreamingAlbumSortOrder.TRACK_COUNT_ASC, StreamingAlbumSortOrder.TRACK_COUNT_DESC -> "TRACK_COUNT"
                                            }
                                            val isAscending = albumSortOrder.ascending
                                            val albumOptions = listOf(
                                                RhythmSortOption("TITLE", context.getString(R.string.sort_title), RhythmIcons.SortByAlpha),
                                                RhythmSortOption("ARTIST", context.getString(R.string.sort_artist), RhythmIcons.ArtistFilled),
                                                RhythmSortOption("YEAR", context.getString(R.string.sort_date_created), RhythmIcons.DateRange),
                                                RhythmSortOption("TRACK_COUNT", context.getString(R.string.sort_song_count), RhythmIcons.MusicNote)
                                            )
                                            fun getOrder(key: String, asc: Boolean): StreamingAlbumSortOrder {
                                                return when (key) {
                                                    "TITLE" -> if (asc) StreamingAlbumSortOrder.TITLE_ASC else StreamingAlbumSortOrder.TITLE_DESC
                                                    "ARTIST" -> if (asc) StreamingAlbumSortOrder.ARTIST_ASC else StreamingAlbumSortOrder.ARTIST_DESC
                                                    "YEAR" -> if (asc) StreamingAlbumSortOrder.YEAR_ASC else StreamingAlbumSortOrder.YEAR_DESC
                                                    "TRACK_COUNT" -> if (asc) StreamingAlbumSortOrder.TRACK_COUNT_ASC else StreamingAlbumSortOrder.TRACK_COUNT_DESC
                                                    else -> StreamingAlbumSortOrder.TITLE_ASC
                                                }
                                            }
                                            RhythmSortMenuContent(
                                                selectedKey = currentKey,
                                                isAscending = isAscending,
                                                options = albumOptions,
                                                onKeySelected = { key ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    albumSortOrder = getOrder(key, isAscending)
                                                    showSortMenu = false
                                                },
                                                onDirectionToggled = { asc ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    albumSortOrder = getOrder(currentKey, asc)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                        StreamingLibraryTab.ARTISTS -> {
                                            val currentKey = when (artistSortOrder) {
                                                StreamingArtistSortOrder.NAME_ASC, StreamingArtistSortOrder.NAME_DESC -> "NAME"
                                                StreamingArtistSortOrder.SONG_COUNT_ASC, StreamingArtistSortOrder.SONG_COUNT_DESC -> "SONG_COUNT"
                                                StreamingArtistSortOrder.ALBUM_COUNT_ASC, StreamingArtistSortOrder.ALBUM_COUNT_DESC -> "ALBUM_COUNT"
                                                StreamingArtistSortOrder.POPULARITY_ASC, StreamingArtistSortOrder.POPULARITY_DESC -> "POPULARITY"
                                            }
                                            val isAscending = artistSortOrder.ascending
                                            val artistOptions = listOf(
                                                RhythmSortOption("NAME", context.getString(R.string.sort_name), RhythmIcons.SortByAlpha),
                                                RhythmSortOption("SONG_COUNT", context.getString(R.string.sort_song_count), RhythmIcons.MusicNote),
                                                RhythmSortOption("ALBUM_COUNT", context.getString(R.string.library_sort_album), RhythmIcons.AlbumFilled),
                                                RhythmSortOption("POPULARITY", context.getString(R.string.bottomsheet_sort_by), RhythmIcons.TrendingUp)
                                            )
                                            fun getOrder(key: String, asc: Boolean): StreamingArtistSortOrder {
                                                return when (key) {
                                                    "NAME" -> if (asc) StreamingArtistSortOrder.NAME_ASC else StreamingArtistSortOrder.NAME_DESC
                                                    "SONG_COUNT" -> if (asc) StreamingArtistSortOrder.SONG_COUNT_ASC else StreamingArtistSortOrder.SONG_COUNT_DESC
                                                    "ALBUM_COUNT" -> if (asc) StreamingArtistSortOrder.ALBUM_COUNT_ASC else StreamingArtistSortOrder.ALBUM_COUNT_DESC
                                                    "POPULARITY" -> if (asc) StreamingArtistSortOrder.POPULARITY_ASC else StreamingArtistSortOrder.POPULARITY_DESC
                                                    else -> StreamingArtistSortOrder.NAME_ASC
                                                }
                                            }
                                            RhythmSortMenuContent(
                                                selectedKey = currentKey,
                                                isAscending = isAscending,
                                                options = artistOptions,
                                                onKeySelected = { key ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    artistSortOrder = getOrder(key, isAscending)
                                                    showSortMenu = false
                                                },
                                                onDirectionToggled = { asc ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    artistSortOrder = getOrder(currentKey, asc)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                        StreamingLibraryTab.PLAYLISTS -> {
                                            val currentKey = when (playlistSortOrder) {
                                                StreamingPlaylistSortOrder.NAME_ASC, StreamingPlaylistSortOrder.NAME_DESC -> "NAME"
                                                StreamingPlaylistSortOrder.TRACK_COUNT_ASC, StreamingPlaylistSortOrder.TRACK_COUNT_DESC -> "TRACK_COUNT"
                                            }
                                            val isAscending = playlistSortOrder.ascending
                                            val playlistOptions = listOf(
                                                RhythmSortOption("NAME", context.getString(R.string.sort_name), RhythmIcons.SortByAlpha),
                                                RhythmSortOption("TRACK_COUNT", context.getString(R.string.sort_song_count), RhythmIcons.MusicNote)
                                            )
                                            fun getOrder(key: String, asc: Boolean): StreamingPlaylistSortOrder {
                                                return when (key) {
                                                    "NAME" -> if (asc) StreamingPlaylistSortOrder.NAME_ASC else StreamingPlaylistSortOrder.NAME_DESC
                                                    "TRACK_COUNT" -> if (asc) StreamingPlaylistSortOrder.TRACK_COUNT_ASC else StreamingPlaylistSortOrder.TRACK_COUNT_DESC
                                                    else -> StreamingPlaylistSortOrder.NAME_ASC
                                                }
                                            }
                                            RhythmSortMenuContent(
                                                selectedKey = currentKey,
                                                isAscending = isAscending,
                                                options = playlistOptions,
                                                onKeySelected = { key ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    playlistSortOrder = getOrder(key, isAscending)
                                                    showSortMenu = false
                                                },
                                                onDirectionToggled = { asc ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    playlistSortOrder = getOrder(currentKey, asc)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        bottomBar = {},
        floatingActionButton = {
            if (tabs.getOrNull(selectedTabIndex) == StreamingLibraryTab.PLAYLISTS) {
                val playlistsScrollingUp = if (playlistViewType == PlaylistViewType.GRID) playlistsGridState.isScrollingUp() else playlistsListState.isScrollingUp()
                val playlistsScrollInProgress = if (playlistViewType == PlaylistViewType.GRID) playlistsGridState.isScrollInProgress else playlistsListState.isScrollInProgress
                val showPlaylistFab = !playlistsScrollInProgress || playlistsScrollingUp
                PlaylistFabMenu(
                    visible = showPlaylistFab,
                    expanded = showPlaylistFabMenu,
                    onExpandedChange = { showPlaylistFabMenu = it },
                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                    onImportPlaylist = null,
                    onExportPlaylists = null,
                    bottomPadding = fabBottomPaddingVal,
                    haptics = haptics
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                LazyRow(
                    state = tabRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    itemsIndexed(tabs) { index, tab ->
                        val isSelected = selectedTabIndex == index
                        TabAnimation(
                            index = index,
                            selectedIndex = selectedTabIndex,
                            title = stringResource(id = tab.titleRes),
                            selectedColor = MaterialTheme.colorScheme.primary,
                            onSelectedColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                            onUnselectedColor = MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                selectedTabIndex = index
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier.padding(all = 2.dp),
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(id = tab.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 10.dp, top = 0.dp, end = 10.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadLibrary() },
                    state = pullToRefreshState,
                    enabled = !isSelectionMode && isListAtTop && scrollBehavior.state.collapsedFraction == 0f,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                when {
                    !isSelectedServiceConnected -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            StreamingLibraryDisconnectedCard(
                                selectedServiceName = selectedServiceName,
                                onConfigureService = { onConfigureService(configureTargetServiceId) },
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }

                    (isLoading || !hasLoadedLibrary) && !hasLibraryContent -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            StreamingLibraryLoadingCard(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    !libraryErrorMessage.isNullOrBlank() && !hasLibraryContent -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            StreamingLibraryStateCard(
                                title = stringResource(id = R.string.streaming_home_selected_service_unavailable),
                                subtitle = libraryErrorMessage,
                                icon = RhythmIcons.Info,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                actionText = stringResource(id = R.string.streaming_manage_service),
                                onAction = { onConfigureService(configureTargetServiceId) },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    !hasLibraryContent -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            StreamingLibraryStateCard(
                                title = stringResource(id = R.string.streaming_library_empty),
                                subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint),
                                icon = RhythmIcons.AlbumFilled,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                actionText = stringResource(id = R.string.streaming_manage_service),
                                onAction = { onConfigureService(configureTargetServiceId) },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    else -> {
                        val activeTab = tabs.getOrNull(pagerState.currentPage)
                        val hasContent = when (activeTab) {
                            StreamingLibraryTab.SONGS -> localSongs.isNotEmpty()
                            StreamingLibraryTab.ALBUMS -> localAlbums.isNotEmpty()
                            StreamingLibraryTab.ARTISTS -> localArtists.isNotEmpty()
                            else -> false
                        }
                        val songsScrollingUp = songsListState.isScrollingUp()
                        val albumsListScrollingUp = albumsListState.isScrollingUp()
                        val albumsGridScrollingUp = albumsGridState.isScrollingUp()
                        val artistsListScrollingUp = artistsListState.isScrollingUp()
                        val artistsGridScrollingUp = artistsGridState.isScrollingUp()
                        val songsScrollInProgress = songsListState.isScrollInProgress
                        val albumsListScrollInProgress = albumsListState.isScrollInProgress
                        val albumsGridScrollInProgress = albumsGridState.isScrollInProgress
                        val artistsListScrollInProgress = artistsListState.isScrollInProgress
                        val artistsGridScrollInProgress = artistsGridState.isScrollInProgress
                        val shouldShowBottomBar = if (isSelectionMode) {
                            hasContent
                        } else {
                            hasContent && when (activeTab) {
                                StreamingLibraryTab.SONGS -> !songsScrollInProgress || songsScrollingUp
                                StreamingLibraryTab.ALBUMS -> {
                                    val isScrollingUp = if (albumViewType == AlbumViewType.GRID) albumsGridScrollingUp else albumsListScrollingUp
                                    val isScrolling = if (albumViewType == AlbumViewType.GRID) albumsGridScrollInProgress else albumsListScrollInProgress
                                    !isScrolling || isScrollingUp
                                }
                                StreamingLibraryTab.ARTISTS -> {
                                    val isScrollingUp = if (artistViewType == ArtistViewType.GRID) artistsGridScrollingUp else artistsListScrollingUp
                                    val isScrolling = if (artistViewType == ArtistViewType.GRID) artistsGridScrollInProgress else artistsListScrollInProgress
                                    !isScrolling || isScrollingUp
                                }
                                else -> true
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                visible = selectedTab == StreamingLibraryTab.SONGS,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp)
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = categories,
                                            key = { it }
                                        ) { category ->
                                            val isSelected = selectedCategory == category
                                            val scaleAnimatable = remember { Animatable(1f) }
                                            val offsetAnimatable = remember { Animatable(0f) }
                                            
                                            LaunchedEffect(isSelected) {
                                                if (isSelected) {
                                                    launch {
                                                        scaleAnimatable.animateTo(1.05f, animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing))
                                                        scaleAnimatable.animateTo(1f, animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing))
                                                    }
                                                } else {
                                                    scaleAnimatable.snapTo(1f)
                                                }
                                            }
                                            
                                            LaunchedEffect(selectedCategory) {
                                                if (!isSelected) {
                                                    val currentIndex = categories.indexOf(category)
                                                    val selectedIndex = categories.indexOf(selectedCategory)
                                                    if (currentIndex >= 0 && selectedIndex >= 0) {
                                                        val distance = currentIndex - selectedIndex
                                                        if (abs(distance) == 1) {
                                                            val direction = if (distance > 0) 1 else -1
                                                            val offsetValue = 8f * direction
                                                            launch {
                                                                offsetAnimatable.animateTo(offsetValue, animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing))
                                                                offsetAnimatable.animateTo(0f, animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing))
                                                            }
                                                        } else {
                                                            offsetAnimatable.snapTo(0f)
                                                        }
                                                    }
                                                } else {
                                                    offsetAnimatable.snapTo(0f)
                                                }
                                            }

                                            val containerColor by animateColorAsState(
                                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                                label = "chipContainerColor"
                                            )
                                            val labelColor by animateColorAsState(
                                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                                label = "chipLabelColor"
                                            )
                                            val borderColor by animateColorAsState(
                                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                                label = "chipBorderColor"
                                            )
                                            val borderWidth by animateDpAsState(
                                                targetValue = if (isSelected) 2.dp else 1.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                                label = "chipBorderWidth"
                                            )
                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isSelected) 24.dp else 12.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                                label = "chipCornerRadius"
                                            )

                                            FilterChip(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    selectedCategory = category
                                                },
                                                label = {
                                                    Text(
                                                        text = when (category) {
                                                            "All" -> context.getString(R.string.library_category_all)
                                                            "❤️ Favorites" -> context.getString(R.string.library_category_favorites)
                                                            "Short (< 3 min)" -> context.getString(R.string.library_category_short)
                                                            "Medium (3-5 min)" -> context.getString(R.string.library_category_medium)
                                                            "Long (> 5 min)" -> context.getString(R.string.library_category_long)
                                                            "Hi-Res Lossless" -> "Hi-Res Lossless"
                                                            "Lossless" -> "Lossless"
                                                            "Dolby" -> "Dolby"
                                                            "Mono" -> context.getString(R.string.library_category_mono)
                                                            "Stereo" -> context.getString(R.string.library_category_stereo)
                                                            "High Quality" -> "High Quality"
                                                            "Standard" -> "Standard"
                                                            else -> category
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                },
                                                selected = isSelected,
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = containerColor,
                                                    selectedLabelColor = labelColor,
                                                    selectedLeadingIconColor = labelColor,
                                                    containerColor = containerColor,
                                                    labelColor = labelColor,
                                                    iconColor = labelColor
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isSelected,
                                                    borderColor = borderColor,
                                                    selectedBorderColor = borderColor,
                                                    borderWidth = borderWidth
                                                ),
                                                shape = RoundedCornerShape(cornerRadius),
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = scaleAnimatable.value
                                                    scaleY = scaleAnimatable.value
                                                    translationX = offsetAnimatable.value
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) { page ->
                        when (tabs[page]) {
                            StreamingLibraryTab.ALBUMS -> {
                                if (localAlbums.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.streaminglibraryscreen_no_albums),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    SingleCardAlbumsContent(
                                        albums = localAlbums,
                                        listState = albumsListState,
                                        gridState = albumsGridState,
                                        onAlbumClick = { album ->
                                            val streamingAlbum = sortedAlbums.firstOrNull { it.id == album.id }
                                            streamingAlbum?.let {
                                                if (it.tracks.isNotEmpty()) {
                                                    viewModel.playQueue(it.tracks, startIndex = 0, shuffle = false)
                                                } else {
                                                    scope.launch {
                                                        val tracks = viewModel.getAlbumSongs(it)
                                                        if (tracks.isNotEmpty()) {
                                                            viewModel.playQueue(tracks, startIndex = 0, shuffle = false)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onAlbumBottomSheetClick = { album ->
                                            val streamingAlbum = sortedAlbums.firstOrNull { it.id == album.id }
                                            streamingAlbum?.let {
                                                onNavigateToAlbum(it)
                                            }
                                        },
                                        onSongClick = { song ->
                                            val index = localSongs.indexOfFirst { it.id == song.id }
                                            if (index >= 0) {
                                                viewModel.playQueue(
                                                    queue = sortedSongs,
                                                    startIndex = index,
                                                    shuffle = false
                                                )
                                            }
                                        },
                                        haptics = haptics,
                                        appSettings = appSettings,
                                        onPlayQueue = { localQueue ->
                                            val queue = mapLocalSongsToStreaming(localQueue)
                                            if (queue.isNotEmpty()) {
                                                viewModel.playQueue(queue = queue, startIndex = 0, shuffle = false)
                                            }
                                        },
                                        onShuffleQueue = { localQueue ->
                                            val queue = mapLocalSongsToStreaming(localQueue)
                                            if (queue.isNotEmpty()) {
                                                viewModel.playQueue(
                                                    queue = queue,
                                                    startIndex = if (queue.size > 1) random.nextInt(queue.size) else 0,
                                                    shuffle = true
                                                )
                                            }
                                        },
                                        onRefreshClick = { viewModel.loadLibrary() },
                                        bottomPadding = adjustedSongsBottomPadding
                                    )
                                }
                            }

                            StreamingLibraryTab.SONGS -> {
                                SingleCardSongsContent(
                                    songs = filteredSongs,
                                    listState = songsListState,
                                    albums = localAlbums,
                                    artists = localArtists,
                                    onSongClick = { localSong ->
                                        val index = sortedSongs.indexOfFirst { it.id == localSong.id }
                                        if (index >= 0) {
                                            viewModel.playQueue(
                                                queue = sortedSongs,
                                                startIndex = index,
                                                shuffle = false
                                            )
                                        }
                                    },
                                    onAddToPlaylist = { song ->
                                        songsToAddToPlaylist = listOf(song)
                                        showAddToPlaylistSheet = true
                                    },
                                    onAddToQueue = { song ->
                                        sortedSongsById[song.id]?.let { ss ->
                                            if (localMusicViewModel != null) {
                                                viewModel.addSongToQueue(ss, localMusicViewModel)
                                            }
                                        }
                                    },
                                    onPlayNext = { song ->
                                        sortedSongsById[song.id]?.let { ss ->
                                            if (localMusicViewModel != null) {
                                                viewModel.playNext(ss, localMusicViewModel)
                                            }
                                        }
                                    },
                                    onShowSongInfo = { song ->
                                        selectedSongForInfo = song
                                        showSongInfoSheet = true
                                    },
                                    onAddToBlacklist = {},
                                    favoriteSongs = streamingFavoriteSongIds,
                                    onPlayQueue = { localQueue ->
                                        val queue = mapLocalSongsToStreaming(localQueue)
                                        if (queue.isNotEmpty()) {
                                            viewModel.playQueue(
                                                queue = queue,
                                                startIndex = 0,
                                                shuffle = false
                                            )
                                        }
                                    },
                                    onPlayQueueFromIndex = { localQueue, index ->
                                        val queue = mapLocalSongsToStreaming(localQueue)
                                        if (queue.isNotEmpty()) {
                                            viewModel.playQueue(
                                                queue = queue,
                                                startIndex = index.coerceIn(0, queue.lastIndex),
                                                shuffle = false
                                            )
                                        }
                                    },
                                    onShuffleQueue = { localQueue ->
                                        val queue = mapLocalSongsToStreaming(localQueue)
                                        if (queue.isNotEmpty()) {
                                            viewModel.playQueue(
                                                queue = queue,
                                                startIndex = if (queue.size > 1) random.nextInt(queue.size) else 0,
                                                shuffle = true
                                            )
                                        }
                                    },
                                    onGoToArtist = { localArtist ->
                                        val resolvedArtist = sortedArtists.firstOrNull { it.id == localArtist.id }
                                            ?: sortedArtists.firstOrNull {
                                                it.name.equals(localArtist.name, ignoreCase = true)
                                            }
                                        resolvedArtist?.let(onNavigateToArtist)
                                    },
                                    onGoToAlbum = { album ->
                                        val streamingAlbum = sortedAlbums.firstOrNull { it.id == album.id }
                                        streamingAlbum?.let(openAlbumBottomSheet)
                                    },
                                    currentSong = currentLocalSong,
                                    isPlaying = isPlayerPlaying,
                                    haptics = haptics,
                                    enableRatingSystem = false,
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    multiSelectionState = multiSelectionState,
                                    onSongLongPress = { song -> multiSelectionState.toggleSelection(song) },
                                    onSongSelectionToggle = { song -> multiSelectionState.toggleSelection(song) },
                                    onShowMultiSelectionSheet = { showMultiSelectionSheet = true },
                                    songMenuContent = @Composable { localSong, dismissMenu ->
                                        val songIndex = sortedSongs.indexOfFirst { it.id == localSong.id }
                                        val resolvedArtist = sortedArtists.firstOrNull {
                                            it.name.equals(localSong.artist, ignoreCase = true)
                                        }
                                        val resolvedAlbum = sortedAlbums.firstOrNull { album ->
                                            val albumMatchesByTrack = album.tracks.any { it.id == localSong.id }
                                            val albumMatchesById = sortedSongsById[localSong.id]?.albumId?.let { album.id == it } == true
                                            val albumArtist = localSong.albumArtist?.takeIf { it.isNotBlank() } ?: localSong.artist
                                            val albumMatchesByMetadata = album.title.equals(localSong.album, ignoreCase = true) &&
                                                album.artist.equals(albumArtist, ignoreCase = true)
                                            albumMatchesByTrack || albumMatchesById || albumMatchesByMetadata
                                        } ?: sortedAlbums.firstOrNull { album ->
                                            album.title.equals(localSong.album, ignoreCase = true)
                                        }

                                        val streamingSong = sortedSongsById[localSong.id]
                                        val isLiked = streamingSong != null && likedSongs.any { it.id == streamingSong.id }

                                        RhythmSongMenuContent(
                                            song = localSong,
                                            onPlay = {
                                                dismissMenu()
                                                if (songIndex >= 0) {
                                                    viewModel.playQueue(
                                                        queue = sortedSongs,
                                                        startIndex = songIndex,
                                                        shuffle = false
                                                    )
                                                }
                                            },
                                            onPlayNext = {
                                                dismissMenu()
                                                sortedSongsById[localSong.id]?.let { s ->
                                                    if (localMusicViewModel != null) {
                                                        viewModel.playNext(s, localMusicViewModel)
                                                    }
                                                }
                                            },
                                            onAddToQueue = {
                                                dismissMenu()
                                                sortedSongsById[localSong.id]?.let { s ->
                                                    if (localMusicViewModel != null) {
                                                        viewModel.addSongToQueue(s, localMusicViewModel)
                                                    }
                                                }
                                            },
                                            isLiked = isLiked,
                                            onToggleLike = {
                                                dismissMenu()
                                                streamingSong?.let { s ->
                                                    if (isLiked) viewModel.unlikeSong(s) else viewModel.likeSong(s)
                                                }
                                            },
                                            onAddToPlaylist = {
                                                dismissMenu()
                                                sortedSongsById[localSong.id]?.let { s ->
                                                    onAddSongToPlaylist(s)
                                                }
                                            },
                                            onShowSongInfo = {
                                                dismissMenu()
                                                selectedSongForInfo = localSong
                                                showSongInfoSheet = true
                                            },
                                            onGoToAlbum = resolvedAlbum?.let { album ->
                                                {
                                                    dismissMenu()
                                                    openAlbumBottomSheet(album)
                                                }
                                            },
                                            onGoToArtist = resolvedArtist?.let { artist ->
                                                {
                                                    dismissMenu()
                                                    onNavigateToArtist(artist)
                                                }
                                            }
                                        )
                                    },
                                    onRefreshClick = { viewModel.loadLibrary() },
                                    bottomPadding = adjustedSongsBottomPadding
                                )
                            }

                            StreamingLibraryTab.ARTISTS -> {
                                SingleCardArtistsContent(
                                    artists = localArtists,
                                    listState = artistsListState,
                                    gridState = artistsGridState,
                                    onArtistClick = { localArtist ->
                                        val resolvedArtist = sortedArtists.firstOrNull { it.id == localArtist.id }
                                            ?: sortedArtists.firstOrNull {
                                                it.name.equals(localArtist.name, ignoreCase = true)
                                            }
                                        resolvedArtist?.let(onNavigateToArtist)
                                    },
                                    haptics = haptics,
                                    onPlayQueue = { localQueue ->
                                        val queue = mapLocalSongsToStreaming(localQueue)
                                        if (queue.isNotEmpty()) {
                                            viewModel.playQueue(queue = queue, startIndex = 0, shuffle = false)
                                        }
                                    },
                                    onShuffleQueue = { localQueue ->
                                        val queue = mapLocalSongsToStreaming(localQueue)
                                        if (queue.isNotEmpty()) {
                                            viewModel.playQueue(
                                                queue = queue,
                                                startIndex = if (queue.size > 1) random.nextInt(queue.size) else 0,
                                                shuffle = true
                                            )
                                        }
                                    },
                                    onRefreshClick = { viewModel.loadLibrary() },
                                    bottomPadding = adjustedSongsBottomPadding
                                )
                            }

                            StreamingLibraryTab.PLAYLISTS -> {
                                SingleCardPlaylistsContent(
                                    playlists = localPlaylists,
                                    listState = playlistsListState,
                                    gridState = playlistsGridState,
                                    onPlaylistClick = { localPlaylist ->
                                        localPlaylistsById[localPlaylist.id]?.let(onNavigateToPlaylist)
                                    },
                                    haptics = haptics,
                                    onCreatePlaylist = {
                                        showCreatePlaylistDialog = true
                                    },
                                    appSettings = appSettings,
                                    onRefreshClick = { viewModel.loadLibrary() },
                                    bottomPadding = adjustedSongsBottomPadding
                                )
                            }
                        }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.background,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .zIndex(5f)
                        )

                            val bottomBarSongs = remember(activeTab, filteredSongs, localAlbums, localArtists) {
                                when (activeTab) {
                                    StreamingLibraryTab.SONGS -> filteredSongs
                                    StreamingLibraryTab.ALBUMS -> localAlbums.flatMap { it.songs }
                                    StreamingLibraryTab.ARTISTS -> localArtists.flatMap { it.songs }
                                    else -> emptyList()
                                }
                            }

                            val locateScope = rememberCoroutineScope()
                            val showLocateButton = !shouldShowBottomBar && !isListAtTop

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showLocateButton,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 20.dp, bottom = fabBottomPaddingVal)
                                    .zIndex(10f)
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        locateScope.launch {
                                            when (activeTab) {
                                                StreamingLibraryTab.SONGS -> {
                                                    val idx = localSongs.indexOfFirst { it.id == currentLocalSong?.id }
                                                    songsListState.animateScrollToItem(if (idx >= 0) idx else 0)
                                                }
                                                StreamingLibraryTab.PLAYLISTS -> {
                                                    if (playlistViewType == PlaylistViewType.GRID) playlistsGridState.animateScrollToItem(0)
                                                    else playlistsListState.animateScrollToItem(0)
                                                }
                                                StreamingLibraryTab.ALBUMS -> {
                                                    if (albumViewType == AlbumViewType.GRID) albumsGridState.animateScrollToItem(0)
                                                    else albumsListState.animateScrollToItem(0)
                                                }
                                                StreamingLibraryTab.ARTISTS -> {
                                                    if (artistViewType == ArtistViewType.GRID) artistsGridState.animateScrollToItem(0)
                                                    else artistsListState.animateScrollToItem(0)
                                                }
                                                else -> {}
                                            }
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    val locateIcon = if (activeTab == StreamingLibraryTab.SONGS) {
                                        MaterialSymbolIcon("my_location", filled = true)
                                    } else {
                                        RhythmIcons.ArrowUpward
                                    }
                                    val locateDesc = if (activeTab == StreamingLibraryTab.SONGS) "Locate current song" else "Scroll to top"
                                    Icon(
                                        imageVector = locateIcon,
                                        contentDescription = locateDesc,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            LibraryBottomBar(
                                isVisible = shouldShowBottomBar,
                                activeTab = activeTab?.name ?: "",
                                songs = bottomBarSongs,
                                isSelectionMode = isSelectionMode,
                                selectedSongsCount = selectedSongs.size,
                                explorerPath = null,
                                onSelectToggle = {
                                    if (bottomBarSongs.isNotEmpty()) {
                                        multiSelectionState.toggleSelection(bottomBarSongs.first())
                                    }
                                },
                                onCancelSelection = {
                                    multiSelectionState.clearSelection()
                                },
                                onPlayAll = {
                                    val queue = mapLocalSongsToStreaming(bottomBarSongs)
                                    if (queue.isNotEmpty()) {
                                        viewModel.playQueue(queue = queue, startIndex = 0, shuffle = false)
                                    }
                                },
                                onShuffle = {
                                    val queue = mapLocalSongsToStreaming(bottomBarSongs)
                                    if (queue.isNotEmpty()) {
                                        viewModel.playQueue(
                                            queue = queue,
                                            startIndex = if (queue.size > 1) random.nextInt(queue.size) else 0,
                                            shuffle = true
                                        )
                                    }
                                },
                                onPlaySelected = {
                                    if (selectedSongs.isNotEmpty()) {
                                        val queue = mapLocalSongsToStreaming(selectedSongs)
                                        if (queue.isNotEmpty()) {
                                            viewModel.playQueue(queue = queue, startIndex = 0, shuffle = false)
                                        }
                                        multiSelectionState.clearSelection()
                                    }
                                },
                                onMoreActions = {
                                    showMultiSelectionSheet = true
                                },
                                onBack = {},
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .zIndex(10f)
                            )
                        }
                    }
                }
                    }
                }
            }
            }
        }



        if (showSongInfoSheet && selectedSongForInfo != null) {
            SongInfoBottomSheet(
                song = selectedSongForInfo,
                onDismiss = {
                    showSongInfoSheet = false
                    selectedSongForInfo = null
                },
                appSettings = appSettings,
                isStreamingMode = true
            )
        }

        if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
            MultiSelectionBottomSheet(
                selectedSongs = selectedSongs,
                favoriteSongIds = streamingFavoriteSongIds,
                onDismiss = {
                    showMultiSelectionSheet = false
                    multiSelectionState.clearSelection()
                },
                onPlayAll = {
                    val queue = mapLocalSongsToStreaming(selectedSongs)
                    if (queue.isNotEmpty()) {
                        viewModel.playQueue(queue = queue, startIndex = 0, shuffle = false)
                    }
                    multiSelectionState.clearSelection()
                },
                onAddToQueue = {
                    if (localMusicViewModel != null) {
                        val queue = mapLocalSongsToStreaming(selectedSongs)
                        queue.forEach { song -> viewModel.addSongToQueue(song, localMusicViewModel) }
                    }
                    multiSelectionState.clearSelection()
                },
                onPlayNext = {
                    if (localMusicViewModel != null) {
                        val queue = mapLocalSongsToStreaming(selectedSongs)
                        queue.reversed().forEach { song -> viewModel.playNext(song, localMusicViewModel) }
                    }
                    multiSelectionState.clearSelection()
                },
                onAddToPlaylist = {
                    songsToAddToPlaylist = selectedSongs
                    showMultiSelectionSheet = false
                    showAddToPlaylistSheet = true
                },
                onToggleLikeAll = { shouldLike ->
                    val queue = mapLocalSongsToStreaming(selectedSongs)
                    queue.forEach { song ->
                        val isFavorited = streamingFavoriteSongIds.contains(song.id)
                        if (shouldLike != isFavorited) {
                            if (shouldLike) {
                                viewModel.likeSong(song)
                            } else {
                                viewModel.unlikeSong(song)
                            }
                        }
                    }
                    multiSelectionState.clearSelection()
                },
                onGoToAlbum = {
                    selectedSongs.firstOrNull()?.let { song ->
                        openAlbumForSong(song)
                    }
                    multiSelectionState.clearSelection()
                },
                onGoToArtist = {
                    selectedSongs.firstOrNull()?.let { localSong ->
                        val resolvedArtist = sortedArtists.firstOrNull {
                            it.name.equals(localSong.artist, ignoreCase = true)
                        }
                        resolvedArtist?.let(onNavigateToArtist)
                    }
                    multiSelectionState.clearSelection()
                },
                onAddToBlacklist = null,
                onBatchEditTags = null
            )
        }

        if (showAddToPlaylistSheet && songsToAddToPlaylist.isNotEmpty()) {
            AddToPlaylistBottomSheet(
                song = songsToAddToPlaylist.first(),
                playlists = localPlaylists,
                onDismissRequest = {
                    showAddToPlaylistSheet = false
                    songsToAddToPlaylist = emptyList()
                    multiSelectionState.clearSelection()
                },
                onAddToPlaylist = { localPlaylist ->
                    val streamingSongs = mapLocalSongsToStreaming(songsToAddToPlaylist)
                    val resolvedPlaylist = savedPlaylists.firstOrNull { it.id == localPlaylist.id }
                    if (resolvedPlaylist != null && streamingSongs.isNotEmpty()) {
                        if (streamingSongs.size == 1) {
                            viewModel.addSongToPlaylist(resolvedPlaylist.id, streamingSongs.first())
                        } else {
                            viewModel.addSongsToPlaylist(resolvedPlaylist.id, streamingSongs)
                        }
                        android.widget.Toast.makeText(
                            context,
                            "Added ${streamingSongs.size} songs to ${resolvedPlaylist.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    showAddToPlaylistSheet = false
                    songsToAddToPlaylist = emptyList()
                    multiSelectionState.clearSelection()
                },
                onCreateNewPlaylist = {
                    showCreatePlaylistDialog = true
                }
            )
        }
    }

    // Create Playlist Dialog for streaming
    if (showCreatePlaylistDialog) {
        chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog(
            onDismiss = {
                showCreatePlaylistDialog = false
                songsToAddToPlaylist = emptyList()
                multiSelectionState.clearSelection()
            },
            onConfirm = { name ->
                if (songsToAddToPlaylist.isEmpty()) {
                    viewModel.createPlaylist(name)
                } else {
                    val streamingSongs = mapLocalSongsToStreaming(songsToAddToPlaylist)
                    viewModel.createPlaylist(name, streamingSongs) { playlist ->
                        android.widget.Toast.makeText(
                            context,
                            "Added ${streamingSongs.size} songs to ${playlist.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    songsToAddToPlaylist = emptyList()
                    multiSelectionState.clearSelection()
                }
                showCreatePlaylistDialog = false
            }
        )
    }
}

@Composable
private fun StreamingSongsTabPage(
    songs: List<StreamingSong>,
    isLoading: Boolean,
    onPlaySongAtIndex: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    val uniqueSongs = remember(songs) { songs.distinctBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            StreamingLibrarySectionHeader(
                title = stringResource(id = R.string.library_your_music),
                subtitle = stringResource(
                    id = R.string.streaming_home_widget_playlist_track_count,
                    uniqueSongs.size
                ),
                onPlayAll = if (uniqueSongs.isNotEmpty()) onPlayAll else null,
                onShufflePlay = if (uniqueSongs.size > 1) onShuffle else null
            )
        }

        if (isLoading && uniqueSongs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StreamingLibraryLoadingCard()
                }
            }
        } else if (uniqueSongs.isEmpty()) {
            item {
                StreamingLibraryEmptyCard(
                    icon = MaterialSymbolIcon("history", filled = true),
                    title = stringResource(id = R.string.library_no_songs),
                    subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint)
                )
            }
        } else {
            itemsIndexed(uniqueSongs, key = { _, song -> song.id }) { index, song ->
                StreamingLibrarySongRow(
                    song = song,
                    onClick = {
                        val originalIndex = songs.indexOf(song)
                        if (originalIndex >= 0) {
                            onPlaySongAtIndex(originalIndex)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamingAlbumsTabPage(
    albums: List<StreamingAlbum>,
    isLoading: Boolean,
    onOpenAlbum: (StreamingAlbum) -> Unit
) {
    val uniqueAlbums = remember(albums) { albums.distinctBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            StreamingLibrarySectionHeader(
                title = stringResource(id = R.string.library_your_albums),
                subtitle = stringResource(
                    id = R.string.library_albums_count,
                    uniqueAlbums.size
                )
            )
        }

        if (isLoading && uniqueAlbums.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StreamingLibraryLoadingCard()
                }
            }
        } else if (uniqueAlbums.isEmpty()) {
            item {
                StreamingLibraryEmptyCard(
                    icon = RhythmIcons.AlbumFilled,
                    title = stringResource(id = R.string.library_no_albums),
                    subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint)
                )
            }
        } else {
            items(uniqueAlbums, key = { it.id }) { album ->
                StreamingLibraryAlbumRow(
                    album = album,
                    onClick = { onOpenAlbum(album) }
                )
            }
        }
    }
}

@Composable
private fun StreamingArtistsTabPage(
    artists: List<StreamingArtist>,
    isLoading: Boolean,
    onOpenArtist: (StreamingArtist) -> Unit
) {
    val uniqueArtists = remember(artists) { artists.distinctBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            StreamingLibrarySectionHeader(
                title = stringResource(id = R.string.library_your_artists),
                subtitle = "${uniqueArtists.size} artists"
            )
        }

        if (isLoading && uniqueArtists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StreamingLibraryLoadingCard()
                }
            }
        } else if (uniqueArtists.isEmpty()) {
            item {
                StreamingLibraryEmptyCard(
                    icon = RhythmIcons.ArtistFilled,
                    title = stringResource(id = R.string.library_no_artists),
                    subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint)
                )
            }
        } else {
            items(uniqueArtists, key = { it.id }) { artist ->
                StreamingLibraryArtistRow(
                    artist = artist,
                    onClick = { onOpenArtist(artist) }
                )
            }
        }
    }
}

@Composable
private fun StreamingPlaylistsTabPage(
    playlists: List<StreamingPlaylist>,
    isLoading: Boolean,
    onOpenPlaylist: (StreamingPlaylist) -> Unit
) {
    val uniquePlaylists = remember(playlists) { playlists.distinctBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            StreamingLibrarySectionHeader(
                title = stringResource(id = R.string.library_your_playlists),
                subtitle = stringResource(
                    id = R.string.library_playlists_count,
                    uniquePlaylists.size
                )
            )
        }

        if (isLoading && uniquePlaylists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StreamingLibraryLoadingCard()
                }
            }
        } else if (uniquePlaylists.isEmpty()) {
            item {
                StreamingLibraryEmptyCard(
                    icon = RhythmIcons.Queue,
                    title = stringResource(id = R.string.library_no_playlists),
                    subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint)
                )
            }
        } else {
            items(uniquePlaylists, key = { it.id }) { playlist ->
                StreamingLibraryPlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) }
                )
            }
        }
    }
}

@Composable
private fun StreamingLibrarySectionHeader(
    title: String,
    subtitle: String,
    onPlayAll: (() -> Unit)? = null,
    onShufflePlay: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onPlayAll != null || onShufflePlay != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onPlayAll?.let { playAction ->
                    FilledTonalButton(onClick = playAction) {
                        Icon(
                            imageVector = RhythmIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(id = R.string.action_play))
                    }
                }

                onShufflePlay?.let { shuffleAction ->
                    FilledTonalButton(onClick = shuffleAction) {
                        Icon(
                            imageVector = RhythmIcons.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(id = R.string.action_shuffle))
                    }
                }
            }
        }
    }
}


@Composable
private fun StreamingLibraryDisconnectedCard(
    selectedServiceName: String,
    onConfigureService: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(id = R.string.streaming_home_selected_service_unavailable),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    id = R.string.streaming_home_connect_selected_service,
                    selectedServiceName
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onConfigureService,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.streaming_manage_service))
            }
        }
    }
}

@Composable
private fun StreamingLibraryLoadingCard(
    modifier: Modifier = Modifier
) {
    StreamingLibraryStateCard(
        title = stringResource(id = R.string.streaming_library_syncing),
        subtitle = stringResource(id = R.string.streaming_home_widget_empty_hint),
        icon = MaterialSymbolIcon("history", filled = true),
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        centeredContent = true,
        modifier = modifier
    )
}

@Composable
private fun StreamingLibraryStateCard(
    title: String,
    subtitle: String,
    icon: MaterialSymbolIcon,
    iconContainerColor: Color,
    iconTint: Color,
    showProgressIndicator: Boolean = false,
    centeredContent: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = if (centeredContent) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (showProgressIndicator) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = iconTint
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
                ,
                textAlign = if (centeredContent) TextAlign.Center else TextAlign.Start
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (centeredContent) TextAlign.Center else TextAlign.Start
            )

            if (actionText != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = if (centeredContent) Modifier else Modifier.fillMaxWidth()
                ) {
                    Text(text = actionText)
                }
            }
        }
    }
}

@Composable
private fun StreamingLibrarySongRow(
    song: StreamingSong,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.TrackImage(
                imageUrl = song.artworkUri,
                trackName = song.title,
                modifier = Modifier.size(66.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatCompactDuration(song.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreamingLibraryAlbumRow(
    album: StreamingAlbum,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.AlbumArt(
                imageUrl = album.artworkUri,
                albumName = album.title,
                modifier = Modifier.size(66.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = album.songCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreamingLibraryArtistRow(
    artist: StreamingArtist,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.ArtistImage(
                imageUrl = artist.artworkUri,
                artistName = artist.name,
                modifier = Modifier.size(66.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = RhythmIcons.Forward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StreamingLibraryPlaylistRow(
    playlist: StreamingPlaylist,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.PlaylistImage(
                imageUrl = playlist.artworkUri,
                playlistName = playlist.name,
                modifier = Modifier.size(66.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.description.orEmpty().ifBlank {
                        stringResource(id = R.string.streaming_home_widget_playlist_track_count, playlist.songCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = RhythmIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StreamingLibraryEmptyCard(
    icon: MaterialSymbolIcon,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deriveAlbumsFromSongs(songs: List<StreamingSong>): List<StreamingAlbum> {
    if (songs.isEmpty()) {
        return emptyList()
    }

    return songs
        .filter { it.album.isNotBlank() }
        .groupBy { song -> song.albumId ?: "${song.sourceType.name}:${song.artist.lowercase()}:${song.album.lowercase()}" }
        .values
        .sortedByDescending { albumSongs -> albumSongs.size }
        .take(40)
        .map { albumSongs ->
            val firstSong = albumSongs.first()
            StreamingAlbum(
                id = firstSong.albumId ?: "ui-derived:${firstSong.sourceType.name}:album:${firstSong.artist.lowercase()}:${firstSong.album.lowercase()}",
                title = firstSong.album,
                artist = firstSong.albumArtist?.takeIf { it.isNotBlank() } ?: firstSong.artist,
                artworkUri = albumSongs.firstNotNullOfOrNull { it.artworkUri },
                songCount = albumSongs.size,
                year = firstSong.releaseDate?.take(4)?.toIntOrNull(),
                sourceType = firstSong.sourceType,
                tracks = albumSongs
            )
        }
}

fun StreamingSong.toLibrarySong(): Song {
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
        albumId = albumId.orEmpty().takeIf { it.isNotBlank() } ?: "${sourceType.name}:${artist.lowercase()}:${album.lowercase()}",
        duration = duration,
        uri = playbackUri,
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        albumArtist = albumArtist,
        trackNumber = trackNumber ?: 0,
        year = year ?: 0,
        genre = genre,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        codec = codec
    )
}

fun StreamingPlaylist.toLibraryPlaylist(): Playlist {
    val loadedTracks = getTracks()
    val displaySongs = if (loadedTracks.isNotEmpty()) {
        loadedTracks.map { it.toLibrarySong() }
    } else if (songCount > 0) {
        // Generate placeholder songs so the count displays correctly in the UI
        (1..songCount).map { i ->
            Song(
                id = "${id}_placeholder_$i",
                title = "Track $i",
                artist = "",
                album = name,
                duration = 0L,
                uri = Uri.parse("streaming://playlist/$id/track/$i")
            )
        }
    } else {
        emptyList()
    }
    return Playlist(
        id = id,
        name = name,
        songs = displaySongs,
        dateCreated = externalId?.hashCode()?.toLong() ?: id.hashCode().toLong(),
        dateModified = snapshotId?.hashCode()?.toLong() ?: songCount.toLong(),
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    )
}

fun StreamingAlbum.toLibraryAlbum(librarySongs: List<chromahub.rhythm.app.shared.data.model.Song>): Album {
    val streamingTracks = tracks
    val matchingSongs = if (streamingTracks.isNotEmpty()) {
        streamingTracks.map { it.toLibrarySong() }
    } else if (librarySongs.isNotEmpty()) {
        librarySongs.filter {
            it.album.equals(title, ignoreCase = true) &&
                it.artist.equals(artist, ignoreCase = true)
        }
    } else {
        emptyList()
    }

    val displayedSongCount = when {
        songCount > 0 -> songCount
        streamingTracks.isNotEmpty() -> streamingTracks.size
        else -> matchingSongs.size
    }

    return Album(
        id = id,
        title = title,
        artist = artist,
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        year = year ?: 0,
        songs = matchingSongs,
        numberOfSongs = displayedSongCount
    )
}

private fun StreamingArtist.toLibraryArtist(
    librarySongs: List<Song>,
    libraryAlbums: List<Album>,
    separatorEnabled: Boolean,
    separatorDelimiters: String
): Artist {
    val matchingSongs = if (librarySongs.isNotEmpty()) {
        librarySongs.filter { song ->
            song.artist.equals(name, ignoreCase = true) ||
                ArtistSeparator.splitArtistNames(
                    song.artist,
                    delimiters = separatorDelimiters,
                    enabled = separatorEnabled
                ).any { splitName -> splitName.equals(name, ignoreCase = true) }
        }
    } else {
        getTopTracks().map { it.toLibrarySong() }
    }

    val matchingAlbums = if (libraryAlbums.isNotEmpty()) {
        libraryAlbums.filter { it.artist.equals(name, ignoreCase = true) }
    } else {
        getAlbumsList().map { it.toLibraryAlbum(matchingSongs) }
    }

    return Artist(
        id = id,
        name = name,
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        albums = matchingAlbums,
        songs = matchingSongs,
        numberOfAlbums = if (albumCount > 0) albumCount else matchingAlbums.size,
        numberOfTracks = if (songCount > 0) songCount else matchingSongs.size
    )
}



private fun formatCompactDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

private class ScrollDirectionTracker(var previousIndex: Int = 0, var previousOffset: Int = 0)

@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    val tracker = remember(this) { ScrollDirectionTracker(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            val currentIndex = firstVisibleItemIndex
            val currentOffset = firstVisibleItemScrollOffset
            val result = if (currentIndex != tracker.previousIndex) {
                currentIndex < tracker.previousIndex
            } else {
                currentOffset < tracker.previousOffset
            }
            tracker.previousIndex = currentIndex
            tracker.previousOffset = currentOffset
            result
        }
    }.value
}

@Composable
private fun LazyGridState.isScrollingUp(): Boolean {
    val tracker = remember(this) { ScrollDirectionTracker(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            val currentIndex = firstVisibleItemIndex
            val currentOffset = firstVisibleItemScrollOffset
            val result = if (currentIndex != tracker.previousIndex) {
                currentIndex < tracker.previousIndex
            } else {
                currentOffset < tracker.previousOffset
            }
            tracker.previousIndex = currentIndex
            tracker.previousOffset = currentOffset
            result
        }
    }.value
}

private enum class BottomBarButtonType { NONE, LEFT, CENTER, RIGHT }

@Composable
private fun LibraryBottomBar(
    isVisible: Boolean,
    activeTab: String,
    songs: List<Song>,
    isSelectionMode: Boolean,
    selectedSongsCount: Int,
    explorerPath: String?,
    onSelectToggle: () -> Unit,
    onCancelSelection: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySelected: () -> Unit,
    onMoreActions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 12.dp
) {
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    var lastClickedButton by remember { mutableStateOf<BottomBarButtonType?>(null) }
    LaunchedEffect(lastClickedButton) {
        if (lastClickedButton != null && lastClickedButton != BottomBarButtonType.NONE) {
            delay(220L)
            lastClickedButton = BottomBarButtonType.NONE
        }
    }

    val leftScale by animateFloatAsState(
        targetValue = if (lastClickedButton == BottomBarButtonType.LEFT) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "leftScale"
    )
    val centerScale by animateFloatAsState(
        targetValue = if (lastClickedButton == BottomBarButtonType.CENTER) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "centerScale"
    )
    val rightScale by animateFloatAsState(
        targetValue = if (lastClickedButton == BottomBarButtonType.RIGHT) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rightScale"
    )

    val centerWeightTarget = when (lastClickedButton) {
        BottomBarButtonType.CENTER -> 1.25f
        BottomBarButtonType.RIGHT -> 0.75f
        else -> 1f
    }
    val rightWeightTarget = when (lastClickedButton) {
        BottomBarButtonType.RIGHT -> 1.25f
        BottomBarButtonType.CENTER -> 0.75f
        else -> 1f
    }

    val centerWeight by animateFloatAsState(
        targetValue = centerWeightTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "centerWeight"
    )
    val rightWeight by animateFloatAsState(
        targetValue = rightWeightTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rightWeight"
    )

    val centerCornerTarget = if (lastClickedButton == BottomBarButtonType.CENTER) 14.dp else 24.dp
    val rightCornerTarget = if (lastClickedButton == BottomBarButtonType.RIGHT) 14.dp else 24.dp

    val centerCorner by animateDpAsState(
        targetValue = centerCornerTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "centerCorner"
    )
    val rightCorner by animateDpAsState(
        targetValue = rightCornerTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rightCorner"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it * 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it * 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val isTablet = LocalConfiguration.current.screenWidthDp >= 600
        val baseBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
        val bottomBarContext = LocalContext.current
        val localAppSettings = remember { AppSettings.getInstance(bottomBarContext) }
        val bottomPaddingVal = if (isTablet) {
            12.dp
        } else {
            (baseBottomPadding - 4.dp).coerceAtLeast(0.dp)
        }
        Surface(
            modifier = if (isTablet) {
                Modifier
                    .width(440.dp)
                    .padding(bottom = bottomPaddingVal)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomPaddingVal)
            },
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showLeftButton = isSelectionMode || activeTab == "SONGS" || (activeTab == "EXPLORER" && explorerPath != null)
                
                AnimatedVisibility(
                    visible = showLeftButton,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            lastClickedButton = BottomBarButtonType.LEFT
                            if (isSelectionMode) {
                                onCancelSelection()
                            } else if (activeTab == "EXPLORER") {
                                onBack()
                            } else {
                                onSelectToggle()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = leftScale
                                scaleY = leftScale
                            },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "leftButtonIcon"
                        ) { selectionMode ->
                            if (selectionMode) {
                                Icon(
                                    imageVector = RhythmIcons.Close,
                                    contentDescription = "Cancel selection",
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (activeTab == "EXPLORER") RhythmIcons.Back else MaterialSymbolIcon("check_box"),
                                    contentDescription = if (activeTab == "EXPLORER") "Back" else "Select songs",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                val centerButtonWeightTarget = if (isSelectionMode) 1f else centerWeightTarget
                val animatedCenterWeight by animateFloatAsState(
                    targetValue = centerButtonWeightTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "animatedCenterWeight"
                )

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        lastClickedButton = BottomBarButtonType.CENTER
                        if (isSelectionMode) {
                            onPlaySelected()
                        } else {
                            onPlayAll()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(animatedCenterWeight)
                        .graphicsLayer {
                            scaleX = centerScale
                            scaleY = centerScale
                        },
                    shape = RoundedCornerShape(centerCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(150, delayMillis = 75)) +
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 75)))
                                .togetherWith(fadeOut(animationSpec = tween(75)))
                        },
                        label = "centerButtonContent"
                    ) { selectionMode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectionMode) "Play ($selectedSongsCount)" else "Play All",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                val rightButtonWeightTarget = if (isSelectionMode) 0.001f else rightWeightTarget
                val animatedRightWeight by animateFloatAsState(
                    targetValue = rightButtonWeightTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "animatedRightWeight"
                )

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        lastClickedButton = BottomBarButtonType.RIGHT
                        if (isSelectionMode) {
                            onMoreActions()
                        } else {
                            onShuffle()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .then(
                            if (isSelectionMode) {
                                Modifier.width(48.dp)
                            } else {
                                Modifier.weight(animatedRightWeight.coerceAtLeast(0.001f))
                            }
                        )
                        .graphicsLayer {
                            scaleX = rightScale
                            scaleY = rightScale
                        },
                    shape = RoundedCornerShape(if (isSelectionMode) 24.dp else rightCorner),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = if (isSelectionMode) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(150, delayMillis = 75)) +
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 75)))
                                .togetherWith(fadeOut(animationSpec = tween(75)))
                        },
                        label = "rightButtonContent"
                    ) { selectionMode ->
                        if (selectionMode) {
                            Icon(
                                imageVector = RhythmIcons.More,
                                contentDescription = "More actions",
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_shuffle),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

