/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.scan

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves and persists stable dateAdded timestamps for songs across file moves,
 * synchronization tool updates (e.g. Syncthing), metadata retagging, and MediaStore re-indexing.
 */
class StableDateAddedResolver(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        private const val TAG = "StableDateAddedResolver"
        const val PREFS_NAME = "song_date_added_cache"

        const val PREFIX_PATH = "date_added_"
        const val PREFIX_META_FULL = "date_added_mf_"
        const val PREFIX_META_CORE = "date_added_mc_"
        const val PREFIX_REL_PATH = "date_added_rp_"
        const val PREFIX_FILE_NAME = "date_added_fn_"

        private val GENERIC_FILENAME_PREFIXES = listOf(
            "track", "audiotrack", "sound", "voice", "recording",
            "untitled", "audio", "unknown", "music", "file", "song",
            "cd", "disc", "disk"
        )

        fun normalizeTimestamp(timestamp: Long): Long {
            val ms = when {
                timestamp <= 0L -> 0L
                timestamp in 1..99_999_999_999L -> timestamp * 1000L
                timestamp in 100_000_000_000L..99_999_999_999_999L -> timestamp
                timestamp in 100_000_000_000_000L..99_999_999_999_999_999L -> timestamp / 1000L
                else -> timestamp / 1_000_000L
            }
            if (ms <= 0L) return 0L
            val maxAllowed = System.currentTimeMillis() + 86_400_000L
            return if (ms > maxAllowed) System.currentTimeMillis() else ms
        }

        fun normalizePath(path: String?): String {
            if (path.isNullOrBlank()) return ""
            return path.trim()
                .replace('\\', '/')
                .trimEnd('/')
                .lowercase(Locale.ROOT)
        }

        fun normalizeMetadata(value: String?): String {
            if (value.isNullOrBlank()) return ""
            return value.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("""\s+"""), " ")
        }

        fun isUnknownTitle(title: String?): Boolean {
            val norm = title?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return norm.isBlank() ||
                norm == "<unknown>" ||
                norm == "unknown" ||
                norm == "unknown title" ||
                norm == "untitled" ||
                norm == "-"
        }

        fun isUnknownArtist(artist: String?): Boolean {
            val norm = artist?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return norm.isBlank() ||
                norm == "<unknown>" ||
                norm == "unknown" ||
                norm == "unknown artist" ||
                norm == "-" ||
                norm == "artist"
        }

        fun isGenericAudioFilename(filename: String): Boolean {
            val base = filename.substringBeforeLast('.').trim().lowercase(Locale.ROOT)
            if (base.length <= 2) return true
            val stripped = base.filterNot { it == '-' || it == '_' || it == ' ' }
            if (stripped.isEmpty() || stripped.all { it.isDigit() }) return true
            for (prefix in GENERIC_FILENAME_PREFIXES) {
                if (base == prefix) return true
                if (base.startsWith(prefix)) {
                    val remainder = base.substring(prefix.length).filterNot { it == '-' || it == '_' || it == ' ' }
                    if (remainder.isEmpty() || remainder.all { it.isDigit() }) return true
                }
            }
            return false
        }

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            return buildString(digest.size * 2) {
                digest.forEach { byte ->
                    append(((byte.toInt() ushr 4) and 0xF).toString(16))
                    append((byte.toInt() and 0xF).toString(16))
                }
            }
        }

        fun pathKey(normPath: String): String {
            return PREFIX_PATH + sha256(normPath)
        }

        fun metaFullKey(normTitle: String, normArtist: String, normAlbum: String, durSec: Long): String? {
            if (normTitle.isBlank() || isUnknownTitle(normTitle) || durSec <= 0L) return null
            return PREFIX_META_FULL + sha256("$normTitle|$normArtist|$normAlbum|$durSec")
        }

        fun metaCoreKey(normTitle: String, normArtist: String, durSec: Long): String? {
            if (normTitle.isBlank() || isUnknownTitle(normTitle) || normArtist.isBlank() || isUnknownArtist(normArtist) || durSec <= 0L) return null
            return PREFIX_META_CORE + sha256("$normTitle|$normArtist|$durSec")
        }

        fun relPathKey(normPath: String, durSec: Long): String? {
            val segments = normPath.split('/').filter { it.isNotBlank() }
            if (segments.size < 2 || durSec <= 0L) return null
            val lastTwo = segments.takeLast(2).joinToString("/")
            return PREFIX_REL_PATH + sha256("$lastTwo|$durSec")
        }

        fun fileNameKey(normPath: String, durSec: Long): String? {
            val fileName = normPath.substringAfterLast('/')
            if (fileName.isBlank() || durSec <= 0L || isGenericAudioFilename(fileName)) return null
            return PREFIX_FILE_NAME + sha256("$fileName|$durSec")
        }
    }

    private val pendingWrites = ConcurrentHashMap<String, Long>()

    private val existingById = ConcurrentHashMap<String, SongEntity>()
    private val existingByPath = ConcurrentHashMap<String, SongEntity>()

    private val candidatesByMetaFull = ConcurrentHashMap<String, MutableList<SongEntity>>()
    private val candidatesByMetaCore = ConcurrentHashMap<String, MutableList<SongEntity>>()
    private val candidatesByRelPath = ConcurrentHashMap<String, MutableList<SongEntity>>()
    private val candidatesByFileName = ConcurrentHashMap<String, MutableList<SongEntity>>()
    private val claimedSongIds = ConcurrentHashMap.newKeySet<String>()

    fun prepareForScan(existingSongs: List<SongEntity>) {
        existingById.clear()
        existingByPath.clear()
        candidatesByMetaFull.clear()
        candidatesByMetaCore.clear()
        candidatesByRelPath.clear()
        candidatesByFileName.clear()
        claimedSongIds.clear()

        for (song in existingSongs) {
            existingById[song.id] = song
            val normPath = normalizePath(song.path)
            if (normPath.isNotBlank()) {
                existingByPath[normPath] = song
            }

            val validDateAdded = normalizeTimestamp(song.dateAdded)
            if (validDateAdded > 0L) {
                val durSec = song.duration / 1000L
                val normTitle = normalizeMetadata(song.title)
                val normArtist = normalizeMetadata(song.artist)
                val normAlbum = normalizeMetadata(song.album)

                metaFullKey(normTitle, normArtist, normAlbum, durSec)?.let { key ->
                    candidatesByMetaFull.getOrPut(key) { mutableListOf() }.add(song)
                }
                metaCoreKey(normTitle, normArtist, durSec)?.let { key ->
                    candidatesByMetaCore.getOrPut(key) { mutableListOf() }.add(song)
                }
                if (normPath.isNotBlank()) {
                    relPathKey(normPath, durSec)?.let { key ->
                        candidatesByRelPath.getOrPut(key) { mutableListOf() }.add(song)
                    }
                    fileNameKey(normPath, durSec)?.let { key ->
                        candidatesByFileName.getOrPut(key) { mutableListOf() }.add(song)
                    }
                }
            }
        }
    }

    fun findExistingByPath(normPath: String): SongEntity? {
        if (normPath.isBlank()) return null
        return existingByPath[normPath]
    }

    fun claimExistingSong(songId: String) {
        claimedSongIds.add(songId)
    }

    fun getCachedDate(key: String): Long {
        val fromPending = pendingWrites[key]
        if (fromPending != null && fromPending > 0L) {
            return normalizeTimestamp(fromPending)
        }
        val raw = prefs.getLong(key, -1L)
        return if (raw > 0L) normalizeTimestamp(raw) else -1L
    }

    fun resolveDateAdded(
        songId: String? = null,
        filePath: String? = null,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0L,
        observedDateAddedMs: Long = 0L
    ): Long {
        val normalizedObserved = normalizeTimestamp(observedDateAddedMs)
        val normPath = normalizePath(filePath)
        val normTitle = normalizeMetadata(title)
        val normArtist = normalizeMetadata(artist)
        val normAlbum = normalizeMetadata(album)
        val durSec = durationMs / 1000L

        val candidateDates = mutableListOf<Long>()

        if (!songId.isNullOrBlank()) {
            existingById[songId]?.let { existing ->
                val date = normalizeTimestamp(existing.dateAdded)
                if (date > 0L) candidateDates.add(date)
                claimExistingSong(existing.id)
            }
        }

        if (normPath.isNotBlank()) {
            existingByPath[normPath]?.let { existing ->
                val date = normalizeTimestamp(existing.dateAdded)
                if (date > 0L) candidateDates.add(date)
                claimExistingSong(existing.id)
            }
        }

        if (candidateDates.isEmpty()) {
            val relocatedCandidate = findRelocatedCandidate(normTitle, normArtist, normAlbum, normPath, durSec)
            if (relocatedCandidate != null) {
                val date = normalizeTimestamp(relocatedCandidate.dateAdded)
                if (date > 0L) {
                    candidateDates.add(date)
                    Log.d(TAG, "Matched relocated song '${relocatedCandidate.title}' to '$normPath' (dateAdded=$date)")
                }
            }
        }

        if (normPath.isNotBlank()) {
            val pathCached = getCachedDate(pathKey(normPath))
            if (pathCached > 0L) candidateDates.add(pathCached)

            if (durSec > 0L) {
                relPathKey(normPath, durSec)?.let { key ->
                    val date = getCachedDate(key)
                    if (date > 0L) candidateDates.add(date)
                }
                fileNameKey(normPath, durSec)?.let { key ->
                    val date = getCachedDate(key)
                    if (date > 0L) candidateDates.add(date)
                }
            }
        }

        if (normTitle.isNotBlank() && durSec > 0L) {
            metaFullKey(normTitle, normArtist, normAlbum, durSec)?.let { key ->
                val date = getCachedDate(key)
                if (date > 0L) candidateDates.add(date)
            }
            metaCoreKey(normTitle, normArtist, durSec)?.let { key ->
                val date = getCachedDate(key)
                if (date > 0L) candidateDates.add(date)
            }
        }

        val validHistoricalDates = candidateDates.filter { it > 0L }
        val historicalDate = if (validHistoricalDates.isNotEmpty()) validHistoricalDates.minOrNull() ?: 0L else 0L

        val finalDate = when {
            historicalDate > 0L && normalizedObserved > 0L -> minOf(historicalDate, normalizedObserved)
            historicalDate > 0L -> historicalDate
            normalizedObserved > 0L -> normalizedObserved
            else -> System.currentTimeMillis()
        }

        recordResolvedDate(
            normPath = normPath,
            normTitle = normTitle,
            normArtist = normArtist,
            normAlbum = normAlbum,
            durSec = durSec,
            dateAddedMs = finalDate
        )

        return finalDate
    }

    private fun findRelocatedCandidate(
        normTitle: String,
        normArtist: String,
        normAlbum: String,
        normPath: String,
        durSec: Long
    ): SongEntity? {
        if (durSec <= 0L) return null

        metaFullKey(normTitle, normArtist, normAlbum, durSec)?.let { key ->
            candidatesByMetaFull[key]?.firstOrNull { claimedSongIds.add(it.id) }?.let { return it }
        }

        if (normPath.isNotBlank()) {
            relPathKey(normPath, durSec)?.let { key ->
                candidatesByRelPath[key]?.firstOrNull { claimedSongIds.add(it.id) }?.let { return it }
            }
        }

        metaCoreKey(normTitle, normArtist, durSec)?.let { key ->
            candidatesByMetaCore[key]?.firstOrNull { claimedSongIds.add(it.id) }?.let { return it }
        }

        if (normPath.isNotBlank()) {
            fileNameKey(normPath, durSec)?.let { key ->
                candidatesByFileName[key]?.firstOrNull { claimedSongIds.add(it.id) }?.let { return it }
            }
        }

        return null
    }

    fun recordResolvedDate(
        normPath: String,
        normTitle: String,
        normArtist: String,
        normAlbum: String,
        durSec: Long,
        dateAddedMs: Long
    ) {
        if (dateAddedMs <= 0L) return

        val updateKey: (String?) -> Unit = { key ->
            if (!key.isNullOrBlank()) {
                pendingWrites.compute(key) { _, current ->
                    val existing = current ?: getCachedDate(key)
                    if (existing <= 0L || dateAddedMs < existing) {
                        dateAddedMs
                    } else {
                        existing
                    }
                }
            }
        }

        if (normPath.isNotBlank()) {
            updateKey(pathKey(normPath))
            if (durSec > 0L) {
                updateKey(relPathKey(normPath, durSec))
                updateKey(fileNameKey(normPath, durSec))
            }
        }

        if (normTitle.isNotBlank() && durSec > 0L) {
            updateKey(metaFullKey(normTitle, normArtist, normAlbum, durSec))
            updateKey(metaCoreKey(normTitle, normArtist, durSec))
        }
    }

    fun flush() {
        if (pendingWrites.isEmpty()) return

        val updates = HashMap<String, Long>()
        updates.putAll(pendingWrites)
        pendingWrites.clear()

        if (updates.isEmpty()) return
        Log.d(TAG, "Flushing ${updates.size} stable date-added entries to SharedPreferences")
        try {
            prefs.edit {
                for ((key, value) in updates) {
                    putLong(key, value)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush date-added cache to SharedPreferences", e)
        }
    }
}
