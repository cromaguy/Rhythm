/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import chromahub.rhythm.app.shared.data.model.Song

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
    haptics: HapticFeedback? = null,
    onPlay: (() -> Unit)? = null
) {
    SongOverflowBottomSheet(
        song = song,
        onDismiss = onDismiss,
        onPlay = onPlay ?: {},
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onAddToPlaylist = if (showAddToPlaylist) onAddToPlaylist else null,
        onGoToAlbum = if (showGoToAlbum) onGoToAlbum else null,
        onGoToArtist = onGoToArtist,
        onShowSongInfo = onShowSongInfo,
        onRemoveFromPlaylist = if (showRemoveFromPlaylist) onRemoveFromPlaylist else null,
        onDeleteSong = onDeleteSong,
        onShare = onShare,
        isStreaming = isStreamingMode
    )
}
