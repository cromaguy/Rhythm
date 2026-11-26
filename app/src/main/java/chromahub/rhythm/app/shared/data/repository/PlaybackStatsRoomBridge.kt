/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.ListeningSpanEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEngagementEntity

/**
 * Pure mapper + one-shot idempotent backfill synchronizing legacy
 * `playback_history.json` events into Room v10 `listening_spans` and `song_engagements`.
 */
internal object PlaybackStatsRoomBridge {
    private const val TAG = "PlaybackStatsRoomBridge"
    private const val PREFS_NAME = "rhythm_stats_db_bridge"
    private const val KEY_BACKFILL_COMPLETE = "json_to_room_v10_done"

    internal data class MappedStats(
        val spans: List<ListeningSpanEntity>,
        val engagements: List<SongEngagementEntity>
    )

    internal fun mapEventsToEntities(events: List<PlaybackStatsRepository.PlaybackEvent>): MappedStats {
        if (events.isEmpty()) return MappedStats(emptyList(), emptyList())

        val spans = ArrayList<ListeningSpanEntity>(events.size)
        val counts = HashMap<String, Long>()
        val durations = HashMap<String, Long>()
        val lastPlayed = HashMap<String, Long>()

        for (event in events) {
            if (event.songId.isBlank() || event.durationMs <= 0L) continue
            val started = event.startMillis()
            val ended = maxOf(started, event.endMillis())
            spans.add(
                ListeningSpanEntity(
                    songId = event.songId,
                    songTitle = event.songTitle,
                    artist = event.artistName,
                    album = event.albumName,
                    genre = event.genre,
                    startedAtMs = started,
                    endedAtMs = ended,
                    durationMs = event.durationMs,
                    completed = event.durationMs >= 30_000L
                )
            )
            counts[event.songId] = (counts[event.songId] ?: 0L) + 1L
            durations[event.songId] = (durations[event.songId] ?: 0L) + event.durationMs
            val prevLast = lastPlayed[event.songId] ?: 0L
            if (ended > prevLast) lastPlayed[event.songId] = ended
        }

        val engagements = counts.keys.map { songId ->
            SongEngagementEntity(
                songId = songId,
                playCount = counts[songId] ?: 0L,
                totalPlayDurationMs = durations[songId] ?: 0L,
                lastPlayedAtMs = lastPlayed[songId] ?: 0L,
                skipCount = 0L
            )
        }
        return MappedStats(spans = spans, engagements = engagements)
    }

    suspend fun backfillIfNeeded(
        context: Context,
        eventsSnapshot: () -> List<PlaybackStatsRepository.PlaybackEvent>
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BACKFILL_COMPLETE, false)) return

        runCatching {
            val dao = RhythmDatabase.getInstance(appContext).listeningStatsDao()
            if (dao.getSpanCount() == 0) {
                val mapped = mapEventsToEntities(eventsSnapshot())
                if (mapped.spans.isNotEmpty()) {
                    mapped.spans.chunked(250).forEach { dao.insertSpans(it) }
                    mapped.engagements.chunked(250).forEach { dao.upsertEngagements(it) }
                    Log.i(TAG, "Backfilled ${mapped.spans.size} spans and ${mapped.engagements.size} engagements into Room")
                }
            }
            prefs.edit { putBoolean(KEY_BACKFILL_COMPLETE, true) }
        }.onFailure {
            Log.w(TAG, "Skipping stats backfill due to error", it)
        }
    }

    suspend fun recordEvent(
        context: Context,
        songId: String,
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
        startedAtMs: Long,
        endedAtMs: Long,
        durationMs: Long
    ) {
        runCatching {
            val dao = RhythmDatabase.getInstance(context.applicationContext).listeningStatsDao()
            dao.recordListeningEvent(
                songId = songId,
                songTitle = title,
                artist = artist,
                album = album,
                genre = genre,
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                durationMs = durationMs,
                completed = durationMs >= 30_000L
            )
        }.onFailure {
            Log.w(TAG, "Failed to mirror playback event to Room", it)
        }
    }
}
