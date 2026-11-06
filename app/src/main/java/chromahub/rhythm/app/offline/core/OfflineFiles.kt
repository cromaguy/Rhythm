// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File

/**
 * File naming shared by the downloads and the player's resolvers. It matches what Rhythm's
 * repository already does (`StreamingMusicRepositoryImpl.getDownloadFile`), so downloads made
 * before this feature keep resolving.
 */
object OfflineFiles {

    /** Original-file extensions the repository writes (its `audioExtensions`). */
    val AUDIO_EXTENSIONS = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".wma", ".webm")

    /** `SUBSONIC::abc` -> `SUBSONIC__abc`: a track id as a file name. */
    fun safeName(trackId: String): String = trackId.replace(":", "_").replace("/", "_").replace("\\", "_")

    /**
     * Names in the downloads dir that are partial downloads nothing will resume: a '.tx.part' (a server-side transcode
     * never survives an attempt) and a '.part' of a song that is no longer queued, running or failed-and-retryable
     * ([resumableIds] are the ledger ids of those). A stop leaves the '.part' of a job that will run again, on purpose:
     * the next run asks the server for the rest.
     */
    fun orphanedParts(fileNames: Collection<String>, resumableIds: Collection<String>): List<String> {
        val keep = resumableIds.mapTo(HashSet(), ::safeName)
        return fileNames.filter { name ->
            when {
                name.endsWith(".tx.part") -> true
                name.endsWith(".part") -> name.removeSuffix(".part") !in keep
                else -> false
            }
        }
    }

    private fun coverKey(text: String) = text.replace(":", "_").replace("/", "_").replace("\\", "_").replace(" ", "_").lowercase()

    /** The file an album's cover is cached in: by the album id, else artist and album (what the repository writes). */
    fun albumCoverName(albumId: String?, artist: String, album: String): String =
        coverKey(albumId ?: "${artist}_$album") + "_album_art.jpg"

    /** The file a song's own cover is cached in. */
    fun songCoverName(songId: String): String = safeName(songId) + "_art.jpg"

    /**
     * Cover files in the downloads dir that no downloaded song needs any more: an album's cover once its last song is
     * released, a song's own cover once the song is. [keepAlbumCovers] and [keepSongCovers] are the names the songs
     * still in the ledger would use (see [albumCoverName] and [songCoverName]).
     */
    fun orphanedCovers(fileNames: Collection<String>, keepAlbumCovers: Set<String>, keepSongCovers: Set<String>): List<String> =
        fileNames.filter { name ->
            when {
                name.endsWith("_album_art.jpg") -> name !in keepAlbumCovers
                name.endsWith("_art.jpg") -> name !in keepSongCovers
                else -> false
            }
        }

    /** `file:///...` for [file], the form Android's `Uri.fromFile` makes and `FileDataSource` opens. */
    fun fileUri(file: File): String {
        val u = file.toURI().toString()
        return if (u.startsWith("file:///")) u else u.replaceFirst("file:/", "file:///")
    }

    /** The downloaded original of [trackId] in [dir] as a `file://` URI, or null. */
    fun downloadedUri(dir: File, trackId: String): String? {
        val base = safeName(trackId)
        for (ext in AUDIO_EXTENSIONS) {
            val f = File(dir, base + ext)
            if (f.isFile && f.length() > 0) return fileUri(f)
        }
        return null
    }

    /**
     * The track id of a downloaded file's URI: [store] first (exact), then the id shape Rhythm's
     * Subsonic and Jellyfin ids have (`SUBSONIC::<id>` -> `SUBSONIC__<id>`). Null when [uri] is not
     * a file in [dir].
     */
    fun trackIdOfDownload(dir: File, uri: String, store: DownloadStateStore?): String? {
        val path = try {
            java.net.URI(uri).path ?: return null
        } catch (_: Exception) {
            return null
        }
        val file = File(path)
        val parent = file.parentFile ?: return null
        if (parent.canonicalPath != dir.canonicalPath) return null
        val base = file.name.substringBeforeLast('.', file.name)
        store?.songIdOfFile(base, ::safeName)?.let { return it }
        for (prefix in listOf("SUBSONIC", "JELLYFIN")) {
            if (base.startsWith(prefix + "__")) return prefix + "::" + base.removePrefix(prefix + "__")
        }
        return null
    }
}
