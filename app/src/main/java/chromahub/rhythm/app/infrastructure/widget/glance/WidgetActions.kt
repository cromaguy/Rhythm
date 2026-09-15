/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.widget.glance

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.infrastructure.service.MediaPlaybackService
import chromahub.rhythm.app.infrastructure.widget.glance.RhythmMusicWidget
import chromahub.rhythm.app.infrastructure.widget.glance.RhythmLyricsWidget
import kotlinx.coroutines.delay
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.core.content.edit


private fun hasActiveSongSnapshot(context: Context): Boolean {
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    val title = prefs.getString("song_title", "").orEmpty().trim()
    val artist = prefs.getString("artist_name", "").orEmpty().trim()
    val isIdleDefault = title.equals("Rhythm", ignoreCase = true) && artist.isBlank()
    return title.isNotBlank() && !isIdleDefault
}

private fun openRhythm(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    context.startActivity(intent)
}

private fun dispatchServiceAction(context: Context, action: String) {
    val intent = Intent(context, MediaPlaybackService::class.java).apply {
        this.action = action
    }
    try {
        context.startService(intent)
    } catch (e: Exception) {
        Log.w("WidgetAction", "startService failed for $action, retrying with startForegroundService", e)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (fgsError: Exception) {
            Log.w("WidgetAction", "Cannot start foreground service for action: $action", fgsError)
            openRhythm(context)
        }
    }
}

/**
 * Play/Pause action callback for widget
 */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!hasActiveSongSnapshot(context)) {
            openRhythm(context)
            return
        }

        dispatchServiceAction(context, MediaPlaybackService.ACTION_PLAY_PAUSE)
        
        // Trigger immediate widget update after short delay for state change
        delay(100)
        try { RhythmMusicWidget().updateAll(context) } catch (_: Exception) {}
        try { RhythmLyricsWidget().updateAll(context) } catch (_: Exception) {}
    }
}

/**
 * Skip to next track action callback for widget
 */
class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!hasActiveSongSnapshot(context)) {
            openRhythm(context)
            return
        }

        dispatchServiceAction(context, MediaPlaybackService.ACTION_SKIP_NEXT)
        
        // Trigger immediate widget update after short delay for state change
        delay(100)
        try { RhythmMusicWidget().updateAll(context) } catch (_: Exception) {}
        try { RhythmLyricsWidget().updateAll(context) } catch (_: Exception) {}
    }
}

/**
 * Skip to previous track action callback for widget
 */
class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!hasActiveSongSnapshot(context)) {
            openRhythm(context)
            return
        }

        dispatchServiceAction(context, MediaPlaybackService.ACTION_SKIP_PREVIOUS)
        
        // Trigger immediate widget update after short delay for state change
        delay(100)
        try { RhythmMusicWidget().updateAll(context) } catch (_: Exception) {}
        try { RhythmLyricsWidget().updateAll(context) } catch (_: Exception) {}
    }
}

/**
 * Toggle favorite action callback for widget
 */
class ToggleFavoriteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!hasActiveSongSnapshot(context)) {
            openRhythm(context)
            return
        }

        // OPTIMISTIC UPDATE: Toggle the state immediately in SharedPreferences and Glance Datastore
        try {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val currentFavorite = prefs.getBoolean(RhythmMusicWidget.KEY_IS_FAVORITE, false)
            val newFavorite = !currentFavorite
            
            // 1. SharedPreferences
            prefs.edit { putBoolean(RhythmMusicWidget.KEY_IS_FAVORITE, newFavorite) }
            
            // 2. Glance Datastore
            updateAppWidgetState(context, glanceId) { glancePrefs ->
                glancePrefs[booleanPreferencesKey(RhythmMusicWidget.KEY_IS_FAVORITE)] = newFavorite
            }
            RhythmMusicWidget().update(context, glanceId)
            Log.d("WidgetAction", "Optimistic favorite toggle: $newFavorite")
        } catch (e: Exception) {
            Log.e("WidgetAction", "Error during optimistic favorite toggle", e)
        }

        dispatchServiceAction(context, MediaPlaybackService.ACTION_TOGGLE_FAVORITE)
        
        // Trigger immediate widget update after short delay for state change
        delay(150)
        try { RhythmMusicWidget().updateAll(context) } catch (_: Exception) {}
    }
}

/**
 * Toggle shuffle action callback for widget
 */
class ToggleShuffleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!hasActiveSongSnapshot(context)) {
            openRhythm(context)
            return
        }

        try {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val currentShuffle = prefs.getBoolean("is_shuffle", false)
            val newShuffle = !currentShuffle

            prefs.edit { putBoolean("is_shuffle", newShuffle) }

            updateAppWidgetState(context, glanceId) { glancePrefs ->
                glancePrefs[booleanPreferencesKey("is_shuffle")] = newShuffle
            }
            try { RhythmCookieWidget().update(context, glanceId) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e("WidgetAction", "Error during optimistic shuffle toggle", e)
        }

        dispatchServiceAction(context, MediaPlaybackService.ACTION_TOGGLE_SHUFFLE)

        delay(150)
        GlanceWidgetUpdater.forceUpdateAll(context)
    }
}

/**
 * Toggle repeat mode action callback for widget
 */
class ToggleRepeatAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!hasActiveSongSnapshot(context)) {
            openRhythm(context)
            return
        }

        try {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val currentRepeat = prefs.getInt("repeat_mode", 0)
            val newRepeat = when (currentRepeat) {
                0 -> 2 // off -> all
                2 -> 1 // all -> one
                1 -> 0 // one -> off
                else -> 0
            }

            prefs.edit { putInt("repeat_mode", newRepeat) }

            updateAppWidgetState(context, glanceId) { glancePrefs ->
                glancePrefs[intPreferencesKey("repeat_mode")] = newRepeat
            }
            try { RhythmCookieWidget().update(context, glanceId) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e("WidgetAction", "Error during optimistic repeat toggle", e)
        }

        dispatchServiceAction(context, MediaPlaybackService.ACTION_TOGGLE_REPEAT)

        delay(150)
        GlanceWidgetUpdater.forceUpdateAll(context)
    }
}

