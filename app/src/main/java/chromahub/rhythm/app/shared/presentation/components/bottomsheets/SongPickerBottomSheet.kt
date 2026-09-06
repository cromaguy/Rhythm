/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsSearchBar
import chromahub.rhythm.app.R
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

/**
 * Modal bottom sheet that provides song picking UI (search + multi-select + add)
 * Replicates AddToPlaylistScreen features but presented as a bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerBottomSheet(
    targetPlaylist: Playlist,
    availableSongs: List<Song>,
    onDismissRequest: () -> Unit,
    onAddSongsToPlaylist: (List<Song>) -> Unit,
    sheetState: androidx.compose.material3.SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSongs = remember(availableSongs, searchQuery) {
        if (searchQuery.isBlank()) availableSongs
        else availableSongs.filter { song ->
            song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.album.contains(searchQuery, ignoreCase = true)
        }
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        lazyListState = listState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.add_to_playlist_named, targetPlaylist.name),
            subtitle = if (isSelectionMode) "${selectedSongs.size} selected" else "${filteredSongs.size} songs",
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            // Search + actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    hint = "Pick a tune..."
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    AnimatedVisibility(visible = isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    isSelectionMode = false
                                    selectedSongs = emptySet()
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Close,
                                    contentDescription = stringResource(R.string.addtoplaylistscreen_exit_selection),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (selectedSongs.isNotEmpty()) {
                                FilledTonalIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        if (selectedSongs.size == filteredSongs.size) selectedSongs = emptySet()
                                        else selectedSongs = filteredSongs.map { it.id }.toSet()
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (selectedSongs.size == filteredSongs.size) MaterialSymbolIcon("deselect", filled = true) else RhythmIcons.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                FilledIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        val songsToAdd = filteredSongs.filter { selectedSongs.contains(it.id) }
                                        onAddSongsToPlaylist(songsToAdd)
                                        isSelectionMode = false
                                        selectedSongs = emptySet()
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(imageVector = RhythmIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = !isSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedSongs = emptySet()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isSelectionMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isSelectionMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(imageVector = MaterialSymbolIcon("checklist", filled = true), contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isSelectionMode && selectedSongs.isEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.addtoplaylistscreen_tap_songs_to_select),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Body: song list
            AdaptiveSheetScrollContainer(
                lazyListState = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { endPadding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp + endPadding, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            EmptySongsState(hasSearch = searchQuery.isNotEmpty(), modifier = Modifier.padding(vertical = 48.dp))
                        }
                    } else {
                        itemsIndexed(items = filteredSongs, key = { index, song -> "addsong_${song.id}_$index" }) { index, song ->
                            SongSelectionItem(
                                song = song,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedSongs.contains(song.id),
                                index = index,
                                totalCount = filteredSongs.size,
                                onSongClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    if (isSelectionMode) {
                                        selectedSongs = if (selectedSongs.contains(song.id)) selectedSongs - song.id else selectedSongs + song.id
                                    } else {
                                        onAddSongsToPlaylist(listOf(song))
                                    }
                                },
                                onLongClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedSongs = setOf(song.id)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongSelectionItem(
    song: Song,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    index: Int,
    totalCount: Int,
    onSongClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "itemScale"
    )

    Surface(
        onClick = onSongClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(onClick = onSongClick, onLongClick = onLongClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        shape = groupedSongItemShape(index, totalCount),
        tonalElevation = if (isSelected) 2.dp else 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(targetState = isSelectionMode, transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() }, label = "selectionToggle") { inSelectionMode ->
                if (inSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.size(48.dp))
                } else {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).apply(ImageUtils.buildImageRequest(song.artworkUri, song.title, LocalContext.current.cacheDir, M3PlaceholderType.TRACK)).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "${song.artist} • ${song.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 0.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = RhythmIcons.Add, contentDescription = stringResource(R.string.cd_add_song), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun groupedSongItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun EmptySongsState(hasSearch: Boolean, modifier: Modifier = Modifier) {
    ExpressiveCookieEmptyState(
        modifier = modifier.fillMaxWidth(),
        title = if (hasSearch) stringResource(R.string.nav_no_matching_songs) else stringResource(R.string.playlist_no_songs_available),
        subtitle = if (hasSearch) stringResource(R.string.nav_try_different) else stringResource(R.string.playlist_all_in_playlist),
        mainIcon = if (hasSearch) RhythmIcons.Search else RhythmIcons.MusicNote,
        accentIcon = if (hasSearch) RhythmIcons.Search else RhythmIcons.MusicNote,
        cornerIcon = RhythmIcons.MusicNote,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
