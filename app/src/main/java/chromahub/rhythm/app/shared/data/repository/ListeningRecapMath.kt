/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.repository

import chromahub.rhythm.app.shared.data.model.Song
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ListeningPersonality(val title: String, val subtitle: String) {
    EARLY_BIRD("Early Bird", "Most of your streams land between 05:00 and 11:00"),
    DAYTIME("Daytime Listener", "Your soundtrack peaks across midday sessions"),
    EVENING("Evening Unwinder", "Golden hour and dinner sessions lead your day"),
    NIGHT_OWL("Night Owl", "Your headphones shine brightest after dark")
}

data class DecadeShare(
    val decade: Int,
    val share: Float
)

data class ListeningRecap(
    val year: Int,
    val isYearComplete: Boolean,
    val totalListenedMs: Long,
    val totalPlays: Int,
    val activeDays: Int,
    val daysInRange: Int,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val monthlyDurationsMs: List<Long>,
    val hourlyDurationsMs: List<Long>,
    val personality: ListeningPersonality,
    val favoriteDayOfWeek: DayOfWeek?,
    val biggestDay: LocalDate?,
    val biggestDayDurationMs: Long,
    val topArtist: String?,
    val topArtistPlayCount: Int,
    val topSongs: List<PlaybackStatsRepository.SongPlaybackSummary>,
    val topDecade: DecadeShare?
) {
    val averageDailyMs: Long
        get() = if (daysInRange > 0) totalListenedMs / daysInRange else 0L
}

/**
 * Pure calendar-year analytics & Wrapped story math operating on Rhythm [PlaybackEvent] spans.
 */
object ListeningRecapMath {

    private const val MIN_TAGGED_SHARE = 0.25f
    private const val MIN_DECADE_SHARE_OF_TAGGED = 0.25f
    private val PLAUSIBLE_YEARS = 1900..2100

    data class RecapBounds(
        val startMillis: Long,
        val endMillis: Long,
        val isYearComplete: Boolean,
        val daysInRange: Int
    )

    fun recapYearBounds(year: Int, zoneId: ZoneId, nowMillis: Long): RecapBounds? {
        if (year !in 1970..9999) return null
        val firstDay = LocalDate.of(year, 1, 1)
        val start = firstDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        if (start > nowMillis) return null
        val nextYearStart = firstDay.plusYears(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val isComplete = nowMillis >= nextYearStart
        val end = if (isComplete) nextYearStart - 1L else nowMillis
        val lastDay = Instant.ofEpochMilli(end).atZone(zoneId).toLocalDate()
        return RecapBounds(
            startMillis = start,
            endMillis = end,
            isYearComplete = isComplete,
            daysInRange = (ChronoUnit.DAYS.between(firstDay, lastDay) + 1L).toInt()
        )
    }

    /** Slices [events] into 24 local hour buckets, respecting DST transitions cleanly. */
    fun hourlyDurations(
        events: List<PlaybackStatsRepository.PlaybackEvent>,
        zoneId: ZoneId
    ): LongArray {
        val totals = LongArray(24)
        val rules = zoneId.rules
        for (event in events) {
            var cursor = event.startMillis()
            val end = maxOf(cursor, event.endMillis())
            while (cursor < end) {
                val instant = Instant.ofEpochMilli(cursor)
                val zoned = instant.atZone(zoneId)
                val nextHour = zoned.toLocalDateTime()
                    .truncatedTo(ChronoUnit.HOURS)
                    .plusHours(1)
                    .toInstant(zoned.offset)
                    .toEpochMilli()
                val nextTransition = rules.nextTransition(instant)?.instant?.toEpochMilli() ?: Long.MAX_VALUE
                val sliceEnd = minOf(end, nextHour, nextTransition)
                if (sliceEnd <= cursor) break
                totals[zoned.hour] += (sliceEnd - cursor)
                cursor = sliceEnd
            }
        }
        return totals
    }

    fun classifyPersonality(hourly: LongArray): ListeningPersonality {
        require(hourly.size == 24) { "Expected 24 hourly buckets, got ${hourly.size}" }
        fun avg(hours: List<Int>): Double = hours.sumOf { hourly[it] }.toDouble() / hours.size
        val windows = listOf(
            ListeningPersonality.EARLY_BIRD to (5..10).toList(),
            ListeningPersonality.DAYTIME to (11..16).toList(),
            ListeningPersonality.EVENING to (17..21).toList(),
            ListeningPersonality.NIGHT_OWL to listOf(22, 23, 0, 1, 2, 3, 4)
        )
        return windows.maxBy { avg(it.second) }.first
    }

    fun dominantDecade(listenedMsByReleaseYear: Map<Int, Long>, totalMs: Long): DecadeShare? {
        if (totalMs <= 0L) return null
        val byDecade = listenedMsByReleaseYear
            .filterKeys { it in PLAUSIBLE_YEARS }
            .entries
            .groupBy({ (it.key / 10) * 10 }, { it.value })
            .mapValues { (_, list) -> list.sum() }
        val taggedMs = byDecade.values.sum()
        if (taggedMs <= 0L || taggedMs < totalMs * MIN_TAGGED_SHARE) return null
        val best = byDecade.entries.maxWithOrNull(
            compareBy<Map.Entry<Int, Long>> { it.value }.thenBy { it.key }
        ) ?: return null
        if (best.value < taggedMs * MIN_DECADE_SHARE_OF_TAGGED) return null
        return DecadeShare(
            decade = best.key,
            share = (best.value.toDouble() / totalMs).toFloat().coerceIn(0f, 1f)
        )
    }

    fun buildRecap(
        year: Int,
        events: List<PlaybackStatsRepository.PlaybackEvent>,
        songs: List<Song>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): ListeningRecap? {
        val bounds = recapYearBounds(year, zoneId, nowMillis) ?: return null
        val scoped = events.mapNotNull { ev ->
            val s = maxOf(ev.startMillis(), bounds.startMillis)
            val e = minOf(ev.endMillis(), bounds.endMillis)
            if (e <= s) null else ev.copy(startTimestamp = s, endTimestamp = e, durationMs = e - s)
        }
        if (scoped.isEmpty()) return null

        val songById = songs.associateBy { it.id }
        val monthly = LongArray(12)
        val byDate = HashMap<LocalDate, Long>()
        val byDayOfWeek = HashMap<DayOfWeek, Long>()
        val byYear = HashMap<Int, Long>()
        val byArtistCount = HashMap<String, Int>()
        val albums = HashSet<String>()
        val songAggregates = HashMap<String, Pair<Long, Int>>()

        var totalMs = 0L
        for (ev in scoped) {
            totalMs += ev.durationMs
            val date = Instant.ofEpochMilli(ev.startMillis()).atZone(zoneId).toLocalDate()
            monthly[(date.monthValue - 1).coerceIn(0, 11)] += ev.durationMs
            byDate[date] = (byDate[date] ?: 0L) + ev.durationMs
            byDayOfWeek[date.dayOfWeek] = (byDayOfWeek[date.dayOfWeek] ?: 0L) + ev.durationMs

            val catalogSong = songById[ev.songId]
            val artist = ev.artistName?.takeIf { it.isNotBlank() } ?: catalogSong?.artist.orEmpty()
            val album = ev.albumName?.takeIf { it.isNotBlank() } ?: catalogSong?.album.orEmpty()
            val yearTag = catalogSong?.year ?: 0
            if (yearTag in PLAUSIBLE_YEARS) {
                byYear[yearTag] = (byYear[yearTag] ?: 0L) + ev.durationMs
            }
            if (artist.isNotBlank()) byArtistCount[artist] = (byArtistCount[artist] ?: 0) + 1
            if (album.isNotBlank()) albums.add("$artist|$album")

            val prev = songAggregates[ev.songId] ?: (0L to 0)
            songAggregates[ev.songId] = (prev.first + ev.durationMs) to (prev.second + 1)
        }

        val hourly = hourlyDurations(scoped, zoneId)
        val personality = classifyPersonality(hourly)
        val favDow = byDayOfWeek.maxByOrNull { it.value }?.key
        val peakDate = byDate.maxByOrNull { it.value }
        val topArtistEntry = byArtistCount.maxByOrNull { it.value }

        val topSongs = songAggregates.entries
            .sortedByDescending { it.value.first }
            .take(5)
            .map { (songId, agg) ->
                val match = songById[songId]
                val latestEvent = scoped.lastOrNull { it.songId == songId }
                PlaybackStatsRepository.SongPlaybackSummary(
                    songId = songId,
                    title = match?.title ?: latestEvent?.songTitle ?: "Unknown",
                    artist = match?.artist ?: latestEvent?.artistName ?: "Unknown",
                    albumArtUri = match?.artworkUri?.toString(),
                    totalDurationMs = agg.first,
                    playCount = agg.second
                )
            }

        return ListeningRecap(
            year = year,
            isYearComplete = bounds.isYearComplete,
            totalListenedMs = totalMs,
            totalPlays = scoped.size,
            activeDays = byDate.size,
            daysInRange = bounds.daysInRange,
            uniqueArtists = byArtistCount.size,
            uniqueAlbums = albums.size,
            monthlyDurationsMs = monthly.toList(),
            hourlyDurationsMs = hourly.toList(),
            personality = personality,
            favoriteDayOfWeek = favDow,
            biggestDay = peakDate?.key,
            biggestDayDurationMs = peakDate?.value ?: 0L,
            topArtist = topArtistEntry?.key,
            topArtistPlayCount = topArtistEntry?.value ?: 0,
            topSongs = topSongs,
            topDecade = dominantDecade(byYear, totalMs)
        )
    }
}
