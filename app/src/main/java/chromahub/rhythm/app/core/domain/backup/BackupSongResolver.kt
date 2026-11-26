/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import java.io.File
import java.text.Normalizer
import kotlin.math.abs

/**
 * High-speed 4-tier song matcher for device migrations, backup restores, and playlist imports:
 * 1. Exact identity / URI (`id`, `uri`, or cloud ID passthrough)
 * 2. File path / relative path / trailing path segments / filename / extensionless name
 * 3. Normalized metadata (`title` + `artist`, preferring matching `album`)
 * 4. Relaxed title/filename + duration tolerance (`±2500ms`)
 */
object BackupSongResolver {

    private const val DURATION_TOLERANCE_MS = 2500L
    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}\\s]")
    private val MULTISPACE = Regex("\\s+")

    data class ResolutionQuery(
        val id: String? = null,
        val uri: String? = null,
        val filePath: String? = null,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
        val baseDirectory: String? = null
    )

    data class ResolutionStats(
        val matchedCount: Int,
        val unresolvedCount: Int
    )

    class SongIndex(
        val songs: List<Song>,
        extraFilePaths: Map<String, Song> = emptyMap()
    ) {
        private val byId = HashMap<String, Song>(songs.size)
        private val byUri = HashMap<String, Song>(songs.size)
        private val byExactPath = HashMap<String, Song>(songs.size + extraFilePaths.size)
        private val byLowerPath = HashMap<String, Song>(songs.size + extraFilePaths.size)
        private val byTwoSegments = HashMap<String, MutableList<Song>>(songs.size)
        private val byFileName = HashMap<String, MutableList<Song>>(songs.size)
        private val byFileNameNoExt = HashMap<String, MutableList<Song>>(songs.size)
        private val byNormFileNameNoExt = HashMap<String, MutableList<Song>>(songs.size)
        private val byNormTitleArtist = HashMap<String, MutableList<Song>>(songs.size)
        private val byNormTitle = HashMap<String, MutableList<Song>>(songs.size)

        init {
            extraFilePaths.forEach { (path, song) ->
                indexPath(path, song)
            }
            songs.forEach { song ->
                if (song.id.isNotBlank()) byId[song.id] = song
                val uriStr = song.uri.toString()
                if (uriStr.isNotBlank()) byUri[uriStr] = song
                song.path?.takeIf { it.isNotBlank() }?.let { indexPath(it, song) }
                if (uriStr.startsWith("file://")) {
                    indexPath(uriStr.removePrefix("file://"), song)
                }

                val normTitle = normalizeText(song.title)
                val normArtist = normalizeText(song.artist)
                if (normTitle.isNotEmpty()) {
                    byNormTitle.getOrPut(normTitle) { mutableListOf() }.add(song)
                    if (normArtist.isNotEmpty()) {
                        byNormTitleArtist.getOrPut("$normTitle|$normArtist") { mutableListOf() }.add(song)
                    }
                }
            }
        }

        private fun indexPath(rawPath: String, song: Song) {
            val normalized = rawPath.replace('\\', '/').trim()
            if (normalized.isEmpty()) return
            byExactPath.putIfAbsent(normalized, song)
            byLowerPath.putIfAbsent(normalized.lowercase(), song)

            val segments = normalized.split('/').filter { it.isNotBlank() }
            if (segments.size >= 2) {
                val twoSeg = "${segments[segments.size - 2].lowercase()}/${segments.last().lowercase()}"
                byTwoSegments.getOrPut(twoSeg) { mutableListOf() }.add(song)
            }

            val fileName = segments.lastOrNull()?.lowercase() ?: return
            byFileName.getOrPut(fileName) { mutableListOf() }.add(song)
            val noExt = fileName.substringBeforeLast('.')
            if (noExt.isNotBlank()) {
                byFileNameNoExt.getOrPut(noExt) { mutableListOf() }.add(song)
                val normNoExt = normalizeText(
                    noExt.replace('_', ' ').replace("%20", " ").replace('-', ' ')
                )
                if (normNoExt.isNotBlank()) {
                    byNormFileNameNoExt.getOrPut(normNoExt) { mutableListOf() }.add(song)
                }
            }
        }

        fun resolve(query: ResolutionQuery): Song? {
            query.id?.takeIf { it.isNotBlank() }?.let { id ->
                byId[id]?.let { return it }
            }
            query.uri?.takeIf { it.isNotBlank() }?.let { uriStr ->
                byUri[uriStr]?.let { return it }
            }

            val pathCandidates = buildList {
                query.filePath?.takeIf { it.isNotBlank() }?.let { add(it) }
                query.uri?.takeIf { it.startsWith("file://") }?.let { add(it.removePrefix("file://")) }
                if (query.filePath == null && query.uri != null &&
                    !query.uri.startsWith("content://") &&
                    !query.uri.startsWith("streaming://") &&
                    !query.uri.startsWith("http://") &&
                    !query.uri.startsWith("https://")
                ) {
                    add(query.uri)
                }
            }

            for (rawPath in pathCandidates) {
                val clean = rawPath.replace('\\', '/').trim()
                byExactPath[clean]?.let { return it }
                byLowerPath[clean.lowercase()]?.let { return it }

                if (!clean.startsWith("/") && !query.baseDirectory.isNullOrBlank()) {
                    val resolved = runCatching {
                        File(query.baseDirectory, clean).canonicalPath.replace('\\', '/')
                    }.getOrNull()
                    if (resolved != null) {
                        byExactPath[resolved]?.let { return it }
                        byLowerPath[resolved.lowercase()]?.let { return it }
                    }
                }

                if (clean.startsWith("/") && !clean.startsWith("/storage")) {
                    for (prefix in listOf("/storage/emulated/0", "/storage/sdcard0")) {
                        val absPath = (prefix + clean).lowercase()
                        byLowerPath[absPath]?.let { return it }
                    }
                }

                val segments = clean.split('/').filter { it.isNotBlank() }
                if (segments.size >= 2) {
                    val twoSeg = "${segments[segments.size - 2].lowercase()}/${segments.last().lowercase()}"
                    byTwoSegments[twoSeg]?.let { candidates ->
                        pickBestCandidate(candidates, query)?.let { return it }
                    }
                }

                val fileName = segments.lastOrNull()?.lowercase().orEmpty()
                if (fileName.isNotBlank()) {
                    byFileName[fileName]?.let { candidates ->
                        pickBestCandidate(candidates, query)?.let { return it }
                    }
                    val noExt = fileName.substringBeforeLast('.')
                    if (noExt.isNotBlank()) {
                        byFileNameNoExt[noExt]?.let { candidates ->
                            pickBestCandidate(candidates, query)?.let { return it }
                        }
                        val normNoExt = normalizeText(
                            noExt.replace('_', ' ').replace("%20", " ").replace('-', ' ')
                        )
                        if (normNoExt.isNotBlank()) {
                            byNormFileNameNoExt[normNoExt]?.let { candidates ->
                                pickBestCandidate(candidates, query)?.let { return it }
                            }
                        }
                    }
                }
            }

            val normTitle = normalizeText(query.title)
            val normArtist = normalizeText(query.artist)
            if (normTitle.isNotEmpty() && normArtist.isNotEmpty()) {
                byNormTitleArtist["$normTitle|$normArtist"]?.let { candidates ->
                    pickBestCandidate(candidates, query)?.let { return it }
                }
            }

            if (query.title.contains(" - ")) {
                val left = query.title.substringBefore(" - ")
                val right = query.title.substringAfter(" - ")
                val nLeft = normalizeText(left)
                val nRight = normalizeText(right)
                if (nLeft.isNotEmpty() && nRight.isNotEmpty()) {
                    byNormTitleArtist["$nRight|$nLeft"]?.let { candidates ->
                        pickBestCandidate(candidates, query)?.let { return it }
                    }
                    byNormTitleArtist["$nLeft|$nRight"]?.let { candidates ->
                        pickBestCandidate(candidates, query)?.let { return it }
                    }
                }
            }

            if (normTitle.isNotEmpty()) {
                byNormTitle[normTitle]?.let { candidates ->
                    if (query.durationMs > 0L) {
                        candidates.firstOrNull { candidate ->
                            candidate.duration > 0L && abs(candidate.duration - query.durationMs) <= DURATION_TOLERANCE_MS
                        }?.let { return it }
                    }
                    if (candidates.size == 1 && query.artist.isBlank()) {
                        return candidates.first()
                    }
                    pickBestCandidate(candidates, query)?.let { return it }
                }

                if (query.durationMs > 0L && pathCandidates.isNotEmpty()) {
                    val fileStem = normalizeText(
                        pathCandidates.first().substringAfterLast('/').substringBeforeLast('.')
                            .replace('_', ' ').replace('-', ' ')
                    )
                    if (fileStem.isNotEmpty()) {
                        songs.firstOrNull { candidate ->
                            candidate.duration > 0L &&
                                abs(candidate.duration - query.durationMs) <= DURATION_TOLERANCE_MS &&
                                normalizeText(candidate.title).let { ct ->
                                    ct.isNotEmpty() && (fileStem.contains(ct) || ct.contains(fileStem))
                                }
                        }?.let { return it }
                    }
                }
            }

            return null
        }

        private fun pickBestCandidate(candidates: List<Song>, query: ResolutionQuery): Song? {
            if (candidates.isEmpty()) return null
            if (candidates.size == 1) return candidates.first()

            val normAlbum = normalizeText(query.album)
            val normArtist = normalizeText(query.artist)
            return candidates.maxByOrNull { song ->
                var score = 0
                if (normArtist.isNotEmpty() && normalizeText(song.artist) == normArtist) score += 4
                if (normAlbum.isNotEmpty() && normalizeText(song.album) == normAlbum) score += 3
                if (query.durationMs > 0L && song.duration > 0L &&
                    abs(song.duration - query.durationMs) <= DURATION_TOLERANCE_MS
                ) {
                    score += 2
                }
                score
            }
        }
    }

    fun isStreamingTrack(song: Song): Boolean {
        return song.id.startsWith("subsonic::") ||
            song.id.startsWith("jellyfin::") ||
            song.uri.toString().startsWith("streaming://")
    }

    fun resolvePlaylists(
        playlists: List<Playlist>,
        availableSongs: List<Song>,
        keepUnresolvedStreaming: Boolean = true
    ): Pair<List<Playlist>, ResolutionStats> {
        if (playlists.isEmpty()) return emptyList<Playlist>() to ResolutionStats(0, 0)
        if (availableSongs.isEmpty()) {
            val total = playlists.sumOf { it.songs.size }
            return playlists to ResolutionStats(matchedCount = 0, unresolvedCount = total)
        }

        val index = SongIndex(availableSongs)
        var matched = 0
        var unresolved = 0

        val resolvedPlaylists = playlists.map { playlist ->
            val mappedSongs = playlist.songs.mapNotNull { song ->
                val hit = index.resolve(
                    ResolutionQuery(
                        id = song.id,
                        uri = song.uri.toString(),
                        filePath = song.path,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationMs = song.duration
                    )
                )
                if (hit != null) {
                    matched++
                    hit
                } else if (keepUnresolvedStreaming && isStreamingTrack(song)) {
                    song
                } else {
                    unresolved++
                    // Keep valid existing URI songs rather than silently destroying playlist entries on partial scans
                    if (song.uri.toString().isNotBlank() && song.title.isNotBlank()) song else null
                }
            }
            playlist.copy(songs = mappedSongs)
        }

        return resolvedPlaylists to ResolutionStats(matchedCount = matched, unresolvedCount = unresolved)
    }

    internal fun normalizeText(input: String): String {
        if (input.isBlank()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
        val withoutMarks = COMBINING_MARKS.replace(normalized, "")
        val alnum = NON_ALNUM.replace(withoutMarks.lowercase(), " ")
        return MULTISPACE.replace(alnum, " ").trim()
    }
}
