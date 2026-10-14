/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song

@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipPrevious: () -> Unit = {},
    onDismiss: () -> Unit = {},
    isMediaLoading: Boolean = false,
    verticalDragEnabled: Boolean = true
) {
    MaterialMiniPlayer(
        song = song, isPlaying = isPlaying, progress = progress,
        onPlayPause = onPlayPause, onPlayerClick = onPlayerClick,
        onSkipNext = onSkipNext, onSkipPrevious = onSkipPrevious,
        onDismiss = onDismiss, isMediaLoading = isMediaLoading,
        verticalDragEnabled = verticalDragEnabled, modifier = modifier
    )
}
