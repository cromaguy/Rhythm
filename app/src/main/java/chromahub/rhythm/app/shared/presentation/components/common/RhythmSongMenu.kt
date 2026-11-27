/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LocalSongShareHandler
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongShareRequest
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.shareSongAudioFile
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.R

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
    val songShareHandler = LocalSongShareHandler.current
    val finalOnShare = onShare ?: song?.let { s ->
        {
            val request = SongShareRequest(song = s)
            if (songShareHandler != null) {
                songShareHandler(request)
            } else {
                shareSongAudioFile(context, s)
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
                    icon = if (fav) MaterialSymbolIcon("thumb_up", filled = true) else MaterialSymbolIcon("thumb_up", filled = false),
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = action
                )
            )
        }
        onToggleLike?.let { action ->
            val liked = isLiked == true
            add(
                SongMenuItem(
                    title = if (liked) "Unlike" else "Like",
                    icon = if (liked) MaterialSymbolIcon("thumb_up", filled = true) else MaterialSymbolIcon("thumb_up", filled = false),
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
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

    RhythmGroupedMenuContent(
        modifier = modifier,
        items = menuItems.map { item ->
            RhythmMenuItem(
                title = item.title,
                icon = item.icon,
                iconContainerColor = item.iconBgColor,
                iconTint = item.iconTint,
                isDestructive = item.icon == RhythmIcons.Block || item.icon == RhythmIcons.Delete,
                onClick = item.onClick
            )
        }
    )
}
