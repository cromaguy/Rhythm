/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import android.content.Context
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.SongSwipeAction
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons

fun SongSwipeAction.icon(): MaterialSymbolIcon = when (this) {
    SongSwipeAction.ADD_TO_QUEUE -> RhythmIcons.Queue
    SongSwipeAction.PLAY_NEXT -> RhythmIcons.SkipNext
    SongSwipeAction.TOGGLE_FAVORITE -> MaterialSymbolIcon("thumb_up")
    SongSwipeAction.ADD_TO_PLAYLIST -> RhythmIcons.AddToPlaylist
    SongSwipeAction.SHOW_SONG_INFO -> RhythmIcons.Info
    SongSwipeAction.SHARE -> RhythmIcons.Share
}

fun SongSwipeAction.label(context: Context): String = context.getString(
    when (this) {
        SongSwipeAction.ADD_TO_QUEUE -> R.string.action_add_to_queue
        SongSwipeAction.PLAY_NEXT -> R.string.action_play_next
        SongSwipeAction.TOGGLE_FAVORITE -> R.string.action_like
        SongSwipeAction.ADD_TO_PLAYLIST -> R.string.library_action_add_to_playlist
        SongSwipeAction.SHOW_SONG_INFO -> R.string.action_song_info
        SongSwipeAction.SHARE -> R.string.action_share
    }
)
