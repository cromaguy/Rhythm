/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.widget.glance

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.withContext
import chromahub.rhythm.app.infrastructure.service.RhythmTileService
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat

/**
 * Utility object for updating the Glance-based widget
 * 
 * This handles updating widget state when playback changes
 */
object GlanceWidgetUpdater {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    /**
     * Update widget with current playback state
     */
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
        // Use the application context for every widget/state API so short-lived
        // contexts (e.g. the playback service) are never retained by Glance's
        // process-lifetime state cache.
        val appContext = context.applicationContext

        // Update dynamic launcher shortcuts
        updateAppShortcuts(appContext, isPlaying)

        // Update SharedPreferences shared with the Glance widgets
        val prefs = appContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            if (song != null) {
                putString(RhythmMusicWidget.KEY_SONG_ID, song.id)
                putString(RhythmMusicWidget.KEY_SONG_TITLE, song.title)
                putString(RhythmMusicWidget.KEY_ARTIST_NAME, song.artist)
                putString(RhythmMusicWidget.KEY_ALBUM_NAME, song.album)
                putString(RhythmMusicWidget.KEY_ARTWORK_URI, song.artworkUri?.toString())
            } else {
                putString(RhythmMusicWidget.KEY_SONG_ID, "")
                putString(RhythmMusicWidget.KEY_SONG_TITLE, "Rhythm")
                putString(RhythmMusicWidget.KEY_ARTIST_NAME, "")
                putString(RhythmMusicWidget.KEY_ALBUM_NAME, "")
                remove(RhythmMusicWidget.KEY_ARTWORK_URI)
            }
            putBoolean(RhythmMusicWidget.KEY_IS_PLAYING, isPlaying)
            putBoolean(RhythmMusicWidget.KEY_HAS_PREVIOUS, hasPrevious)
            putBoolean(RhythmMusicWidget.KEY_HAS_NEXT, hasNext)
            putBoolean(RhythmMusicWidget.KEY_IS_FAVORITE, isFavorite)
            putBoolean("is_shuffle", isShuffleEnabled)
            putInt("repeat_mode", repeatMode)
            putString(RhythmMusicWidget.KEY_UP_NEXT_QUEUE, RhythmMusicWidget.serializeUpNext(upNextItems))
        }
        
        // Update Glance widget state directly using Glance state system
        scope.launch {
            try {
                // Preload bitmap in background if artworkUri exists
                val artworkUri = song?.artworkUri?.toString()
                if (!artworkUri.isNullOrBlank()) {
                    try {
                        withContext(Dispatchers.IO) {
                            val imageLoader = coil.Coil.imageLoader(appContext)
                            val request = ImageRequest.Builder(appContext)
                                .data(artworkUri)
                                .size(Size(150, 150))
                                .build()
                            val result = imageLoader.execute(request)
                            val loaded = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (loaded != null) {
                                RhythmMusicWidget.cacheBitmap(artworkUri, loaded)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GlanceWidgetUpdater", "Error preloading bitmap in updater", e)
                    }
                }

                val manager = GlanceAppWidgetManager(appContext)
                
                // Helper to update state for any music widget
                val updatePrefsHelper = { mutablePrefs: androidx.datastore.preferences.core.MutablePreferences ->
                    if (song != null) {
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_SONG_ID)] = song.id
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_SONG_TITLE)] = song.title
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_ARTIST_NAME)] = song.artist
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_ALBUM_NAME)] = song.album
                        song.artworkUri?.let {
                            mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_ARTWORK_URI)] = it.toString()
                        }
                    } else {
                        mutablePrefs.remove(stringPreferencesKey(RhythmMusicWidget.KEY_SONG_ID))
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_SONG_TITLE)] = "Rhythm"
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_ARTIST_NAME)] = ""
                        mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_ALBUM_NAME)] = ""
                        mutablePrefs.remove(stringPreferencesKey(RhythmMusicWidget.KEY_ARTWORK_URI))
                    }
                    mutablePrefs[booleanPreferencesKey(RhythmMusicWidget.KEY_IS_PLAYING)] = isPlaying
                    mutablePrefs[booleanPreferencesKey(RhythmMusicWidget.KEY_HAS_PREVIOUS)] = hasPrevious
                    mutablePrefs[booleanPreferencesKey(RhythmMusicWidget.KEY_HAS_NEXT)] = hasNext
                    mutablePrefs[booleanPreferencesKey(RhythmMusicWidget.KEY_IS_FAVORITE)] = isFavorite
                    mutablePrefs[booleanPreferencesKey("is_shuffle")] = isShuffleEnabled
                    mutablePrefs[intPreferencesKey("repeat_mode")] = repeatMode
                    mutablePrefs[stringPreferencesKey(RhythmMusicWidget.KEY_UP_NEXT_QUEUE)] =
                        RhythmMusicWidget.serializeUpNext(upNextItems)
                }

                // 1. RhythmMusicWidget
                manager.getGlanceIds(RhythmMusicWidget::class.java).forEach { glanceId ->
                    updateAppWidgetState(appContext, glanceId) { prefs -> updatePrefsHelper(prefs) }
                }
                
                // 2. RhythmCookieWidget
                try {
                    manager.getGlanceIds(RhythmCookieWidget::class.java).forEach { glanceId ->
                        updateAppWidgetState(appContext, glanceId) { prefs -> updatePrefsHelper(prefs) }
                    }
                } catch (_: Exception) {}
                
                // Update RhythmLyricsWidget as well
                val lyricGlanceIds = manager.getGlanceIds(RhythmLyricsWidget::class.java)
                lyricGlanceIds.forEach { glanceId ->
                    updateAppWidgetState(appContext, glanceId) { prefs ->
                        if (song != null) {
                            prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_SONG_TITLE)] = song.title
                            prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_ARTIST_NAME)] = song.artist
                            song.artworkUri?.let {
                                prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_ARTWORK_URI)] = it.toString()
                            } ?: prefs.remove(stringPreferencesKey(RhythmLyricsWidget.KEY_ARTWORK_URI))
                        } else {
                            prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_SONG_TITLE)] = "Rhythm"
                            prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_ARTIST_NAME)] = ""
                            prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_LYRIC_LINES)] = ""
                            prefs[intPreferencesKey(RhythmLyricsWidget.KEY_ACTIVE_INDEX)] = -1
                            prefs.remove(stringPreferencesKey(RhythmLyricsWidget.KEY_ARTWORK_URI))
                        }
                        prefs[booleanPreferencesKey(RhythmLyricsWidget.KEY_IS_PLAYING)] = isPlaying
                    }
                }
                
                // Force update all widgets
                try { RhythmMusicWidget().updateAll(appContext) } catch (_: Exception) {}
                try { RhythmCookieWidget().updateAll(appContext) } catch (_: Exception) {}
                try { RhythmLyricsWidget().updateAll(appContext) } catch (_: Exception) {}
                try { RhythmStatsWidget().updateAll(appContext) } catch (_: Exception) {}

                // Update Quick Settings Tile
try {
    android.service.quicksettings.TileService.requestListeningState(
        appContext,
        android.content.ComponentName(appContext, RhythmTileService::class.java)
    )
} catch (e: Exception) {
    android.util.Log.e("GlanceWidgetUpdater", "Error updating tile listening state", e)
}
            } catch (e: Exception) {
                android.util.Log.e("GlanceWidgetUpdater", "Error updating widget", e)
            }
        }
    }
    
    /**
     * Update widget to show "No song playing" state
     */
    fun updateWidgetEmpty(context: Context) {
        updateWidget(
            context = context,
            song = null,
            isPlaying = false,
            hasPrevious = false,
            hasNext = false
        )
    }
    
    /**
     * Force update all widgets
     */
    fun forceUpdateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try { RhythmMusicWidget().updateAll(appContext) } catch (_: Exception) {}
            try { RhythmCookieWidget().updateAll(appContext) } catch (_: Exception) {}
            try { RhythmLyricsWidget().updateAll(appContext) } catch (_: Exception) {}
            try { RhythmStatsWidget().updateAll(appContext) } catch (_: Exception) {}
        }
        
        // Also trigger worker update
        scheduleWidgetUpdate(appContext, delayMillis = 0)
    }
    
    /**
     * Schedule a widget update using WorkManager for reliability
     */
    private fun scheduleWidgetUpdate(context: Context, delayMillis: Long = 0) {
        try {
            val updateRequest = OneTimeWorkRequestBuilder<RhythmWidgetWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            
            WorkManager.getInstance(context).enqueue(updateRequest)
        } catch (e: Exception) {
            android.util.Log.e("GlanceWidgetUpdater", "Error scheduling widget update", e)
        }
    }
    
    /**
     * Update lyrics widget with dynamic lyric lines and active index
     */
    fun updateLyrics(
        context: Context,
        lyricTexts: List<String>,
        activeIndex: Int
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(appContext)
                val glanceIds = manager.getGlanceIds(RhythmLyricsWidget::class.java)
                if (glanceIds.isEmpty()) return@launch
                
                val joined = lyricTexts.joinToString("##LINE##")
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(appContext, glanceId) { prefs ->
                        prefs[stringPreferencesKey(RhythmLyricsWidget.KEY_LYRIC_LINES)] = joined
                        prefs[intPreferencesKey(RhythmLyricsWidget.KEY_ACTIVE_INDEX)] = activeIndex
                    }
                }
                try { RhythmLyricsWidget().updateAll(appContext) } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e("GlanceWidgetUpdater", "Error updating lyrics widget", e)
            }
        }
    }

    /**
     * Update launcher app shortcuts dynamically
     */
    private fun updateAppShortcuts(context: Context, isPlaying: Boolean) {
        try {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            if (shortcutManager != null) {
                val playPauseShortcut = android.content.pm.ShortcutInfo.Builder(context, "shortcut_play_pause")
                    .setShortLabel(if (isPlaying) "Pause" else "Play")
                    .setLongLabel(if (isPlaying) "Pause Music" else "Play Music")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, if (isPlaying) chromahub.rhythm.app.R.drawable.ic_pause_shortcut else chromahub.rhythm.app.R.drawable.ic_play_shortcut))
                    .setIntent(Intent(context, chromahub.rhythm.app.activities.MainActivity::class.java).apply {
                        action = "chromahub.rhythm.app.action.SHORTCUT_PLAY_PAUSE"
                    })
                    .build()

                val nextShortcut = android.content.pm.ShortcutInfo.Builder(context, "shortcut_next")
                    .setShortLabel("Next")
                    .setLongLabel("Next Track")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, chromahub.rhythm.app.R.drawable.ic_skip_next_shortcut))
                    .setIntent(Intent(context, chromahub.rhythm.app.activities.MainActivity::class.java).apply {
                        action = "chromahub.rhythm.app.action.SHORTCUT_SKIP_NEXT"
                    })
                    .build()

                val prevShortcut = android.content.pm.ShortcutInfo.Builder(context, "shortcut_previous")
                    .setShortLabel("Previous")
                    .setLongLabel("Previous Track")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, chromahub.rhythm.app.R.drawable.ic_skip_previous_shortcut))
                    .setIntent(Intent(context, chromahub.rhythm.app.activities.MainActivity::class.java).apply {
                        action = "chromahub.rhythm.app.action.SHORTCUT_SKIP_PREVIOUS"
                    })
                    .build()

                val openPlayerShortcut = android.content.pm.ShortcutInfo.Builder(context, "shortcut_open_player")
                    .setShortLabel("Open Player")
                    .setLongLabel("Open Music Player")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, chromahub.rhythm.app.R.drawable.rhythm_icon_small))
                    .setIntent(Intent(context, chromahub.rhythm.app.activities.MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        putExtra("OPEN_PLAYER", true)
                    })
                    .build()

                shortcutManager.dynamicShortcuts = listOf(playPauseShortcut, nextShortcut, prevShortcut, openPlayerShortcut)
                // Track shortcut usage so the system can surface the shortcuts as suggestions
                listOf("shortcut_play_pause", "shortcut_next", "shortcut_previous", "shortcut_open_player")
                    .forEach { ShortcutManagerCompat.reportShortcutUsed(context, it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("GlanceWidgetUpdater", "Error updating dynamic shortcuts", e)
        }
    }
}
