/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

private data class MultiOptionItem(
    val icon: MaterialSymbolIcon,
    val text: String,
    val containerColor: Color,
    val iconColor: Color,
    val onClick: () -> Unit
)

private fun getGridItemShape(index: Int, totalItems: Int): RoundedCornerShape {
    if (totalItems <= 1) return RoundedCornerShape(24.dp)
    if (totalItems == 2) {
        return if (index == 0) {
            RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
        } else {
            RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
        }
    }
    
    val totalRows = (totalItems + 1) / 2
    val r = index / 2
    val c = index % 2
    
    return when {
        r == 0 -> {
            if (c == 0) {
                RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            }
        }
        r == totalRows - 1 -> {
            if (index == totalItems - 1 && c == 0) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            } else if (c == 0) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            }
        }
        else -> RoundedCornerShape(8.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectionBottomSheet(
    selectedSongs: List<Song>,
    favoriteSongIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToPlaylist: () -> Unit,
    onToggleLikeAll: ((shouldLike: Boolean) -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onAddToBlacklist: (() -> Unit)? = null,
    onBatchEditTags: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val haptics = LocalHapticFeedback.current
    
    val allAreLiked by remember(selectedSongs, favoriteSongIds) {
        derivedStateOf {
            selectedSongs.isNotEmpty() && selectedSongs.all { favoriteSongIds.contains(it.id) }
        }
    }
    
    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        scrollState = scrollState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header with selection info
            MultiSelectionHeader(selectedSongs = selectedSongs)
            
            // Actions section with grouped grid layout
            AdaptiveSheetScrollContainer(
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) { endPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 8.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
                    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
                    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
                    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
                    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer
                    val errorContainer = MaterialTheme.colorScheme.errorContainer
                    val errorColor = MaterialTheme.colorScheme.error

                    val gridItems = remember(
                        allAreLiked,
                        onPlayNext,
                        onToggleLikeAll,
                        onGoToAlbum,
                        onGoToArtist,
                        onAddToBlacklist,
                        onBatchEditTags,
                        primaryContainer,
                        onPrimaryContainer,
                        secondaryContainer,
                        onSecondaryContainer,
                        tertiaryContainer,
                        onTertiaryContainer,
                        errorContainer,
                        errorColor
                    ) {
                        buildList {
                            add(
                                MultiOptionItem(
                                    icon = RhythmIcons.Play,
                                    text = context.getString(R.string.action_play_all),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = { onPlayAll(); onDismiss() }
                                )
                            )
                            if (onPlayNext != null) {
                                add(
                                    MultiOptionItem(
                                        icon = RhythmIcons.SkipNext,
                                        text = context.getString(R.string.action_play_next),
                                        containerColor = primaryContainer,
                                        iconColor = onPrimaryContainer,
                                        onClick = { onPlayNext(); onDismiss() }
                                    )
                                )
                            }
                            add(
                                MultiOptionItem(
                                    icon = RhythmIcons.Queue,
                                    text = context.getString(R.string.action_add_to_queue),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = { onAddToQueue(); onDismiss() }
                                )
                            )
                            add(
                                MultiOptionItem(
                                    icon = RhythmIcons.AddToPlaylist,
                                    text = context.getString(R.string.content_desc_add_to_playlist),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = { onAddToPlaylist(); onDismiss() }
                                )
                            )
                            if (onToggleLikeAll != null) {
                                add(
                                    MultiOptionItem(
                                        icon = if (allAreLiked) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true),
                                        text = if (allAreLiked) context.getString(R.string.action_dislike) else context.getString(R.string.action_like),
                                        containerColor = tertiaryContainer,
                                        iconColor = onTertiaryContainer,
                                        onClick = { onToggleLikeAll(!allAreLiked); onDismiss() }
                                    )
                                )
                            }
                            if (onGoToAlbum != null) {
                                add(
                                    MultiOptionItem(
                                        icon = RhythmIcons.Album,
                                        text = context.getString(R.string.multiselectionbottomsheet_go_to_album),
                                        containerColor = secondaryContainer,
                                        iconColor = onSecondaryContainer,
                                        onClick = { onGoToAlbum(); onDismiss() }
                                    )
                                )
                            }
                            if (onGoToArtist != null) {
                                add(
                                    MultiOptionItem(
                                        icon = RhythmIcons.Artist,
                                        text = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                                        containerColor = secondaryContainer,
                                        iconColor = onSecondaryContainer,
                                        onClick = { onGoToArtist(); onDismiss() }
                                    )
                                )
                            }
                            if (onAddToBlacklist != null) {
                                add(
                                    MultiOptionItem(
                                        icon = RhythmIcons.Block,
                                        text = context.getString(R.string.action_add_to_blacklist),
                                        containerColor = errorContainer,
                                        iconColor = errorColor,
                                        onClick = { onAddToBlacklist(); onDismiss() }
                                    )
                                )
                            }
                            if (onBatchEditTags != null) {
                                add(
                                    MultiOptionItem(
                                        icon = RhythmIcons.Edit,
                                        text = context.getString(R.string.multiselectionbottomsheet_edit_tags),
                                        containerColor = secondaryContainer,
                                        iconColor = onSecondaryContainer,
                                        onClick = { onBatchEditTags() }
                                    )
                                )
                            }
                        }
                    }

                    val chunks = remember(gridItems) { gridItems.chunked(2) }

                    chunks.forEachIndexed { rowIndex, chunk ->
                        if (chunk.size == 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    val index0 = rowIndex * 2
                                    SongOptionGridItem(
                                        icon = chunk[0].icon,
                                        text = chunk[0].text,
                                        containerColor = chunk[0].containerColor,
                                        iconColor = chunk[0].iconColor,
                                        shape = getGridItemShape(index0, gridItems.size),
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            chunk[0].onClick()
                                        },
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    val index1 = rowIndex * 2 + 1
                                    SongOptionGridItem(
                                        icon = chunk[1].icon,
                                        text = chunk[1].text,
                                        containerColor = chunk[1].containerColor,
                                        iconColor = chunk[1].iconColor,
                                        shape = getGridItemShape(index1, gridItems.size),
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            chunk[1].onClick()
                                        },
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                        } else {
                            val index0 = rowIndex * 2
                            SongOptionGridItem(
                                icon = chunk[0].icon,
                                text = chunk[0].text,
                                containerColor = chunk[0].containerColor,
                                iconColor = chunk[0].iconColor,
                                shape = getGridItemShape(index0, gridItems.size),
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

/**
 * Header showing selection count and stacked album arts
 */
@Composable
private fun MultiSelectionHeader(
    selectedSongs: List<Song>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.multiselectionbottomsheet_multiselection),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(18.dp))
        
        // Selection info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stacked album arts
                val stackedImageSize = 56.dp
                val stackedOverlap = 28.dp
                val stackedCount = selectedSongs.take(4).size
                val stackedWidth = if (stackedCount > 0) {
                    (stackedImageSize - stackedOverlap) * (stackedCount - 1) + stackedImageSize
                } else stackedImageSize
                
                StackedAlbumArts(
                    songs = selectedSongs.take(4),
                    modifier = Modifier
                        .height(56.dp)
                        .width(stackedWidth)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Song count
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.ui_songs_count, selectedSongs.size, selectedSongs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = stringResource(R.string.streaming_selected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (selectedSongs.isNotEmpty()) {
                        Text(
                            text = "${selectedSongs.first().artist} • ${selectedSongs.first().album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid item matching SearchScreen style
 */
@Composable
private fun SongOptionGridItem(
    icon: MaterialSymbolIcon,
    text: String,
    containerColor: Color,
    iconColor: Color,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.25f),
                tonalElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Displays stacked album art images with overlap effect
 */
@Composable
private fun StackedAlbumArts(
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val imageSize = 56.dp
    val overlap = 28.dp
    val borderWidth = 3.dp
    val borderColor = MaterialTheme.colorScheme.surface
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        songs.forEachIndexed { index, song ->
            val offsetX = index * (imageSize.value - overlap.value)
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) }
                    .zIndex((songs.size - index).toFloat())
                    .size(imageSize)
                    .background(borderColor, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(borderWidth)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    M3ImageUtils.AlbumArt(
                        imageUrl = song.artworkUri,
                        albumName = song.album,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}
