/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.R
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils

private data class SongMenuItem(
    val title: String,
    val icon: Any,
    val iconBgColor: Color,
    val iconTint: Color,
    val onClick: () -> Unit
)

@Composable
fun RhythmSongMenuContent(
    modifier: Modifier = Modifier,
    song: Song? = null,
    onPlay: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isLiked: Boolean? = null,
    onToggleLike: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onShowSongInfo: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onAddToBlacklist: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isDownloaded: Boolean? = null,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val finalOnShare = onShare ?: song?.let { s ->
        {
            try {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, s.uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${s.title}"))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val menuItems = buildList {
        onPlay?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_play),
                    icon = RhythmIcons.Play,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = action
                )
            )
        }
        onPlayNext?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_play_next),
                    icon = RhythmIcons.SkipNext,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = action
                )
            )
        }
        onAddToQueue?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_add_to_queue),
                    icon = RhythmIcons.Queue,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = action
                )
            )
        }
        onToggleFavorite?.let { action ->
            val fav = isFavorite == true
            add(
                SongMenuItem(
                    title = if (fav) context.getString(R.string.action_dislike) else context.getString(R.string.action_like),
                    icon = if (fav) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true),
                    iconBgColor = if (fav) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    iconTint = if (fav) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = action
                )
            )
        }
        onToggleLike?.let { action ->
            val liked = isLiked == true
            add(
                SongMenuItem(
                    title = if (liked) "Unlike" else "Like",
                    icon = if (liked) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true),
                    iconBgColor = if (liked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    iconTint = if (liked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = action
                )
            )
        }
        onAddToPlaylist?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.library_action_add_to_playlist),
                    icon = RhythmIcons.AddToPlaylist,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = action
                )
            )
        }
        onToggleDownload?.let { action ->
            val downloaded = isDownloaded == true
            add(
                SongMenuItem(
                    title = when {
                        isDownloading -> stringResource(R.string.streaming_downloading)
                        downloaded -> stringResource(R.string.streaming_remove_download)
                        else -> stringResource(R.string.streaming_download)
                    },
                    icon = when {
                        isDownloading -> MaterialSymbolIcon("sync")
                        downloaded -> MaterialSymbolIcon("download_done", filled = true)
                        else -> MaterialSymbolIcon("download")
                    },
                    iconBgColor = if (downloaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    iconTint = if (downloaded) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = action
                )
            )
        }
        finalOnShare?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_share),
                    icon = RhythmIcons.Share,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = action
                )
            )
        }
        onShowSongInfo?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_song_info),
                    icon = RhythmIcons.Info,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = action
                )
            )
        }
        onGoToAlbum?.let { action ->
            add(
                SongMenuItem(
                    title = stringResource(R.string.multiselectionbottomsheet_go_to_album),
                    icon = RhythmIcons.AlbumFilled,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = action
                )
            )
        }
        onGoToArtist?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                    icon = RhythmIcons.ArtistFilled,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = action
                )
            )
        }
        onAddToBlacklist?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_add_to_blacklist),
                    icon = RhythmIcons.Block,
                    iconBgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = action
                )
            )
        }
        onDeleteSong?.let { action ->
            add(
                SongMenuItem(
                    title = context.getString(R.string.action_delete_song),
                    icon = RhythmIcons.Delete,
                    iconBgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = action
                )
            )
        }
    }

    if (menuItems.isNotEmpty()) {
        val outerRadius = 16.dp
        val innerRadius = 4.dp
        val itemSpacing = 3.dp

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            menuItems.forEachIndexed { index, item ->
                val itemShape = when {
                    menuItems.size == 1 -> RoundedCornerShape(outerRadius)
                    index == 0 -> RoundedCornerShape(
                        topStart = outerRadius, topEnd = outerRadius,
                        bottomStart = innerRadius, bottomEnd = innerRadius
                    )
                    index == menuItems.size - 1 -> RoundedCornerShape(
                        topStart = innerRadius, topEnd = innerRadius,
                        bottomStart = outerRadius, bottomEnd = outerRadius
                    )
                    else -> RoundedCornerShape(innerRadius)
                }

                Surface(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                        item.onClick()
                    },
                    shape = itemShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = item.iconBgColor
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val icon = item.icon
                                val iconSize = 16.dp
                                when (icon) {
                                    is MaterialSymbolIcon -> {
                                        chromahub.rhythm.app.shared.presentation.components.icons.Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = item.iconTint,
                                            modifier = Modifier.size(iconSize)
                                        )
                                    }
                                    is ImageVector -> {
                                        chromahub.rhythm.app.shared.presentation.components.icons.Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = item.iconTint,
                                            modifier = Modifier.size(iconSize)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (item.icon == RhythmIcons.Block || item.icon == RhythmIcons.Delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
