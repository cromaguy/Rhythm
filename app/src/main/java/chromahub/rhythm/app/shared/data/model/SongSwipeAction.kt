/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

enum class SongSwipeAction(val key: String) {
    ADD_TO_QUEUE("ADD_TO_QUEUE"),
    PLAY_NEXT("PLAY_NEXT"),
    TOGGLE_FAVORITE("TOGGLE_FAVORITE"),
    ADD_TO_PLAYLIST("ADD_TO_PLAYLIST"),
    SHOW_SONG_INFO("SHOW_SONG_INFO"),
    SHARE("SHARE");

    companion object {
        fun fromKey(key: String?): SongSwipeAction =
            entries.firstOrNull { it.key == key } ?: ADD_TO_QUEUE
    }
}
