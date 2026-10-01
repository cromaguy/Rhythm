/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.GenreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Time range options for statistics
 */
enum class StatsTimeRange(val displayName: String, val daysBack: Int) {
    TODAY("Today", 1),
    WEEK("This Week", 7),
    MONTH("This Month", 30),
    ALL_TIME("All Time", Int.MAX_VALUE);
    
    fun resolveBounds(
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long?, Long> {
        val now = Instant.ofEpochMilli(nowMillis)
        val endBound = nowMillis
        
        val startBound: Long? = when (this) {
            TODAY -> {
                now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
            WEEK -> {
                now.atZone(zoneId).toLocalDate().minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
            MONTH -> {
                now.atZone(zoneId).toLocalDate().minusDays(29).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }
            ALL_TIME -> null
        }
        
        return startBound to endBound
    }
}

/**
 * Repository for tracking and analyzing playback statistics
 * Provides comprehensive stats tracking for songs and listening habits
 */
class PlaybackStatsRepository private constructor(private val context: Context) {
    private val gson = Gson()
    private val historyFile = File(context.applicationContext.filesDir, "playback_history.json")
    private val fileLock = Any()
    private val eventsType = object : TypeToken<MutableList<PlaybackEvent>>() {}.type
    
    companion object {
        @Volatile
        private var instance: PlaybackStatsRepository? = null
        
        // Keep 90 days of history
        private val MAX_HISTORY_AGE_MS = TimeUnit.DAYS.toMillis(90)
        
        // Maximum reasonable event duration (4 hours)
        private val MAX_REASONABLE_EVENT_DURATION_MS = TimeUnit.HOURS.toMillis(4)
        
        // Session gap threshold (30 minutes)
        private val SESSION_GAP_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(30)
        
        fun getInstance(context: Context): PlaybackStatsRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Observable state for UI updates
    private val _statsSummary = MutableStateFlow<PlaybackStatsSummary?>(null)
    val statsSummary: StateFlow<PlaybackStatsSummary?> = _statsSummary.asStateFlow()
    
    /**
     * Playback event data class
     */
    data class PlaybackEvent(
        val songId: String,
        val timestamp: Long,
        val durationMs: Long,
        val startTimestamp: Long? = null,
        val endTimestamp: Long? = null,
        val songTitle: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val genre: String? = null
    ) {
        fun startMillis(): Long = startTimestamp ?: (timestamp - durationMs).coerceAtLeast(0L)
        fun endMillis(): Long = endTimestamp ?: timestamp
    }
    
    /**
     * Song playback summary
     */
    data class SongPlaybackSummary(
        val songId: String,
        val title: String,
        val artist: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int
    )
    
    /**
     * Artist playback summary
     */
    data class ArtistPlaybackSummary(
        val artist: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )
    
    /**
     * Genre playback summary
     */
    data class GenrePlaybackSummary(
        val genre: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val percentage: Float
    )
    
    /**
     * Album playback summary
     */
    data class AlbumPlaybackSummary(
        val album: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )
    
    /**
     * Timeline entry for charts
     */
    data class TimelineEntry(
        val label: String,
        val totalDurationMs: Long,
        val playCount: Int
    )
    
    /**
     * Playback segment for deduplication
     */
    private data class PlaybackSegment(
        val songId: String,
        val startMillis: Long,
        val endMillis: Long
    ) {
        val durationMs: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)
    }
    
    /**
     * Playback span for overall listening calculation
     */
    private data class PlaybackSpan(
        val startMillis: Long,
        val endMillis: Long
    ) {
        val durationMs: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)
    }
    
    /**
     * Listening session data
     */
    data class ListeningSession(
        val startMillis: Long,
        val endMillis: Long,
        val totalDuration: Long,
        val songCount: Int
    )
    
    /**
     * Day slice for daily stats
     */
    private data class DaySlice(
        val date: LocalDate,
        val durationMs: Long
    )
    
    /**
     * Daily listening bucket for hour-by-hour distribution
     */
    data class DailyListeningBucket(
        val startHour: Int,
        val endHour: Int,
        val totalDurationMs: Long
    )
    
    /**
     * Comprehensive playback stats summary
     */
    data class PlaybackStatsSummary(
        val range: StatsTimeRange,
        val startTimestamp: Long?,
        val endTimestamp: Long,
        val totalDurationMs: Long,
        val totalPlayCount: Int,
        val uniqueSongs: Int,
        val uniqueArtists: Int,
        val averageDailyDurationMs: Long,
        val topSongs: List<SongPlaybackSummary>,
        val topArtists: List<ArtistPlaybackSummary>,
        val topAlbums: List<AlbumPlaybackSummary>,
        val topGenres: List<GenrePlaybackSummary>,
        val timeline: List<TimelineEntry>,
        val activeDays: Int,
        val longestStreakDays: Int,
        val currentStreakDays: Int,
        val totalSessions: Int,
        val averageSessionDurationMs: Long,
        val longestSessionDurationMs: Long,
        val averageSessionsPerDay: Float,
        val peakDayOfWeek: String?,
        val peakDayDurationMs: Long,
        val peakHour: Int?,
        val dailyDistribution: List<DailyListeningBucket>
    )
    
    /**
     * Record a playback event
     */
    fun recordPlayback(
        song: Song,
        durationMs: Long,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (song.id.isBlank() || durationMs <= 0) return
        
        val coercedTimestamp = timestamp.coerceAtLeast(0L)
        val coercedDuration = durationMs.coerceIn(0L, MAX_REASONABLE_EVENT_DURATION_MS)
        val start = (coercedTimestamp - coercedDuration).coerceAtLeast(0L)
        
        val event = PlaybackEvent(
            songId = song.id,
            timestamp = coercedTimestamp,
            durationMs = coercedDuration,
            startTimestamp = start,
            endTimestamp = coercedTimestamp,
            songTitle = song.title,
            artistName = song.artist,
            albumName = song.album,
            genre = song.genre
        )
        
        synchronized(fileLock) {
            val events = readEventsLocked()
            
            // Clean old events
            val cutoff = coercedTimestamp - MAX_HISTORY_AGE_MS
            if (cutoff > 0) {
                events.removeAll { it.endMillis() < cutoff }
            }
            
            events += event
            writeEventsLocked(events)
        }
    }
    
    /**
     * Record playback with simple duration tracking (for simpler integration)
     */
    fun recordSimplePlayback(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        genre: String?,
        durationMs: Long,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (songId.isBlank() || durationMs <= 0) return
        
        val event = PlaybackEvent(
            songId = songId,
            timestamp = timestamp,
            durationMs = durationMs.coerceIn(0L, MAX_REASONABLE_EVENT_DURATION_MS),
            startTimestamp = (timestamp - durationMs).coerceAtLeast(0L),
            endTimestamp = timestamp,
            songTitle = title,
            artistName = artist,
            albumName = album,
            genre = genre
        )
        
        synchronized(fileLock) {
            val events = readEventsLocked()
            val cutoff = timestamp - MAX_HISTORY_AGE_MS
            if (cutoff > 0) {
                events.removeAll { it.endMillis() < cutoff }
            }
            events += event
            writeEventsLocked(events)
        }
    }
    
    /**
     * Load statistics summary for a given time range
     */
    suspend fun loadSummary(
        range: StatsTimeRange,
        songs: List<Song> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): PlaybackStatsSummary = withContext(Dispatchers.IO) {
        val zoneId = ZoneId.systemDefault()
        val allEvents = readEvents()
        val (startBound, endBound) = range.resolveBounds(nowMillis, zoneId)
        
        // Filter events to the time range
        val filteredEvents = allEvents.mapNotNull { event ->
            val start = event.startMillis()
            val end = event.endMillis()
            val lowerBound = startBound ?: Long.MIN_VALUE
            
            if (end < lowerBound || start > endBound) {
                return@mapNotNull null
            }
            
            // Clip to range boundaries
            val clippedStart = max(start, lowerBound)
            val clippedEnd = min(end, endBound)
            val clippedDuration = (clippedEnd - clippedStart).coerceAtLeast(0L)
            
            if (clippedDuration <= 0L) {
                return@mapNotNull null
            }
            
            event.copy(
                timestamp = clippedEnd,
                durationMs = clippedDuration,
                startTimestamp = clippedStart,
                endTimestamp = clippedEnd
            )
        }
        
        // Build song map for lookups
        val songMap = songs.associateBy { it.id }
        
        // Group events by song
        val eventsBySong = filteredEvents.groupBy { it.songId }
        
        // Merge overlapping segments per song
        val segmentsBySong = eventsBySong.mapValues { (_, events) ->
            mergeSongEvents(events)
        }
        
        // Calculate overall spans (merge across songs for total listening time)
        val allSpans = segmentsBySong.values.flatten().map { 
            PlaybackSpan(it.startMillis, it.endMillis) 
        }
        val mergedSpans = mergeSpans(allSpans)
        
        val effectiveStart = startBound ?: mergedSpans.minOfOrNull { it.startMillis } ?: nowMillis
        val effectiveEnd = mergedSpans.maxOfOrNull { it.endMillis } ?: endBound
        
        val totalDuration = mergedSpans.sumOf { it.durationMs }
        val totalPlays = segmentsBySong.values.sumOf { it.size }
        val uniqueSongsCount = segmentsBySong.keys.size
        
        // Calculate day span for averages
        val daySpan = if (startBound != null) {
            val startDate = Instant.ofEpochMilli(effectiveStart).atZone(zoneId).toLocalDate()
            val endDate = Instant.ofEpochMilli(effectiveEnd).atZone(zoneId).toLocalDate()
            max(1L, ChronoUnit.DAYS.between(startDate, endDate) + 1)
        } else {
            1L
        }
        val averageDailyDuration = if (daySpan > 0) totalDuration / daySpan else totalDuration
        
        // Top songs
        val topSongs = segmentsBySong.mapNotNull { (songId, segments) ->
            val song = songMap[songId]
            val event = filteredEvents.find { it.songId == songId }
            val title = song?.title ?: event?.songTitle ?: "Unknown"
            val artist = song?.artist ?: event?.artistName ?: "Unknown Artist"
            
            SongPlaybackSummary(
                songId = songId,
                title = title,
                artist = artist,
                albumArtUri = song?.artworkUri?.toString(),
                totalDurationMs = segments.sumOf { it.durationMs },
                playCount = segments.size
            )
        }.sortedByDescending { it.totalDurationMs }.take(5)
        
        // Top artists (supports multi-artist tags like "Kendrick Lamar; SZA")
        val appSettings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context)
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val delimiters = if (artistSeparatorEnabled) appSettings.artistSeparatorDelimiters.value else ""
        val artistDurations = mutableMapOf<String, Long>()
        val artistPlayCounts = mutableMapOf<String, Int>()
        val artistSongSets = mutableMapOf<String, MutableSet<String>>()

        segmentsBySong.forEach { (songId, segments) ->
            val rawArtist = songMap[songId]?.artist
                ?: filteredEvents.find { it.songId == songId }?.artistName
            val resolvedArtists = if (rawArtist.isNullOrBlank()) {
                listOf("Unknown Artist")
            } else {
                chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                    artistName = rawArtist,
                    delimiters = delimiters,
                    enabled = artistSeparatorEnabled
                ).ifEmpty { listOf(rawArtist.trim()) }
            }
            val songDuration = segments.sumOf { it.durationMs }
            val songPlayCount = segments.size

            resolvedArtists.forEach { artist ->
                artistDurations[artist] = artistDurations.getOrDefault(artist, 0L) + songDuration
                artistPlayCounts[artist] = artistPlayCounts.getOrDefault(artist, 0) + songPlayCount
                artistSongSets.getOrPut(artist) { mutableSetOf() }.add(songId)
            }
        }

        val topArtists = artistDurations.map { (artist, durationMs) ->
            ArtistPlaybackSummary(
                artist = artist,
                totalDurationMs = durationMs,
                playCount = artistPlayCounts.getOrDefault(artist, 0),
                uniqueSongs = artistSongSets[artist]?.size ?: 0
            )
        }.sortedByDescending { it.totalDurationMs }.take(5)
        
        val uniqueArtistsCount = artistDurations.keys.size
        
        // Top albums
        val albumGroups = segmentsBySong.entries.groupBy { (songId, _) ->
            songMap[songId]?.album 
                ?: filteredEvents.find { it.songId == songId }?.albumName 
                ?: "Unknown Album"
        }
        val topAlbums = albumGroups.map { (album, songEntries) ->
            val allSegments = songEntries.flatMap { it.value }
            val firstSong = songEntries.firstOrNull()?.let { songMap[it.key] }
            AlbumPlaybackSummary(
                album = album,
                albumArtUri = firstSong?.artworkUri?.toString(),
                totalDurationMs = allSegments.sumOf { it.durationMs },
                playCount = allSegments.size,
                uniqueSongs = songEntries.size
            )
        }.sortedByDescending { it.totalDurationMs }.take(5)
        
        // Top genres (supports multi-genre tags like "Mahur, Shur")
        val genreDurations = mutableMapOf<String, Long>()
        val genrePlayCounts = mutableMapOf<String, Int>()

        segmentsBySong.forEach { (songId, segments) ->
            val rawGenre = songMap[songId]?.genre
                ?: filteredEvents.find { it.songId == songId }?.genre
            val resolvedGenres = GenreUtils.splitGenres(rawGenre).ifEmpty { listOf("Unknown") }
            val songDuration = segments.sumOf { it.durationMs }
            val songPlayCount = segments.size

            resolvedGenres.forEach { genre ->
                genreDurations[genre] = genreDurations.getOrDefault(genre, 0L) + songDuration
                genrePlayCounts[genre] = genrePlayCounts.getOrDefault(genre, 0) + songPlayCount
            }
        }

        val totalGenrePlays = genrePlayCounts.values.sum().coerceAtLeast(1)
        val topGenres = genreDurations.map { (genre, durationMs) ->
            val playCount = genrePlayCounts.getOrDefault(genre, 0)
            GenrePlaybackSummary(
                genre = genre,
                totalDurationMs = durationMs,
                playCount = playCount,
                percentage = playCount.toFloat() / totalGenrePlays
            )
        }.sortedByDescending { it.totalDurationMs }.take(5)
        
        // Calculate day slices for streak and peak day
        val daySlices = mergedSpans.flatMap { sliceSpanByDay(it, zoneId) }
        val eventsByDay = daySlices.groupBy { it.date }
        val activeDays = eventsByDay.size
        
        // Calculate longest streak
        val sortedDays = eventsByDay.keys.sorted()
        var longestStreak = 0
        var currentStreak = 0
        var lastDay: LocalDate? = null
        sortedDays.forEach { day ->
            if (lastDay == null || day == lastDay.plusDays(1)) {
                currentStreak++
            } else {
                currentStreak = 1
            }
            if (currentStreak > longestStreak) {
                longestStreak = currentStreak
            }
            lastDay = day
        }
        
        // Calculate sessions
        val sessions = computeListeningSessions(mergedSpans)
        val totalSessions = sessions.size
        val totalSessionDuration = sessions.sumOf { it.totalDuration }
        val averageSessionDuration = if (totalSessions > 0) totalSessionDuration / totalSessions else 0L
        val longestSessionDuration = sessions.maxOfOrNull { it.totalDuration } ?: 0L
        val averageSessionsPerDay = if (daySpan > 0) totalSessions.toFloat() / daySpan else 0f
        
        // Peak day of week
        val durationsByDayOfWeek = daySlices.groupBy { it.date.dayOfWeek }
        val peakDay = durationsByDayOfWeek.maxByOrNull { entry ->
            entry.value.sumOf { it.durationMs }
        }
        val peakDayLabel = peakDay?.key?.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val peakDayDuration = peakDay?.value?.sumOf { it.durationMs } ?: 0L
        
        // Timeline entries (by day for week/month, by hour for today)
        val timeline = createTimelineEntries(range, mergedSpans, zoneId, nowMillis)
        
        // Daily distribution (hour buckets)
        val dailyDistribution = computeDailyDistribution(mergedSpans, zoneId)
        val peakHour = dailyDistribution.maxByOrNull { it.totalDurationMs }?.startHour
        
        val summary = PlaybackStatsSummary(
            range = range,
            startTimestamp = startBound,
            endTimestamp = endBound,
            totalDurationMs = totalDuration,
            totalPlayCount = totalPlays,
            uniqueSongs = uniqueSongsCount,
            uniqueArtists = uniqueArtistsCount,
            averageDailyDurationMs = averageDailyDuration,
            topSongs = topSongs,
            topArtists = topArtists,
            topAlbums = topAlbums,
            topGenres = topGenres,
            timeline = timeline,
            activeDays = activeDays,
            longestStreakDays = longestStreak,
            currentStreakDays = run {
                val today = LocalDate.now(zoneId)
                val yesterday = today.minusDays(1)
                if (lastDay == today || lastDay == yesterday) currentStreak else 0
            },
            totalSessions = totalSessions,
            averageSessionDurationMs = averageSessionDuration,
            longestSessionDurationMs = longestSessionDuration,
            averageSessionsPerDay = averageSessionsPerDay,
            peakDayOfWeek = peakDayLabel,
            peakDayDurationMs = peakDayDuration,
            peakHour = peakHour,
            dailyDistribution = dailyDistribution
        )
        
        _statsSummary.value = summary
        summary
    }
    
    /**
     * Get playback statistics for a specific song
     */
    suspend fun getSongPlaybackStats(
        songId: String,
        range: StatsTimeRange = StatsTimeRange.ALL_TIME,
        nowMillis: Long = System.currentTimeMillis()
    ): SongPlaybackSummary? = withContext(Dispatchers.IO) {
        val allEvents = readEvents()
        val (startBound, endBound) = range.resolveBounds(nowMillis, ZoneId.systemDefault())
        
        // Filter events for this song and time range
        val songEvents = allEvents.filter { event ->
            event.songId == songId &&
            event.endMillis() >= (startBound ?: Long.MIN_VALUE) &&
            event.startMillis() <= endBound
        }.mapNotNull { event ->
            val start = event.startMillis()
            val end = event.endMillis()
            val lowerBound = startBound ?: Long.MIN_VALUE
            
            if (end < lowerBound || start > endBound) {
                return@mapNotNull null
            }
            
            // Clip to range boundaries
            val clippedStart = max(start, lowerBound)
            val clippedEnd = min(end, endBound)
            val clippedDuration = (clippedEnd - clippedStart).coerceAtLeast(0L)
            
            if (clippedDuration <= 0L) {
                return@mapNotNull null
            }
            
            event.copy(
                timestamp = clippedEnd,
                durationMs = clippedDuration,
                startTimestamp = clippedStart,
                endTimestamp = clippedEnd
            )
        }
        
        if (songEvents.isEmpty()) {
            return@withContext null
        }
        
        // Merge overlapping segments
        val segments = mergeSongEvents(songEvents)
        val totalDuration = segments.sumOf { it.durationMs }
        val playCount = segments.size
        
        // Get song info from the first event
        val firstEvent = songEvents.first()
        
        SongPlaybackSummary(
            songId = songId,
            title = firstEvent.songTitle ?: "Unknown",
            artist = firstEvent.artistName ?: "Unknown Artist",
            albumArtUri = null, // We don't have this in events
            totalDurationMs = totalDuration,
            playCount = playCount
        )
    }

    /**
     * Retrieves recent playback history as distinct song IDs ordered by last played timestamp descending.
     */
    fun getRecentlyPlayedSongIds(limit: Int = 50): List<String> {
        val events = readEvents()
        return events
            .asSequence()
            .filter { it.songId.isNotBlank() }
            .sortedByDescending { it.endMillis() }
            .map { it.songId }
            .distinct()
            .take(limit)
            .toList()
    }

    /**
     * Retrieves "On Repeat" song IDs (songs with highest frequency and listening time in the last [daysBack] days).
     */
    fun getOnRepeatSongIds(daysBack: Int = 14, limit: Int = 50, nowMillis: Long = System.currentTimeMillis()): List<String> {
        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(daysBack.toLong())
        val events = readEvents().filter { it.endMillis() >= cutoff && it.songId.isNotBlank() }
        if (events.isEmpty()) return emptyList()

        val songScores = mutableMapOf<String, Double>()
        val oneDayMs = TimeUnit.DAYS.toMillis(1).toDouble()

        events.forEach { event ->
            val daysAgo = ((nowMillis - event.endMillis()).coerceAtLeast(0L) / oneDayMs).coerceAtMost(daysBack.toDouble())
            val recencyMultiplier = 1.0 - (daysAgo / (daysBack * 1.5))
            val eventScore = (1.0 + (event.durationMs / 60000.0).coerceAtMost(5.0)) * recencyMultiplier
            songScores[event.songId] = (songScores[event.songId] ?: 0.0) + eventScore
        }

        return songScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
    }

    /**
     * Retrieves "Forgotten Favorites" song IDs (songs with high all-time play counts that have NOT been played in the last [daysNotPlayed] days).
     */
    fun getForgottenFavoritesSongIds(
        allTimePlayCounts: Map<String, Int>,
        daysNotPlayed: Int = 30,
        limit: Int = 50,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> {
        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(daysNotPlayed.toLong())
        val recentlyPlayedIds = readEvents()
            .filter { it.endMillis() >= cutoff }
            .map { it.songId }
            .toSet()

        return allTimePlayCounts.entries
            .asSequence()
            .filter { (songId, playCount) -> playCount >= 4 && !recentlyPlayedIds.contains(songId) }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
    }
    
    /**
     * Clear all history
     */
    fun clearHistory() {
        synchronized(fileLock) {
            if (historyFile.exists()) {
                historyFile.delete()
            }
        }
        _statsSummary.value = null
    }
    
    // Private helper methods
    
    private fun readEvents(): List<PlaybackEvent> = synchronized(fileLock) { 
        readEventsLocked() 
    }
    
    private fun readEventsLocked(): MutableList<PlaybackEvent> {
        if (!historyFile.exists()) {
            return mutableListOf()
        }
        val raw = runCatching { historyFile.readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return mutableListOf()
        
        return runCatching {
            val element = gson.fromJson(raw, JsonElement::class.java)
            if (element.isJsonArray) {
                gson.fromJson<MutableList<PlaybackEvent>>(element, eventsType)
            } else {
                mutableListOf()
            }
        }.getOrElse { mutableListOf() }
    }
    
    private fun writeEventsLocked(events: List<PlaybackEvent>) {
        runCatching {
            historyFile.writeText(gson.toJson(events))
        }
    }
    
    private fun mergeSongEvents(events: List<PlaybackEvent>): List<PlaybackSegment> {
        if (events.isEmpty()) return emptyList()
        
        val sorted = events.sortedBy { it.startMillis() }
        val songId = sorted.first().songId
        val segments = mutableListOf<PlaybackSegment>()
        
        var currentStart = sorted.first().startMillis()
        var currentEnd = sorted.first().endMillis()
        
        for (i in 1 until sorted.size) {
            val event = sorted[i]
            val eventStart = event.startMillis()
            val eventEnd = event.endMillis()
            
            if (eventStart <= currentEnd) {
                // Overlapping or adjacent - extend current segment
                currentEnd = max(currentEnd, eventEnd)
            } else {
                // Gap - save current segment and start new one
                segments.add(PlaybackSegment(songId, currentStart, currentEnd))
                currentStart = eventStart
                currentEnd = eventEnd
            }
        }
        segments.add(PlaybackSegment(songId, currentStart, currentEnd))
        
        return segments
    }
    
    private fun mergeSpans(spans: List<PlaybackSpan>): List<PlaybackSpan> {
        if (spans.isEmpty()) return emptyList()
        
        val sorted = spans.sortedBy { it.startMillis }
        val merged = mutableListOf<PlaybackSpan>()
        
        var currentStart = sorted.first().startMillis
        var currentEnd = sorted.first().endMillis
        
        for (i in 1 until sorted.size) {
            val span = sorted[i]
            if (span.startMillis <= currentEnd) {
                currentEnd = max(currentEnd, span.endMillis)
            } else {
                merged.add(PlaybackSpan(currentStart, currentEnd))
                currentStart = span.startMillis
                currentEnd = span.endMillis
            }
        }
        merged.add(PlaybackSpan(currentStart, currentEnd))
        
        return merged
    }
    
    private fun sliceSpanByDay(span: PlaybackSpan, zoneId: ZoneId): List<DaySlice> {
        val slices = mutableListOf<DaySlice>()
        var current = span.startMillis
        
        while (current < span.endMillis) {
            val currentDate = Instant.ofEpochMilli(current).atZone(zoneId).toLocalDate()
            val dayEnd = currentDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val sliceEnd = min(dayEnd, span.endMillis)
            val duration = sliceEnd - current
            
            if (duration > 0) {
                slices.add(DaySlice(currentDate, duration))
            }
            current = sliceEnd
        }
        
        return slices
    }
    
    private fun computeListeningSessions(spans: List<PlaybackSpan>): List<ListeningSession> {
        if (spans.isEmpty()) return emptyList()
        
        val sorted = spans.sortedBy { it.startMillis }
        val sessions = mutableListOf<ListeningSession>()
        
        var sessionStart = sorted.first().startMillis
        var sessionEnd = sorted.first().endMillis
        var sessionDuration = sorted.first().durationMs
        var songCount = 1
        
        for (i in 1 until sorted.size) {
            val span = sorted[i]
            
            if (span.startMillis - sessionEnd > SESSION_GAP_THRESHOLD_MS) {
                // New session
                sessions.add(ListeningSession(sessionStart, sessionEnd, sessionDuration, songCount))
                sessionStart = span.startMillis
                sessionEnd = span.endMillis
                sessionDuration = span.durationMs
                songCount = 1
            } else {
                // Same session
                sessionEnd = max(sessionEnd, span.endMillis)
                sessionDuration += span.durationMs
                songCount++
            }
        }
        sessions.add(ListeningSession(sessionStart, sessionEnd, sessionDuration, songCount))
        
        return sessions
    }
    
    private fun createTimelineEntries(
        range: StatsTimeRange,
        spans: List<PlaybackSpan>,
        zoneId: ZoneId,
        nowMillis: Long
    ): List<TimelineEntry> {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        
        return when (range) {
            StatsTimeRange.TODAY -> {
                // Hour buckets for today
                (0..23).map { hour ->
                    val hourStart = now.toLocalDate().atStartOfDay(zoneId).plusHours(hour.toLong())
                    val hourEnd = hourStart.plusHours(1)
                    val duration = spans.sumOf { span ->
                        val overlapStart = max(span.startMillis, hourStart.toInstant().toEpochMilli())
                        val overlapEnd = min(span.endMillis, hourEnd.toInstant().toEpochMilli())
                        (overlapEnd - overlapStart).coerceAtLeast(0L)
                    }
                    val playCount = spans.count { span ->
                        span.startMillis < hourEnd.toInstant().toEpochMilli() &&
                        span.endMillis > hourStart.toInstant().toEpochMilli()
                    }
                    TimelineEntry(
                        label = String.format(Locale.US, "%02d:00", hour),
                        totalDurationMs = duration,
                        playCount = playCount
                    )
                }
            }
            StatsTimeRange.WEEK -> {
                // Day buckets for the week
                (0..6).map { daysAgo ->
                    val date = now.toLocalDate().minusDays(daysAgo.toLong())
                    val dayStart = date.atStartOfDay(zoneId)
                    val dayEnd = dayStart.plusDays(1)
                    val duration = spans.sumOf { span ->
                        val overlapStart = max(span.startMillis, dayStart.toInstant().toEpochMilli())
                        val overlapEnd = min(span.endMillis, dayEnd.toInstant().toEpochMilli())
                        (overlapEnd - overlapStart).coerceAtLeast(0L)
                    }
                    val playCount = spans.count { span ->
                        span.startMillis < dayEnd.toInstant().toEpochMilli() &&
                        span.endMillis > dayStart.toInstant().toEpochMilli()
                    }
                    TimelineEntry(
                        label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        totalDurationMs = duration,
                        playCount = playCount
                    )
                }.reversed()
            }
            else -> {
                // Week buckets for month/all time
                val slices = spans.flatMap { sliceSpanByDay(it, zoneId) }
                val weekGroups = slices.groupBy { slice ->
                    val weekOfMonth = (slice.date.dayOfMonth - 1) / 7 + 1
                    "Week $weekOfMonth"
                }
                weekGroups.map { (week, slices) ->
                    TimelineEntry(
                        label = week,
                        totalDurationMs = slices.sumOf { it.durationMs },
                        playCount = slices.size
                    )
                }.sortedBy { it.label }
            }
        }
    }
    
    private fun computeDailyDistribution(
        spans: List<PlaybackSpan>,
        zoneId: ZoneId
    ): List<DailyListeningBucket> {
        val hourBuckets = (0..23).associateWith { 0L }.toMutableMap()
        
        spans.forEach { span ->
            var current = span.startMillis
            while (current < span.endMillis) {
                val currentTime = Instant.ofEpochMilli(current).atZone(zoneId)
                val hour = currentTime.hour
                val nextHour = currentTime.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                val hourEnd = min(nextHour.toInstant().toEpochMilli(), span.endMillis)
                val duration = hourEnd - current
                
                hourBuckets[hour] = (hourBuckets[hour] ?: 0L) + duration
                current = hourEnd
            }
        }
        
        return hourBuckets.map { (hour, duration) ->
            DailyListeningBucket(
                startHour = hour,
                endHour = (hour + 1) % 24,
                totalDurationMs = duration
            )
        }.sortedBy { it.startHour }
    }
}
