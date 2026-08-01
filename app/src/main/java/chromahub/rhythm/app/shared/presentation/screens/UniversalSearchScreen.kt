package chromahub.rhythm.app.shared.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.findAlbumForSong
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.SettingScope
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.dialogs.SwitchModeDialog
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.GenreUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.M3ImageUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSearchScreen(
    localViewModel: MusicViewModel = viewModel(),
    streamingViewModel: StreamingMusicViewModel = viewModel(),
    onLocalSongClick: (Song) -> Unit = {},
    onLocalAlbumClick: (Album) -> Unit = {},
    onLocalArtistClick: (Artist) -> Unit = {},
    onLocalPlaylistClick: (Playlist) -> Unit = {},
    onStreamingSongClick: (StreamingSong) -> Unit = {},
    onStreamingAlbumClick: (StreamingAlbum) -> Unit = {},
    onStreamingArtistClick: (StreamingArtist) -> Unit = {},
    onStreamingPlaylistClick: (StreamingPlaylist) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val appMode by appSettings.appMode.collectAsState()
    val showKeyboardOnSearchOpen by appSettings.showKeyboardOnSearchOpen.collectAsState()

    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val horizontalPadding = if (isTablet) 32.dp else 24.dp

    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (showKeyboardOnSearchOpen) {
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failure in unexpected states
            }
        }
    }

    var showFilters by remember { mutableStateOf(false) }
    var filterSongs by remember { mutableStateOf(true) }
    var filterAlbums by remember { mutableStateOf(true) }
    var filterArtists by remember { mutableStateOf(true) }
    var filterPlaylists by remember { mutableStateOf(true) }

    var showSwitchDialog by remember { mutableStateOf(false) }
    var pendingAction: (() -> Unit)? by remember { mutableStateOf(null) }
    var targetModeForPendingAction by remember { mutableStateOf("") }

    var showAllSongsPage by remember { mutableStateOf(false) }
    var showSongOptionsSheet by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Any?>(null) }

    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }
    var isSongInfoStreaming by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    var pendingMetadataEditCompleteCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            localViewModel.completeMetadataWriteAfterPermission(
                onSuccess = {
                    Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, Toast.LENGTH_SHORT).show()
                    pendingMetadataEditCompleteCallback?.invoke(true)
                    pendingMetadataEditCompleteCallback = null
                },
                onError = { errorMessage ->
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    pendingMetadataEditCompleteCallback?.invoke(false)
                    pendingMetadataEditCompleteCallback = null
                }
            )
        } else {
            localViewModel.cancelPendingMetadataWrite()
            pendingMetadataEditCompleteCallback?.invoke(false)
            pendingMetadataEditCompleteCallback = null
            Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, Toast.LENGTH_LONG).show()
        }
    }

    val localSongs by localViewModel.filteredSongs.collectAsState()
    val fullLocalAlbums by localViewModel.filteredAlbums.collectAsState()
    val localArtists by localViewModel.filteredArtists.collectAsState()
    val localPlaylists by localViewModel.playlists.collectAsState()
    val searchHistory by localViewModel.searchHistory.collectAsState()
    val favoriteSongs by localViewModel.favoriteSongs.collectAsState()

    val streamingQuery by streamingViewModel.searchQuery.collectAsState()
    val streamingResults by streamingViewModel.searchResults.collectAsState()
    val isStreamingLoading by streamingViewModel.isLoading.collectAsState()
    val streamingLikedSongs by streamingViewModel.likedSongs.collectAsState()

    val isGenreDetectionComplete by localViewModel.isGenreDetectionComplete.collectAsState()

    val genreBrowseSummaries by localViewModel.genreBrowseSummaries.collectAsState()

    LaunchedEffect(query) {
        if (query.isNotBlank() && streamingQuery != query) {
            streamingViewModel.search(query)
        }
    }

    val matchedLocalSongs = remember(query, localSongs, filterSongs) {
        if (!filterSongs || query.isBlank()) emptyList()
        else {
            val lowerQuery = query.lowercase()
            localSongs.filter {
                it.title.lowercase().contains(lowerQuery) ||
                        it.artist.lowercase().contains(lowerQuery) ||
                        it.album.lowercase().contains(lowerQuery) ||
                        GenreUtils.matchesGenreQuery(it.genre, lowerQuery)
            }
        }
    }
    val matchedLocalAlbums = remember(query, fullLocalAlbums, filterAlbums) {
        if (!filterAlbums || query.isBlank()) emptyList()
        else {
            val lowerQuery = query.lowercase()
            fullLocalAlbums.filter { album ->
                album.title.contains(query, true) ||
                album.artist.contains(query, true) ||
                album.songs.any { it.title.lowercase().contains(lowerQuery) }
            }
        }
    }
    val matchedLocalArtists = remember(query, localArtists, filterArtists) {
        if (!filterArtists || query.isBlank()) emptyList()
        else localArtists.filter { it.name.contains(query, true) }
    }
    val matchedLocalPlaylists = remember(query, localPlaylists, filterPlaylists) {
        if (!filterPlaylists || query.isBlank()) emptyList()
        else localPlaylists.filter { it.name.contains(query, true) }
    }

    val matchedStreamingSongs = if (filterSongs) streamingResults.songs else emptyList()
    val matchedStreamingAlbums = if (filterAlbums) streamingResults.albums else emptyList()
    val matchedStreamingArtists = if (filterArtists) streamingResults.artists else emptyList()
    val matchedStreamingPlaylists = if (filterPlaylists) streamingResults.playlists else emptyList()

    val hasResults = matchedLocalSongs.isNotEmpty() || matchedStreamingSongs.isNotEmpty() ||
            matchedLocalAlbums.isNotEmpty() || matchedStreamingAlbums.isNotEmpty() ||
            matchedLocalArtists.isNotEmpty() || matchedStreamingArtists.isNotEmpty() ||
            matchedLocalPlaylists.isNotEmpty() || matchedStreamingPlaylists.isNotEmpty()

    val handleAction = { itemMode: String, action: () -> Unit ->
        if (itemMode == appMode) {
            action()
        } else {
            targetModeForPendingAction = itemMode
            pendingAction = action
            showSwitchDialog = true
        }
    }

    val handleBack = {
        if (showAllSongsPage) {
            showAllSongsPage = false
        } else if (query.isNotEmpty() || showFilters) {
            query = ""
            showFilters = false
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    if (showSwitchDialog) {
        SwitchModeDialog(
            targetMode = targetModeForPendingAction,
            onDismissRequest = {
                showSwitchDialog = false
                pendingAction = null
            },
            onConfirm = {
                appSettings.setAppMode(targetModeForPendingAction)
                pendingAction?.invoke()
                showSwitchDialog = false
                pendingAction = null
            }
        )
    }

    val statusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val searchBarHeightPadding = 88.dp + (if (showFilters) 48.dp else 0.dp)
    val totalBottomPadding = searchBarHeightPadding + navigationBarsBottom + 16.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = query.isBlank(),
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(initialOffsetY = { -it / 6 }) + fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(250)))
                } else {
                    (slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutVertically(targetOffsetY = { -it / 6 }) + fadeOut(animationSpec = tween(250)))
                }
            },
            label = "SearchTransition",
            modifier = Modifier.fillMaxSize()
        ) { isBlank ->
            if (isBlank) {
                if (isTablet && searchHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                top = statusBarsTop + 16.dp,
                                bottom = totalBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "history_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp)
                                        .animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = context.getString(R.string.search_recent_searches),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            localViewModel.clearSearchHistory()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(RhythmIcons.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Text(stringResource(R.string.ui_clear_all), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        }
                                    }
                                }
                            }
                            item(key = "history_list") {
                                val historyItems = searchHistory.take(8).map { item ->
                                    Material3SettingsItem(
                                        icon = MaterialSymbolIcon("history", filled = true),
                                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        iconBackgroundTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                                        iconShape = RoundedCornerShape(12.dp),
                                        title = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        scope = SettingScope.BOTH,
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    localViewModel.removeSearchQuery(item)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                                            ) {
                                                Icon(MaterialSymbolIcon("clear", filled = true), contentDescription = stringResource(R.string.universalsearchscreen_remove_search), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            query = item
                                        }
                                    )
                                }
                                Box(modifier = Modifier.animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))) {
                                    Material3SettingsGroup(items = historyItems)
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                top = statusBarsTop + 16.dp,
                                bottom = totalBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            universalGenreBrowseItems(
                                genreSummaries = genreBrowseSummaries,
                                columnsCount = 3,
                                isGenreDetectionComplete = isGenreDetectionComplete,
                                onGenreClick = { genre ->
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    query = genre
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        contentPadding = PaddingValues(
                            top = statusBarsTop + 16.dp,
                            bottom = totalBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (searchHistory.isNotEmpty()) {
                            item(key = "history_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp)
                                        .animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = context.getString(R.string.search_recent_searches),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            localViewModel.clearSearchHistory()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(RhythmIcons.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Text(stringResource(R.string.ui_clear_all), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        }
                                    }
                                }
                            }
                            item(key = "history_list") {
                                val historyItems = searchHistory.take(8).map { item ->
                                    Material3SettingsItem(
                                        icon = MaterialSymbolIcon("history", filled = true),
                                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        iconBackgroundTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                                        iconShape = RoundedCornerShape(12.dp),
                                        title = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        scope = SettingScope.BOTH,
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    localViewModel.removeSearchQuery(item)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                                            ) {
                                                Icon(MaterialSymbolIcon("clear", filled = true), contentDescription = stringResource(R.string.universalsearchscreen_remove_search), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            query = item
                                        }
                                    )
                                }
                                Box(modifier = Modifier.animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))) {
                                    Material3SettingsGroup(items = historyItems)
                                }
                            }
                        }

                        if (searchHistory.isEmpty()) {
                            item(key = "empty_prompt") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 80.dp)
                                        .animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomStart = 16.dp, bottomEnd = 48.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.size(120.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(RhythmIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(48.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(stringResource(R.string.universalsearchscreen_search_across_local_streaming), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        universalGenreBrowseItems(
                            genreSummaries = genreBrowseSummaries,
                            columnsCount = 2,
                            isGenreDetectionComplete = isGenreDetectionComplete,
                            onGenreClick = { genre ->
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                query = genre
                            }
                        )
                    }
                }
            } else {
                if (!hasResults && !isStreamingLoading) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        contentPadding = PaddingValues(
                            top = statusBarsTop + 16.dp,
                            bottom = totalBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "no_results") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp)
                                    .animateItem(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    modifier = Modifier.size(120.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = RhythmIcons.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(60.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.no_results_found),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "We couldn't find anything matching \"$query\".",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                } else if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left Column (Songs + Playlists)
                        LazyColumn(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                top = statusBarsTop + 16.dp,
                                bottom = totalBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isStreamingLoading && !hasResults) {
                                item(key = "loading_left") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                        WavyLoader()
                                    }
                                }
                            } else {
                                val allSongs = buildList {
                                    matchedLocalSongs.take(3).forEach { song ->
                                        add(SongSearchItem("LOCAL", song.title, "${song.artist} • ${song.album}", song.artworkUri, song) {
                                            if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                            handleAction("LOCAL") { onLocalSongClick(song) }
                                        })
                                    }
                                    matchedStreamingSongs.take(3).forEach { song ->
                                        add(SongSearchItem("STREAMING", song.title, "${song.artist} • ${song.album}", song.artworkUri, song) {
                                            if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                            handleAction("STREAMING") { onStreamingSongClick(song) }
                                        })
                                    }
                                }

                                if (allSongs.isNotEmpty()) {
                                    item(key = "songs_group") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_tab_songs),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                            )

                                            Column(
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                allSongs.forEachIndexed { index, item ->
                                                    UniversalSearchSongItem(
                                                        title = item.title,
                                                        subtitle = item.subtitle,
                                                        artworkUri = item.artworkUri,
                                                        mode = item.mode,
                                                        onClick = item.onClick,
                                                        onMoreClick = {
                                                            selectedSongForOptions = item.originalSong
                                                            showSongOptionsSheet = true
                                                        },
                                                        haptics = haptics,
                                                        index = index,
                                                        totalCount = allSongs.size
                                                    )
                                                }
                                            }

                                            val totalSongs = matchedLocalSongs.size + matchedStreamingSongs.size
                                            if (totalSongs > allSongs.size) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                    ),
                                                    shape = RoundedCornerShape(16.dp),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp)
                                                        .clickable {
                                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                            showAllSongsPage = true
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = context.getString(R.string.search_view_all_songs),
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                            Text(
                                                                text = "See all $totalSongs songs",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector = RhythmIcons.Back,
                                                            contentDescription = stringResource(R.string.cd_view_all),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 180f }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                val allPlaylists = buildList {
                                    matchedLocalPlaylists.take(5).forEach { playlist ->
                                        add(Triple("LOCAL", playlist.name, "Local Playlist") to {
                                            if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                            handleAction("LOCAL") { onLocalPlaylistClick(playlist) }
                                        })
                                    }
                                    matchedStreamingPlaylists.take(5).forEach { playlist ->
                                        add(Triple("STREAMING", playlist.name, "Streaming Playlist") to {
                                            if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                            handleAction("STREAMING") { onStreamingPlaylistClick(playlist) }
                                        })
                                    }
                                }
                                if (allPlaylists.isNotEmpty()) {
                                    item(key = "playlists_group") {
                                        val playlistItems = allPlaylists.map { (info, action) ->
                                            val (mode, title, subtitle) = info
                                            Material3SettingsItem(
                                                icon = if (mode == "STREAMING") MaterialSymbolIcon("cloud", filled = true) else RhythmIcons.Playlist,
                                                scope = if (mode == "STREAMING") SettingScope.STREAMING else SettingScope.LOCAL,
                                                iconBackgroundTint = if (mode == "STREAMING") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                description = { Text(subtitle) },
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    action()
                                                }
                                            )
                                        }
                                        Box(modifier = Modifier.animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))) {
                                            Material3SettingsGroup(title = stringResource(R.string.settings_tab_playlists), items = playlistItems)
                                        }
                                    }
                                }
                            }
                        }

                        // Right Column (Albums + Artists)
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                top = statusBarsTop + 16.dp,
                                bottom = totalBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val allAlbums = buildList {
                                matchedLocalAlbums.take(6).forEach { album ->
                                    add(SearchGridItem("LOCAL", album.title, album.artist, album.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("LOCAL") { onLocalAlbumClick(album) }
                                    })
                                }
                                matchedStreamingAlbums.take(6).forEach { album ->
                                    add(SearchGridItem("STREAMING", album.title, album.artist, album.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("STREAMING") { onStreamingAlbumClick(album) }
                                    })
                                }
                            }
                            if (allAlbums.isNotEmpty()) {
                                item(key = "albums_header") {
                                    Text(stringResource(R.string.settings_tab_albums), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).animateItem())
                                }
                                item(key = "albums_grid") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateItem()) {
                                        items(allAlbums.size, key = { i -> "album_${allAlbums[i].title}_$i" }) { i ->
                                            SearchGridCard(item = allAlbums[i], haptics = haptics, context = context, isAlbum = true)
                                        }
                                    }
                                }
                            }

                            val allArtists = buildList {
                                matchedLocalArtists.take(6).forEach { artist ->
                                    add(SearchGridItem("LOCAL", artist.name, "${artist.numberOfTracks} tracks", artist.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("LOCAL") { onLocalArtistClick(artist) }
                                    })
                                }
                                matchedStreamingArtists.take(6).forEach { artist ->
                                    add(SearchGridItem("STREAMING", artist.name, "Streaming Artist", artist.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("STREAMING") { onStreamingArtistClick(artist) }
                                    })
                                }
                            }
                            if (allArtists.isNotEmpty()) {
                                item(key = "artists_header") {
                                    Text(stringResource(R.string.settings_tab_artists), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).animateItem())
                                }
                                item(key = "artists_grid") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateItem()) {
                                        items(allArtists.size, key = { i -> "artist_${allArtists[i].title}_$i" }) { i ->
                                            SearchGridCard(item = allArtists[i], haptics = haptics, context = context, isAlbum = false)
                                        }
                                    }
                                }
                            }

                            if (isStreamingLoading) {
                                item(key = "streaming_loading_right") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).animateItem(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            WavyLoader()
                                            Text(stringResource(R.string.universalsearchscreen_loading_streaming_results), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        contentPadding = PaddingValues(
                            top = statusBarsTop + 16.dp,
                            bottom = totalBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isStreamingLoading && !hasResults) {
                            item(key = "loading") {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).animateItem(), contentAlignment = Alignment.Center) {
                                    WavyLoader()
                                }
                            }
                        } else {
                            val allSongs = buildList {
                                matchedLocalSongs.take(3).forEach { song ->
                                    add(SongSearchItem("LOCAL", song.title, "${song.artist} • ${song.album}", song.artworkUri, song) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("LOCAL") { onLocalSongClick(song) }
                                    })
                                }
                                matchedStreamingSongs.take(3).forEach { song ->
                                    add(SongSearchItem("STREAMING", song.title, "${song.artist} • ${song.album}", song.artworkUri, song) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("STREAMING") { onStreamingSongClick(song) }
                                    })
                                }
                            }

                            if (allSongs.isNotEmpty()) {
                                item(key = "songs_group") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_tab_songs),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                        )

                                        Column(
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            allSongs.forEachIndexed { index, item ->
                                                UniversalSearchSongItem(
                                                    title = item.title,
                                                    subtitle = item.subtitle,
                                                    artworkUri = item.artworkUri,
                                                    mode = item.mode,
                                                    onClick = item.onClick,
                                                    onMoreClick = {
                                                        selectedSongForOptions = item.originalSong
                                                        showSongOptionsSheet = true
                                                    },
                                                    haptics = haptics,
                                                    index = index,
                                                    totalCount = allSongs.size
                                                )
                                            }
                                        }

                                        val totalSongs = matchedLocalSongs.size + matchedStreamingSongs.size
                                        if (totalSongs > allSongs.size) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp)
                                                    .clickable {
                                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                        showAllSongsPage = true
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = context.getString(R.string.search_view_all_songs),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Text(
                                                            text = "See all $totalSongs songs",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = RhythmIcons.Back,
                                                        contentDescription = stringResource(R.string.cd_view_all),
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 180f }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val allAlbums = buildList {
                                matchedLocalAlbums.take(6).forEach { album ->
                                    add(SearchGridItem("LOCAL", album.title, album.artist, album.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("LOCAL") { onLocalAlbumClick(album) }
                                    })
                                }
                                matchedStreamingAlbums.take(6).forEach { album ->
                                    add(SearchGridItem("STREAMING", album.title, album.artist, album.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("STREAMING") { onStreamingAlbumClick(album) }
                                    })
                                }
                            }
                            if (allAlbums.isNotEmpty()) {
                                item(key = "albums_header") {
                                    Text(stringResource(R.string.settings_tab_albums), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).animateItem())
                                }
                                item(key = "albums_grid") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateItem()) {
                                        items(allAlbums.size, key = { i -> "album_${allAlbums[i].title}_$i" }) { i ->
                                            SearchGridCard(item = allAlbums[i], haptics = haptics, context = context, isAlbum = true)
                                        }
                                    }
                                }
                            }

                            val allArtists = buildList {
                                matchedLocalArtists.take(6).forEach { artist ->
                                    add(SearchGridItem("LOCAL", artist.name, "${artist.numberOfTracks} tracks", artist.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("LOCAL") { onLocalArtistClick(artist) }
                                    })
                                }
                                matchedStreamingArtists.take(6).forEach { artist ->
                                    add(SearchGridItem("STREAMING", artist.name, "Streaming Artist", artist.artworkUri) {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("STREAMING") { onStreamingArtistClick(artist) }
                                    })
                                }
                            }
                            if (allArtists.isNotEmpty()) {
                                item(key = "artists_header") {
                                    Text(stringResource(R.string.settings_tab_artists), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).animateItem())
                                }
                                item(key = "artists_grid") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.animateItem()) {
                                        items(allArtists.size, key = { i -> "artist_${allArtists[i].title}_$i" }) { i ->
                                            SearchGridCard(item = allArtists[i], haptics = haptics, context = context, isAlbum = false)
                                        }
                                    }
                                }
                            }

                            val allPlaylists = buildList {
                                matchedLocalPlaylists.take(5).forEach { playlist ->
                                    add(Triple("LOCAL", playlist.name, "Local Playlist") to {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("LOCAL") { onLocalPlaylistClick(playlist) }
                                    })
                                }
                                matchedStreamingPlaylists.take(5).forEach { playlist ->
                                    add(Triple("STREAMING", playlist.name, "Streaming Playlist") to {
                                        if (query.isNotBlank()) localViewModel.addSearchQuery(query)
                                        handleAction("STREAMING") { onStreamingPlaylistClick(playlist) }
                                    })
                                }
                            }
                            if (allPlaylists.isNotEmpty()) {
                                item(key = "playlists_group") {
                                    val playlistItems = allPlaylists.map { (info, action) ->
                                        val (mode, title, subtitle) = info
                                        Material3SettingsItem(
                                            icon = if (mode == "STREAMING") MaterialSymbolIcon("cloud", filled = true) else RhythmIcons.Playlist,
                                            scope = if (mode == "STREAMING") SettingScope.STREAMING else SettingScope.LOCAL,
                                            iconBackgroundTint = if (mode == "STREAMING") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            description = { Text(subtitle) },
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                action()
                                            }
                                        )
                                    }
                                    Box(modifier = Modifier.animateItem(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))) {
                                        Material3SettingsGroup(title = stringResource(R.string.settings_tab_playlists), items = playlistItems)
                                    }
                                }
                            }

                            if (isStreamingLoading) {
                                item(key = "streaming_loading") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).animateItem(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            WavyLoader()
                                            Text(stringResource(R.string.universalsearchscreen_loading_streaming_results), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Search Bar & Controls Area
        Column(
            modifier = Modifier
                .then(
                    if (isTablet) {
                        Modifier.widthIn(max = 680.dp).align(Alignment.BottomCenter)
                    } else {
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    }
                )
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.2f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
                )
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = horizontalPadding)
                .padding(top = 16.dp, bottom = 12.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    IconButton(onClick = handleBack) {
                        Icon(
                            RhythmIcons.Back,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(RhythmIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(stringResource(R.string.universalsearchscreen_search_everywhere), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                query = ""
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(MaterialSymbolIcon("clear", filled = true), contentDescription = stringResource(R.string.ui_clear), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    IconButton(onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showFilters = !showFilters
                    }) {
                        Icon(
                            RhythmIcons.FilterList,
                            contentDescription = stringResource(R.string.cd_filters),
                            tint = if (showFilters) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val songsCornerRadius by animateDpAsState(
                        targetValue = if (filterSongs) 24.dp else 12.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "songsCornerRadius"
                    )
                    FilterChip(
                        selected = filterSongs,
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            filterSongs = !filterSongs
                        },
                        label = { Text(stringResource(R.string.settings_tab_songs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                        leadingIcon = if (filterSongs) {
                            {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(songsCornerRadius),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                    val albumsCornerRadius by animateDpAsState(
                        targetValue = if (filterAlbums) 24.dp else 12.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "albumsCornerRadius"
                    )
                    FilterChip(
                        selected = filterAlbums,
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            filterAlbums = !filterAlbums
                        },
                        label = { Text(stringResource(R.string.settings_tab_albums), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                        leadingIcon = if (filterAlbums) {
                            {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(albumsCornerRadius),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                    val artistsCornerRadius by animateDpAsState(
                        targetValue = if (filterArtists) 24.dp else 12.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "artistsCornerRadius"
                    )
                    FilterChip(
                        selected = filterArtists,
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            filterArtists = !filterArtists
                        },
                        label = { Text(stringResource(R.string.settings_tab_artists), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                        leadingIcon = if (filterArtists) {
                            {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(artistsCornerRadius),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                    val playlistsCornerRadius by animateDpAsState(
                        targetValue = if (filterPlaylists) 24.dp else 12.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "playlistsCornerRadius"
                    )
                    FilterChip(
                        selected = filterPlaylists,
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            filterPlaylists = !filterPlaylists
                        },
                        label = { Text(stringResource(R.string.settings_tab_playlists), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                        leadingIcon = if (filterPlaylists) {
                            {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(playlistsCornerRadius),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showAllSongsPage,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            UniversalAllSongsPage(
                localSongs = matchedLocalSongs,
                streamingSongs = matchedStreamingSongs,
                onBack = { showAllSongsPage = false },
                onOptionsClick = { songObj ->
                    selectedSongForOptions = songObj
                    showSongOptionsSheet = true
                },
                onLocalSongClick = { song -> handleAction("LOCAL") { onLocalSongClick(song) } },
                onStreamingSongClick = { song -> handleAction("STREAMING") { onStreamingSongClick(song) } },
                haptics = haptics
            )
        }

        if (showSongOptionsSheet && selectedSongForOptions != null) {
            val songObj = selectedSongForOptions!!
            val isLocal = songObj is Song
            val isFavorite = if (isLocal) {
                favoriteSongs.contains(songObj.id)
            } else {
                streamingLikedSongs.any { it.id == (songObj as StreamingSong).id }
            }

            UniversalSongOptionsBottomSheet(
                songObj = songObj,
                onDismiss = { showSongOptionsSheet = false },
                onPlayNext = {
                    handleAction(if (isLocal) "LOCAL" else "STREAMING") {
                        if (isLocal) {
                            localViewModel.playNext(songObj)
                        } else {
                            streamingViewModel.playNext(songObj as StreamingSong, localViewModel)
                        }
                    }
                    showSongOptionsSheet = false
                },
                onAddToQueue = {
                    handleAction(if (isLocal) "LOCAL" else "STREAMING") {
                        if (isLocal) {
                            localViewModel.addSongToQueue(songObj)
                        } else {
                            streamingViewModel.addSongToQueue(songObj as StreamingSong, localViewModel)
                        }
                    }
                    showSongOptionsSheet = false
                },
                onAddToPlaylist = {
                    if (isLocal) {
                        selectedSongForPlaylist = songObj
                    } else {
                        selectedSongForPlaylist = (songObj as StreamingSong).toLocalSong()
                    }
                    showAddToPlaylistSheet = true
                    showSongOptionsSheet = false
                },
                onToggleFavorite = {
                    if (isLocal) {
                        localViewModel.toggleFavorite(songObj)
                    } else {
                        val streamingSong = songObj as StreamingSong
                        val isCurrentlyLiked = streamingLikedSongs.any { it.id == streamingSong.id }
                        if (isCurrentlyLiked) {
                            streamingViewModel.unlikeSong(streamingSong)
                            Toast.makeText(context, R.string.universalsearchscreen_removed_from_streaming_favorites, Toast.LENGTH_SHORT).show()
                        } else {
                            streamingViewModel.likeSong(streamingSong)
                            Toast.makeText(context, R.string.universalsearchscreen_added_to_streaming_favorites, Toast.LENGTH_SHORT).show()
                        }
                    }
                    showSongOptionsSheet = false
                },
                isFavorite = isFavorite,
                onShowSongInfo = {
                    if (isLocal) {
                        selectedSongForInfo = songObj
                        isSongInfoStreaming = false
                    } else {
                        selectedSongForInfo = (songObj as StreamingSong).toLocalSong()
                        isSongInfoStreaming = true
                    }
                    showSongInfoSheet = true
                    showSongOptionsSheet = false
                },
                onGoToAlbum = {
                    showSongOptionsSheet = false
                    if (isLocal) {
                        val album = localViewModel.filteredAlbums.value.findAlbumForSong(songObj)
                        if (album != null) {
                            handleAction("LOCAL") { onLocalAlbumClick(album) }
                        } else Toast.makeText(context, R.string.universalsearchscreen_album_not_found, Toast.LENGTH_SHORT).show()
                    } else {
                        val streamingSong = songObj as StreamingSong
                        if (streamingSong.albumId != null) {
                            val streamingAlbum = StreamingAlbum(
                                id = streamingSong.albumId,
                                title = streamingSong.album,
                                artist = streamingSong.artist,
                                artworkUri = streamingSong.artworkUri,
                                songCount = 0,
                                year = null,
                                sourceType = streamingSong.sourceType
                            )
                            handleAction("STREAMING") { onStreamingAlbumClick(streamingAlbum) }
                        } else {
                            Toast.makeText(context, R.string.universalsearchscreen_album_not_found, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onGoToArtist = {
                    showSongOptionsSheet = false
                    if (isLocal) {
                        val separatorEnabled = appSettings.artistSeparatorEnabled.value
                        val delimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { "/;,+&" }
                        val songArtistNames = chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                            artistName = songObj.artist,
                            delimiters = delimiters,
                            enabled = separatorEnabled
                        )
                        val artist = songArtistNames.firstNotNullOfOrNull { name ->
                            localArtists.find { it.name.equals(name, ignoreCase = true) }
                        }
                        if (artist != null) {
                            handleAction("LOCAL") { onLocalArtistClick(artist) }
                        } else Toast.makeText(context, R.string.universalsearchscreen_artist_not_found, Toast.LENGTH_SHORT).show()
                    } else {
                        val streamingSong = songObj as StreamingSong
                        val streamingArtist = StreamingArtist(
                            id = streamingSong.albumArtist ?: streamingSong.artist,
                            name = streamingSong.artist,
                            artworkUri = streamingSong.artworkUri,
                            songCount = 0,
                            albumCount = 0,
                            sourceType = streamingSong.sourceType
                        )
                        handleAction("STREAMING") { onStreamingArtistClick(streamingArtist) }
                    }
                },
                onAddToBlacklist = {
                    handleAction("LOCAL") {
                        if (isLocal) {
                            appSettings.addToBlacklist(songObj.id)
                            Toast.makeText(context, context.getString(R.string.song_added_to_blacklist_format, songObj.title), Toast.LENGTH_SHORT).show()
                        }
                    }
                    showSongOptionsSheet = false
                },
                haptics = haptics
            )
        }

        if (showAddToPlaylistSheet && selectedSongForPlaylist != null) {
            AddToPlaylistBottomSheet(
                song = selectedSongForPlaylist!!,
                playlists = localPlaylists,
                onDismissRequest = { showAddToPlaylistSheet = false },
                onAddToPlaylist = { playlist ->
                    localViewModel.addSongToPlaylist(selectedSongForPlaylist!!, playlist.id) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    showAddToPlaylistSheet = false
                },
                onCreateNewPlaylist = {
                    showAddToPlaylistSheet = false
                    showCreatePlaylistDialog = true
                }
            )
        }

        if (showSongInfoSheet && selectedSongForInfo != null) {
            SongInfoBottomSheet(
                song = selectedSongForInfo,
                onDismiss = { showSongInfoSheet = false },
                appSettings = appSettings,
                isStreamingMode = isSongInfoStreaming,
                onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                    pendingMetadataEditCompleteCallback = onComplete
                    try {
                        localViewModel.saveMetadataChanges(
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
                                    Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully_to, Toast.LENGTH_SHORT).show()
                                }
                                pendingMetadataEditCompleteCallback?.invoke(true)
                                pendingMetadataEditCompleteCallback = null
                            },
                            onError = { errorMessage ->
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
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
                                    Toast.makeText(context, context.getString(R.string.failed_to_request_permission, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    localViewModel.cancelPendingMetadataWrite()
                                    pendingMetadataEditCompleteCallback?.invoke(false)
                                    pendingMetadataEditCompleteCallback = null
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.unexpected_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                        android.util.Log.w("UniversalSearchScreen", "Metadata update failed for song: ${selectedSongForInfo!!.title}", e)
                        pendingMetadataEditCompleteCallback?.invoke(false)
                        pendingMetadataEditCompleteCallback = null
                    }
                },
                onShowLyricsEditor = { }
            )
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                song = selectedSongForPlaylist,
                onConfirm = { name ->
                    localViewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                },
                onConfirmWithSong = { name ->
                    selectedSongForPlaylist?.let { song ->
                        localViewModel.createPlaylist(name, listOf(song)) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        localViewModel.createPlaylist(name)
                    }
                    showCreatePlaylistDialog = false
                }
            )
        }
    }
}

private fun StreamingSong.toLocalSong(): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist,
        album = this.album,
        albumId = this.albumId ?: "",
        duration = this.duration,
        uri = Uri.parse(this.streamingUrl ?: this.previewUrl ?: ""),
        artworkUri = this.artworkUri?.let { Uri.parse(it) },
        trackNumber = 0,
        year = 0,
        genre = null,
        albumArtist = this.albumArtist
    )
}

data class SearchGridItem(
    val mode: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: Any?,
    val onClick: () -> Unit
)

data class SongSearchItem(
    val mode: String,
    val title: String,
    val subtitle: String,
    val artworkUri: Any?,
    val originalSong: Any,
    val onClick: () -> Unit
)

private fun groupedSongItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
fun UniversalSearchSongItem(
    title: String,
    subtitle: String,
    artworkUri: Any?,
    mode: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    index: Int = 0,
    totalCount: Int = 1
) {
    val context = LocalContext.current
    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = groupedSongItemShape(index, totalCount),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.TrackImage(
                imageUrl = artworkUri,
                trackName = title,
                modifier = Modifier.size(48.dp),
                applyExpressiveShape = true
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = if (mode == "STREAMING")
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (mode == "STREAMING") MaterialSymbolIcon("cloud", filled = true) else RhythmIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (mode == "STREAMING")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (mode == "STREAMING") "Streaming" else "Local",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = if (mode == "STREAMING")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onMoreClick()
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(
                    imageVector = RhythmIcons.More,
                    contentDescription = stringResource(R.string.content_desc_more_options),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchGridCard(
    item: SearchGridItem,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: android.content.Context,
    isAlbum: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "grid_card_scale"
    )

    val expressiveShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 8.dp,
        bottomEnd = 28.dp,
        bottomStart = 8.dp
    )

    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            item.onClick()
        },
        modifier = modifier
            .width(if (isAlbum) 160.dp else 140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = expressiveShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAlbum) {
                M3ImageUtils.AlbumArt(
                    imageUrl = item.artworkUrl,
                    albumName = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    applyExpressiveShape = true
                )
            } else {
                M3ImageUtils.ArtistImage(
                    imageUrl = item.artworkUrl,
                    artistName = item.title,
                    modifier = Modifier
                        .size(120.dp),
                    applyExpressiveShape = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = if (item.mode == "STREAMING")
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                else
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (item.mode == "STREAMING") MaterialSymbolIcon("cloud", filled = true) else RhythmIcons.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (item.mode == "STREAMING")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (item.mode == "STREAMING") "Streaming" else "Local",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (item.mode == "STREAMING")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun WavyLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavyLoader")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 4) {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 300, delayMillis = i * 100, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .graphicsLayer { scaleY = scale }
                    .background(color, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalAllSongsPage(
    localSongs: List<Song>,
    streamingSongs: List<StreamingSong>,
    onBack: () -> Unit,
    onOptionsClick: (Any) -> Unit,
    onLocalSongClick: (Song) -> Unit,
    onStreamingSongClick: (StreamingSong) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val allSongs = buildList {
        localSongs.forEach { add(it to "LOCAL") }
        streamingSongs.forEach { add(it to "STREAMING") }
    }

    val miniPlayerBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val contentBottomPadding = (miniPlayerBottomPadding + 20.dp).coerceAtLeast(96.dp)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val horizontalPadding = if (isTablet) 32.dp else 24.dp

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_tab_songs),
        showBackButton = true,
        onBackClick = onBack,
        containerColor = MaterialTheme.colorScheme.background
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 12.dp,
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "header_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
            itemsIndexed(
                items = allSongs,
                key = { index, _ -> "song_$index" }
            ) { index, item ->
                val isLocal = item.second == "LOCAL"
                val title = if (isLocal) (item.first as Song).title else (item.first as StreamingSong).title
                val artist = if (isLocal) (item.first as Song).artist else (item.first as StreamingSong).artist
                val album = if (isLocal) (item.first as Song).album else (item.first as StreamingSong).album
                val artworkUri = if (isLocal) (item.first as Song).artworkUri else (item.first as StreamingSong).artworkUri

                UniversalSearchSongItem(
                    title = title,
                    subtitle = "$artist • $album",
                    artworkUri = artworkUri,
                    mode = item.second,
                    onClick = {
                        if (isLocal) onLocalSongClick(item.first as Song)
                        else onStreamingSongClick(item.first as StreamingSong)
                    },
                    onMoreClick = {
                        onOptionsClick(item.first)
                    },
                    haptics = haptics,
                    index = index,
                    totalCount = allSongs.size
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSongOptionsBottomSheet(
    songObj: Any,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
    onShowSongInfo: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onAddToBlacklist: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    var showContent by remember { mutableStateOf(true) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                UniversalSongOptionsHeader(songObj = songObj)
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val resolvedSong = remember(songObj) {
                        if (songObj is Song) songObj else (songObj as StreamingSong).toLocalSong()
                    }
                    val onShare = {
                        try {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, resolvedSong.uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${resolvedSong.title}"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    val isLocal = songObj is Song
                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
                    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
                    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
                    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
                    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer
                    val errorContainer = MaterialTheme.colorScheme.errorContainer
                    val errorColor = MaterialTheme.colorScheme.error

                    val gridItems = remember(isLocal, isFavorite) {
                        buildList {
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.SkipNext,
                                    text = context.getString(R.string.action_play_next),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = onPlayNext
                                )
                            )
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.Queue,
                                    text = context.getString(R.string.action_add_to_queue),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = onAddToQueue
                                )
                            )
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.AddToPlaylist,
                                    text = context.getString(R.string.content_desc_add_to_playlist),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = onAddToPlaylist
                                )
                            )
                            add(
                                UniversalOptionItem(
                                    icon = if (isFavorite) RhythmIcons.FavoriteFilled else RhythmIcons.Favorite,
                                    text = if (isFavorite) context.getString(R.string.action_remove_from_favorites) else context.getString(R.string.action_add_to_favorites),
                                    containerColor = tertiaryContainer,
                                    iconColor = onTertiaryContainer,
                                    onClick = onToggleFavorite
                                )
                            )
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.Album,
                                    text = context.getString(R.string.multiselectionbottomsheet_go_to_album),
                                    containerColor = secondaryContainer,
                                    iconColor = onSecondaryContainer,
                                    onClick = onGoToAlbum
                                )
                            )
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.Artist,
                                    text = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                                    containerColor = secondaryContainer,
                                    iconColor = onSecondaryContainer,
                                    onClick = onGoToArtist
                                )
                            )
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.Info,
                                    text = context.getString(R.string.action_song_info),
                                    containerColor = secondaryContainer,
                                    iconColor = onSecondaryContainer,
                                    onClick = onShowSongInfo
                                )
                            )
                            if (isLocal) {
                                add(
                                    UniversalOptionItem(
                                        icon = RhythmIcons.Block,
                                        text = context.getString(R.string.action_add_to_blacklist),
                                        containerColor = errorContainer,
                                        iconColor = errorColor,
                                        onClick = onAddToBlacklist
                                    )
                                )
                            }
                            add(
                                UniversalOptionItem(
                                    icon = RhythmIcons.Share,
                                    text = context.getString(R.string.action_share),
                                    containerColor = secondaryContainer,
                                    iconColor = onSecondaryContainer,
                                    onClick = onShare
                                )
                            )
                        }
                    }

                    val chunks = remember(gridItems) { gridItems.chunked(2) }

                    chunks.forEach { chunk ->
                        if (chunk.size == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    UniversalSongOptionGridItem(
                                        icon = chunk[0].icon,
                                        text = chunk[0].text,
                                        containerColor = chunk[0].containerColor,
                                        iconColor = chunk[0].iconColor,
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            chunk[0].onClick()
                                        }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    UniversalSongOptionGridItem(
                                        icon = chunk[1].icon,
                                        text = chunk[1].text,
                                        containerColor = chunk[1].containerColor,
                                        iconColor = chunk[1].iconColor,
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            chunk[1].onClick()
                                        }
                                    )
                                }
                            }
                        } else {
                            UniversalSongOptionGridItem(
                                icon = chunk[0].icon,
                                text = chunk[0].text,
                                containerColor = chunk[0].containerColor,
                                iconColor = chunk[0].iconColor,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    chunk[0].onClick()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

private data class UniversalOptionItem(
    val icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    val text: String,
    val containerColor: Color,
    val iconColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun UniversalSongOptionsHeader(
    songObj: Any,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLocal = songObj is Song
    val title = if (isLocal) songObj.title else (songObj as StreamingSong).title
    val artist = if (isLocal) songObj.artist else (songObj as StreamingSong).artist
    val album = if (isLocal) songObj.album else (songObj as StreamingSong).album
    val artworkUri = if (isLocal) songObj.artworkUri else (songObj as StreamingSong).artworkUri

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.playlistsongoptionsbottomsheet_song_options),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 0.dp
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .apply(ImageUtils.buildImageRequest(
                                artworkUri,
                                title,
                                context.cacheDir,
                                M3PlaceholderType.TRACK
                            ))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isLocal) "Local Song" else "Streaming Song",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "$artist • $album",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun UniversalSongOptionGridItem(
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    text: String,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.3f),
                tonalElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    containerColor.copy(alpha = 0.15f),
                                    containerColor.copy(alpha = 0.05f)
                                ),
                                radius = 22f
                            )
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun LazyListScope.universalGenreBrowseItems(
    genreSummaries: List<GenreUtils.Summary>,
    columnsCount: Int,
    isGenreDetectionComplete: Boolean,
    onGenreClick: (String) -> Unit
) {
    val isActuallyLoading = !isGenreDetectionComplete && genreSummaries.isEmpty()

    item(key = "genre_header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 12.dp, bottom = 6.dp)
        ) {
            Icon(
                imageVector = RhythmIcons.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(R.string.search_browse_by_genre),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }

    if (isActuallyLoading) {
        item(key = "genre_loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                WavyLoader(color = MaterialTheme.colorScheme.tertiary)
            }
        }
    } else if (genreSummaries.isNotEmpty()) {
        val rows = genreSummaries.chunked(columnsCount)
        items(
            count = rows.size,
            key = { rowIndex -> "genre_row_${rows[rowIndex].first().name.lowercase()}" }
        ) { rowIndex ->
            val rowGenres = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowGenres.forEachIndexed { colIndex, summary ->
                    val itemIndex = rowIndex * columnsCount + colIndex
                    UniversalGenreBrowseItemCard(
                        genre = summary.name,
                        songCount = summary.songCount,
                        index = itemIndex,
                        onClick = { onGenreClick(summary.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowGenres.size < columnsCount) {
                    repeat(columnsCount - rowGenres.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversalGenreBrowseItemCard(
    genre: String,
    songCount: Int,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember(index) {
        when (index % 4) {
            0 -> RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 12.dp)
            1 -> RoundedCornerShape(topStart = 12.dp, topEnd = 32.dp, bottomEnd = 12.dp, bottomStart = 32.dp)
            2 -> RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
            else -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 32.dp)
        }
    }

    val colorPair = when (index % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colorPair.first),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.height(115.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = universalGenreIconFor(genre),
                contentDescription = null,
                tint = colorPair.second.copy(alpha = 0.06f),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
                    .graphicsLayer { rotationZ = -18f }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = colorPair.second,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount songs",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorPair.second.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun universalGenreIconFor(genre: String): chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon {
    val normalized = genre.lowercase()
    return when {
        normalized.contains("hip hop") || normalized.contains("hip-hop") || normalized.contains("rap") || normalized.contains("trap") -> MaterialSymbolIcon("mic")
        normalized.contains("rock") || normalized.contains("metal") || normalized.contains("punk") || normalized.contains("grunge") -> RhythmIcons.Music.Audiotrack
        normalized.contains("electronic") || normalized.contains("edm") || normalized.contains("house") || normalized.contains("techno") || normalized.contains("trance") || normalized.contains("synth") -> RhythmIcons.Player.Equalizer
        normalized.contains("classical") || normalized.contains("instrumental") || normalized.contains("orchestra") || normalized.contains("opera") -> RhythmIcons.Music.Album
        normalized.contains("jazz") || normalized.contains("blues") || normalized.contains("soul") || normalized.contains("r&b") || normalized.contains("funk") -> RhythmIcons.Music.MusicNote
        normalized.contains("ambient") || normalized.contains("chill") || normalized.contains("lofi") || normalized.contains("lo-fi") || normalized.contains("acoustic") -> RhythmIcons.Devices.Headphones
        normalized.contains("pop") || normalized.contains("dance") || normalized.contains("disco") || normalized.contains("k-pop") || normalized.contains("j-pop") -> RhythmIcons.Music.MusicNote
        normalized.contains("country") || normalized.contains("folk") -> RhythmIcons.Music.Audiotrack
        else -> RhythmIcons.Music.MusicNote
    }
}
