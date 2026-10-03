/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.MarqueeText
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.ImageUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest

private data class SongOverflowItem(
    val title: String,
    val icon: MaterialSymbolIcon,
    val iconColor: Color = Color.Unspecified,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOverflowBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onEditSong: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onShowSongInfo: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToBlacklist: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isDownloaded: Boolean? = null,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
    isStreaming: Boolean = false,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(sheetState) {
        sheetState.expand()
    }

    val isStreamingSong = isStreaming ||
        song.uri.toString().startsWith("http://") ||
        song.uri.toString().startsWith("https://") ||
        song.uri.toString().startsWith("streaming://") ||
        song.id.startsWith("streaming_") ||
        song.id.startsWith("piped_") ||
        song.id.startsWith("ytm_") ||
        song.id.startsWith("saavn_")

    val canEditSong = onEditSong != null && !isStreamingSong

    val artworkShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.SONG_ART,
        fallbackShape = RoundedCornerShape(12.dp)
    )
    val playButtonShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.PLAYER_CONTROLS,
        fallbackShape = rememberExpressiveShape("SUNNY")
    )

    val finalOnShare = onShare ?: {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, song.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, Toast.LENGTH_SHORT).show()
        }
    }

    val primaryIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryIconColor = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiaryIconColor = MaterialTheme.colorScheme.onTertiaryContainer
    val errorIconColor = MaterialTheme.colorScheme.error

    val menuItems = remember(
        song, isFavorite, isDownloaded, isDownloading,
        onPlayNext, onAddToQueue, onToggleFavorite, onAddToPlaylist,
        onGoToAlbum, onGoToArtist, onShowSongInfo, onToggleDownload,
        onRemoveFromPlaylist, onAddToBlacklist, onDeleteSong, finalOnShare
    ) {
        buildList {
            onPlayNext?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.action_play_next),
                        icon = MaterialSymbolIcon("queue_play_next"),
                        iconColor = primaryIconColor,
                        onClick = action
                    )
                )
            }
            onAddToQueue?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.action_add_to_queue),
                        icon = RhythmIcons.AddToQueue,
                        iconColor = primaryIconColor,
                        onClick = action
                    )
                )
            }
            onAddToPlaylist?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.library_action_add_to_playlist),
                        icon = MaterialSymbolIcon("playlist_add"),
                        iconColor = primaryIconColor,
                        onClick = action
                    )
                )
            }
            onGoToAlbum?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.multiselectionbottomsheet_go_to_album),
                        icon = MaterialSymbolIcon("album"),
                        iconColor = secondaryIconColor,
                        onClick = action
                    )
                )
            }
            onGoToArtist?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                        icon = RhythmIcons.Artist,
                        iconColor = secondaryIconColor,
                        onClick = action
                    )
                )
            }
            onToggleFavorite?.let { action ->
                val fav = isFavorite == true
                add(
                    SongOverflowItem(
                        title = if (fav) context.getString(R.string.action_dislike) else context.getString(R.string.action_like),
                        icon = if (fav) MaterialSymbolIcon("thumb_up", filled = true) else MaterialSymbolIcon("thumb_up"),
                        iconColor = tertiaryIconColor,
                        onClick = action
                    )
                )
            }
            onShowSongInfo?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.action_song_info),
                        icon = RhythmIcons.Info,
                        iconColor = secondaryIconColor,
                        onClick = action
                    )
                )
            }
            onToggleDownload?.let { action ->
                val downloaded = isDownloaded == true
                add(
                    SongOverflowItem(
                        title = when {
                            isDownloading -> context.getString(R.string.streaming_downloading)
                            downloaded -> context.getString(R.string.streaming_remove_download)
                            else -> context.getString(R.string.streaming_download)
                        },
                        icon = when {
                            isDownloading -> MaterialSymbolIcon("sync")
                            downloaded -> MaterialSymbolIcon("download_done", filled = true)
                            else -> MaterialSymbolIcon("download")
                        },
                        iconColor = secondaryIconColor,
                        onClick = action
                    )
                )
            }
            onRemoveFromPlaylist?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.cd_remove_from_playlist),
                        icon = MaterialSymbolIcon("playlist_remove"),
                        iconColor = errorIconColor,
                        onClick = action
                    )
                )
            }
            onAddToBlacklist?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.action_add_to_blacklist),
                        icon = RhythmIcons.Block,
                        iconColor = errorIconColor,
                        onClick = action
                    )
                )
            }
            onDeleteSong?.let { action ->
                add(
                    SongOverflowItem(
                        title = context.getString(R.string.action_delete_song),
                        icon = MaterialSymbolIcon("delete"),
                        iconColor = errorIconColor,
                        onClick = action
                    )
                )
            }
            add(
                SongOverflowItem(
                    title = context.getString(R.string.action_share),
                    icon = MaterialSymbolIcon("share"),
                    iconColor = secondaryIconColor,
                    onClick = finalOnShare
                )
            )
        }
    }

    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        scrollState = scrollState,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            onPlay()
                            onDismiss()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = artworkShape,
                        tonalElevation = 0.dp
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .apply(
                                    ImageUtils.buildImageRequest(
                                        song.artworkUri,
                                        song.title,
                                        context.cacheDir,
                                        M3PlaceholderType.TRACK
                                    )
                                )
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(
                                if (isStreamingSong) R.string.playlistsongoptions_streaming_song else R.string.playlistsongoptions_local_song
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        MarqueeText(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        )

                        MarqueeText(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (canEditSong) {
                    RhythmGroupedButton(
                        size = RhythmButtonSize.Small,
                        isFillMaxWidth = false
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onPlay()
                                onDismiss()
                            },
                            useWeight = false,
                            size = RhythmButtonSize.Small,
                            height = 44.dp,
                            iconSize = 22.dp,
                            isFirst = true,
                            isLast = false,
                            icon = RhythmIcons.Play,
                            contentDescription = stringResource(R.string.action_play)
                        )

                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                onEditSong.invoke()
                                onDismiss()
                            },
                            useWeight = false,
                            size = RhythmButtonSize.Small,
                            height = 44.dp,
                            iconSize = 22.dp,
                            isFirst = false,
                            isLast = true,
                            icon = RhythmIcons.Edit,
                            contentDescription = stringResource(R.string.bottomsheet_timer_edit)
                        )
                    }
                } else {
                    Surface(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            onPlay()
                            onDismiss()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = playButtonShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 0.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Play,
                                contentDescription = stringResource(R.string.action_play),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            AdaptiveSheetScrollContainer(
                scrollState = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    menuItems.forEachIndexed { index, item ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.98f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "overflow_item_scale"
                        )

                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                item.onClick()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            shape = groupedBottomSheetItemShape(index, menuItems.size),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            interactionSource = interactionSource
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (item.iconColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else item.iconColor,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                MarqueeText(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    gradientEdgeColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
