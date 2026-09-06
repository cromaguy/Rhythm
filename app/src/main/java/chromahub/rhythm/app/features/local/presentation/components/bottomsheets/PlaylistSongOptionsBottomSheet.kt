/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon

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
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import androidx.compose.ui.layout.ContentScale

private data class OptionItem(
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
fun PlaylistSongOptionsBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowSongInfo: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onShare: () -> Unit,
    onDeleteSong: () -> Unit,
    showRemoveFromPlaylist: Boolean = true,
    showAddToPlaylist: Boolean = true,
    showGoToAlbum: Boolean = true,
    isStreamingMode: Boolean = false,
    haptics: HapticFeedback
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

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
            Column(
                modifier = Modifier
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
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 0.dp
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
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(
                                    if (isStreamingMode) R.string.playlistsongoptions_streaming_song else R.string.playlistsongoptions_local_song
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

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
                    val errorContainer = MaterialTheme.colorScheme.errorContainer
                    val errorColor = MaterialTheme.colorScheme.error

                    val gridItems = remember(showGoToAlbum, showRemoveFromPlaylist, showAddToPlaylist, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer, errorContainer, errorColor) {
                        buildList {
                            add(
                                OptionItem(
                                    icon = RhythmIcons.SkipNext,
                                    text = context.getString(R.string.action_play_next),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = onPlayNext
                                )
                            )
                            add(
                                OptionItem(
                                    icon = RhythmIcons.Queue,
                                    text = context.getString(R.string.action_add_to_queue),
                                    containerColor = primaryContainer,
                                    iconColor = onPrimaryContainer,
                                    onClick = onAddToQueue
                                )
                            )
                            if (showAddToPlaylist) {
                                add(
                                    OptionItem(
                                        icon = RhythmIcons.AddToPlaylist,
                                        text = context.getString(R.string.content_desc_add_to_playlist),
                                        containerColor = primaryContainer,
                                        iconColor = onPrimaryContainer,
                                        onClick = onAddToPlaylist
                                    )
                                )
                            }
                            if (showGoToAlbum) {
                                add(
                                    OptionItem(
                                        icon = RhythmIcons.Album,
                                        text = context.getString(R.string.multiselectionbottomsheet_go_to_album),
                                        containerColor = secondaryContainer,
                                        iconColor = onSecondaryContainer,
                                        onClick = onGoToAlbum
                                    )
                                )
                            }
                            add(
                                OptionItem(
                                    icon = RhythmIcons.Artist,
                                    text = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                                    containerColor = secondaryContainer,
                                    iconColor = onSecondaryContainer,
                                    onClick = onGoToArtist
                                )
                            )
                            add(
                                OptionItem(
                                    icon = RhythmIcons.Info,
                                    text = context.getString(R.string.action_song_info),
                                    containerColor = secondaryContainer,
                                    iconColor = onSecondaryContainer,
                                    onClick = onShowSongInfo
                                )
                            )
                            if (!isStreamingMode) {
                                add(
                                    OptionItem(
                                        icon = RhythmIcons.Share,
                                        text = context.getString(R.string.action_share),
                                        containerColor = secondaryContainer,
                                        iconColor = onSecondaryContainer,
                                        onClick = onShare
                                    )
                                )
                            }
                            if (showRemoveFromPlaylist) {
                                add(
                                    OptionItem(
                                        icon = RhythmIcons.Remove,
                                        text = context.getString(R.string.cd_remove_from_playlist),
                                        containerColor = errorContainer,
                                        iconColor = errorColor,
                                        onClick = onRemoveFromPlaylist
                                    )
                                )
                            }
                            if (!isStreamingMode) {
                                add(
                                    OptionItem(
                                        icon = RhythmIcons.Delete,
                                        text = context.getString(R.string.action_delete_song),
                                        containerColor = errorContainer,
                                        iconColor = errorColor,
                                        onClick = onDeleteSong
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
