/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import chromahub.rhythm.app.features.local.data.database.FtsQuerySanitizer
import chromahub.rhythm.app.features.local.data.database.dao.ArtistDao
import chromahub.rhythm.app.features.local.data.database.dao.SongDao
import chromahub.rhythm.app.features.local.data.database.entity.toArtist
import chromahub.rhythm.app.features.local.data.database.entity.toSong
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Pure and Room-backed query helpers split from [MusicRepositoryImpl] for clean pagination,
 * FTS4 search delegation, and deterministic folder/album-artist grouping keys.
 */
class LibraryQueryDelegate(
    private val songDao: SongDao,
    private val artistDao: ArtistDao
) {
    fun getPaginatedSongs(): Flow<PagingData<Song>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = true),
        pagingSourceFactory = { songDao.getSongsPagingSource() }
    ).flow.map { pagingData -> pagingData.map { it.toSong() } }

    fun getPaginatedSongsSearch(query: String): Flow<PagingData<Song>> {
        val trimmedQuery = query.trim()
        val ftsQuery = FtsQuerySanitizer.buildPrefixMatchQuery(trimmedQuery)
        return Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = true),
            pagingSourceFactory = {
                when {
                    trimmedQuery.isEmpty() -> songDao.getSongsPagingSource()
                    ftsQuery.isNotEmpty() -> songDao.getSongsPagingSourceFtsSearch(ftsQuery)
                    else -> songDao.getSongsPagingSourceSearch(trimmedQuery)
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toSong() } }
    }

    fun getPaginatedArtists(groupByAlbumArtist: Boolean): Flow<PagingData<Artist>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = true),
        pagingSourceFactory = { artistDao.getArtistsPagingSource(groupByAlbumArtist) }
    ).flow.map { pagingData -> pagingData.map { it.toArtist() } }

    companion object {
        private val PRIMARY_ARTIST_REGEX = Regex("""(?i)\s+(feat\.|ft\.|featuring|with|and|&)\s+.*""")
        private val DISC_FOLDER_REGEX = Regex("""/(?:cd|disc|disk)\s*\d+$""", RegexOption.IGNORE_CASE)
        private val GENERIC_DIRECTORY_NAMES = setOf(
            "download", "downloads", "music", "audio", "media",
            "bluetooth", "internal storage", "sdcard", "0", "emulated"
        )

        fun getRootAlbumFolder(path: String?): String? {
            if (path.isNullOrBlank()) return null
            val parent = path.substringBeforeLast('/', "")
            if (parent.isBlank()) return null
            val normalized = DISC_FOLDER_REGEX.replace(parent, "")
            return normalized.lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
        }

        fun isGenericRootDirectory(folderPath: String): Boolean {
            val trimmed = folderPath.trimEnd('/').lowercase(Locale.ROOT)
            if (trimmed in setOf("/storage/emulated/0", "/storage/emulated", "/storage", "/sdcard", "/mnt/sdcard")) {
                return true
            }
            for (prefix in listOf("/storage/emulated/0/", "/sdcard/", "/mnt/sdcard/")) {
                if (trimmed.startsWith(prefix)) {
                    val rel = trimmed.removePrefix(prefix)
                    if (!rel.contains('/') && rel in GENERIC_DIRECTORY_NAMES) return true
                }
            }
            if (trimmed.startsWith("/storage/")) {
                val parts = trimmed.removePrefix("/storage/").split('/')
                if (parts.size == 1) return true
                if (parts.size == 2 && parts[1] in GENERIC_DIRECTORY_NAMES) return true
            }
            return false
        }

        fun extractPrimaryArtist(artistStr: String): String {
            val trimmed = artistStr.trim()
            if (trimmed.isBlank() || trimmed.equals("<unknown>", ignoreCase = true)) return "Unknown Artist"
            val main = trimmed.replace(PRIMARY_ARTIST_REGEX, "").trim()
            return if (main.isNotBlank()) main else trimmed
        }

        fun getAlbumGroupingKey(song: Song): String {
            val normAlbum = song.album.trim().lowercase(Locale.ROOT).ifBlank { "unknown album" }
            val rootAlbumFolder = getRootAlbumFolder(song.path)
            val isGeneric = rootAlbumFolder != null && isGenericRootDirectory(rootAlbumFolder)
            if (rootAlbumFolder != null && !isGeneric) {
                return "folder_${rootAlbumFolder}|${normAlbum}"
            }
            val normAlbumArtist = song.albumArtist?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                ?.lowercase(Locale.ROOT)
            if (normAlbumArtist != null) {
                return "albumartist_${normAlbumArtist}|${normAlbum}"
            }
            val primaryArtist = extractPrimaryArtist(song.artist).lowercase(Locale.ROOT)
            if (primaryArtist.isNotBlank() && primaryArtist != "unknown artist") {
                return "artist_${primaryArtist}|${normAlbum}"
            }
            val normAlbumId = song.albumId.trim().takeIf { it.isNotBlank() && it != "0" && it != "-1" }
            if (normAlbumId != null && normAlbumId.all { it.isDigit() }) {
                return "mediastore_${normAlbumId}|${normAlbum}"
            }
            if (rootAlbumFolder != null) {
                return "folder_${rootAlbumFolder}|${normAlbum}"
            }
            return "artist_${primaryArtist}|${normAlbum}"
        }
    }
}
