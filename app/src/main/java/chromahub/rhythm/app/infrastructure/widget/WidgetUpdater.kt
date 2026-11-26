/*
 * Copyright (C) 2025 nift4 (Gramophone)
 * Modified for Rhythm by Anjishnu Nandi (cromaguy)
 *
 * SPDX-FileCopyrightText: 2025 nift4 <https://github.com/FoedusProgramme/Gramophone>
 * SPDX-FileCopyrightText: 2025-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.widget

import android.content.Context
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater

/**
 * Thin shim that forwards widget update calls to the Glance-based updater.
 * The legacy RemoteViews widget (MusicWidgetProvider) has been removed.
 */
object WidgetUpdater {

    fun updateWidget(
        context: Context,
        song: Song?,
        isPlaying: Boolean,
        hasPrevious: Boolean = false,
        hasNext: Boolean = false,
        isFavorite: Boolean = false,
        isShuffleEnabled: Boolean = false,
        repeatMode: Int = 0,
        upNextItems: List<String> = emptyList()
    ) {
        GlanceWidgetUpdater.updateWidget(
            context = context,
            song = song,
            isPlaying = isPlaying,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            isFavorite = isFavorite,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            upNextItems = upNextItems
        )
    }

    fun clearWidget(context: Context) {
        GlanceWidgetUpdater.updateWidgetEmpty(context)
    }
}
