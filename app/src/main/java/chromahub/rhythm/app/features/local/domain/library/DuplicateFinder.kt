/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.domain.library

import chromahub.rhythm.app.shared.data.model.Song

/**
 * Pure, side-effect-free duplicate-track detector and audio-quality library analyzer.
 *
 * Groups tracks sharing normalized `(title, artist)` whose durations fall within
 * [DEFAULT_DURATION_TOLERANCE_MS] of each other (so live recordings or remixes of
 * substantially different lengths remain distinct) and ranks copies by fidelity
 * (Lossless > Hi-Res sample rate > Bitrate) so users can safely keep the best copy.
 */
object DuplicateFinder {

    const val DEFAULT_DURATION_TOLERANCE_MS = 3_000L

    data class DuplicateGroup(
        val title: String,
        val artist: String,
        val songs: List<Song>,
        val recommendedKeepId: String
    )

    private val whitespace = Regex("\\s+")

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(whitespace, " ")

    fun findDuplicates(
        songs: List<Song>,
        durationToleranceMs: Long = DEFAULT_DURATION_TOLERANCE_MS
    ): List<DuplicateGroup> {
        val result = mutableListOf<DuplicateGroup>()

        songs
            .groupBy { normalize(it.title) to normalize(it.artist) }
            .forEach { (_, sameNamed) ->
                if (sameNamed.size < 2) return@forEach

                val sorted = sameNamed.sortedBy { it.duration }
                var cluster = mutableListOf(sorted.first())
                for (i in 1 until sorted.size) {
                    val song = sorted[i]
                    if (song.duration - cluster.first().duration <= durationToleranceMs) {
                        cluster.add(song)
                    } else {
                        result.addClusterIfDuplicate(cluster)
                        cluster = mutableListOf(song)
                    }
                }
                result.addClusterIfDuplicate(cluster)
            }

        return result.sortedWith(
            compareByDescending<DuplicateGroup> { it.songs.size }
                .thenBy { it.title.lowercase() }
        )
    }

    internal fun qualityRankScore(song: Song): Long {
        val losslessBonus = if (LibraryAudioStats.isLosslessSong(song)) 1_000_000_000L else 0L
        val sampleRateBonus = (song.sampleRate ?: 0).toLong() * 1_000L
        val bitrateBonus = (song.bitrate ?: 0).toLong()
        return losslessBonus + sampleRateBonus + bitrateBonus
    }

    private fun MutableList<DuplicateGroup>.addClusterIfDuplicate(cluster: List<Song>) {
        if (cluster.size > 1) {
            val ranked = cluster.sortedByDescending(::qualityRankScore)
            val head = ranked.first()
            add(
                DuplicateGroup(
                    title = head.title,
                    artist = head.artist,
                    songs = ranked,
                    recommendedKeepId = head.id
                )
            )
        }
    }
}

/**
 * Pure audio quality census across a library of [Song] items.
 */
object LibraryAudioStats {

    data class Breakdown(
        val totalTracks: Int,
        val losslessTracks: Int,
        val hiResTracks: Int,
        val surroundTracks: Int,
        val lossyTracks: Int,
        val topCodecs: List<Pair<String, Int>>
    ) {
        val losslessPercent: Float
            get() = if (totalTracks > 0) (losslessTracks * 100f / totalTracks) else 0f

        val hiResPercent: Float
            get() = if (totalTracks > 0) (hiResTracks * 100f / totalTracks) else 0f
    }

    private val losslessCodecs = setOf(
        "flac", "alac", "wav", "pcm", "aiff", "aif", "ape", "wv", "wavpack", "dsd", "dsf", "dff", "tta"
    )

    fun isLosslessSong(song: Song): Boolean {
        val codecLower = song.codec?.trim()?.lowercase().orEmpty()
        if (losslessCodecs.any { codecLower.contains(it) }) return true
        val ext = song.path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in losslessCodecs
    }

    fun isHiResSong(song: Song): Boolean {
        val sr = song.sampleRate ?: 0
        if (sr >= 88_200) return true
        val codecLower = song.codec?.trim()?.lowercase().orEmpty()
        return codecLower.contains("dsd") || codecLower.contains("24-bit") || codecLower.contains("24bit")
    }

    fun computeBreakdown(songs: List<Song>): Breakdown {
        if (songs.isEmpty()) {
            return Breakdown(0, 0, 0, 0, 0, emptyList())
        }
        var lossless = 0
        var hiRes = 0
        var surround = 0
        val codecCounts = HashMap<String, Int>()

        for (song in songs) {
            val isLossless = isLosslessSong(song)
            if (isLossless) lossless++
            if (isHiResSong(song)) hiRes++
            if ((song.channels ?: 2) > 2) surround++
            val label = song.codec?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                ?: song.path?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.length in 2..6 }
                ?: "OTHER"
            codecCounts[label] = (codecCounts[label] ?: 0) + 1
        }

        return Breakdown(
            totalTracks = songs.size,
            losslessTracks = lossless,
            hiResTracks = hiRes,
            surroundTracks = surround,
            lossyTracks = (songs.size - lossless).coerceAtLeast(0),
            topCodecs = codecCounts.entries
                .sortedByDescending { it.value }
                .take(4)
                .map { it.key to it.value }
        )
    }
}
