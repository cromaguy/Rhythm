// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import java.time.Instant

enum class Provider(val label: String, val priority: Int) {
    BUGS_MOBILE("Bugs mobile", 100),
    BUGS_PC("Bugs PC", 95),
    GENIE("Genie", 75),
    MELON("Melon", 80),
}

data class Evidence<T>(
    val value: T,
    val provider: Provider,
    val pageUrl: String,
    val confidence: Double,
    val observedAt: Instant = Instant.now(),
)

data class ArtistMetadata(
    val id: String? = null,
    val name: Evidence<String>? = null,
    val description: Evidence<String>? = null,
    val profileImageUrl: Evidence<String>? = null,
    val debutDate: Evidence<String>? = null,
    val genres: Evidence<List<String>>? = null,
    val sourceUrl: String? = null,
)

data class AlbumMetadata(
    val id: String? = null,
    val title: Evidence<String>? = null,
    val artists: Evidence<List<String>>? = null,
    val imageUrl: Evidence<String>? = null,
    val releaseDate: Evidence<String>? = null,
    val genres: Evidence<List<String>>? = null,
    val sourceUrl: String? = null,
    val description: Evidence<String>? = null,
)

data class TrackMetadata(
    val id: String? = null,
    val title: Evidence<String>? = null,
    val artists: Evidence<List<String>>? = null,
    val lyrics: Evidence<String>? = null,
    val imageUrl: Evidence<String>? = null,
    val releaseDate: Evidence<String>? = null,
    val genres: Evidence<List<String>>? = null,
    val albumTitle: Evidence<String>? = null,
    val sourceUrl: String? = null,
    val durationMs: Evidence<Long>? = null,
)

fun String?.yearOrNull(): Int? {
    val text = this?.trim() ?: return null
    val compact = Regex("^(18|19|20|21)\\d{6}$").matchEntire(text)
    if (compact != null) return text.take(4).toIntOrNull()
    return Regex("(?<!\\d)(?:18|19|20|21)\\d{2}(?!\\d)").find(text)?.value?.toIntOrNull()
}

/** Merge only missing/weak values. The winning Evidence object retains its provenance. */
object MetadataMerger {
    private fun <T> choose(a: Evidence<T>, b: Evidence<T>): Evidence<T> = when {
        a.confidence > b.confidence -> a
        b.confidence > a.confidence -> b
        a.provider.priority >= b.provider.priority -> a
        else -> b
    }

    fun artists(items: List<ArtistMetadata>): ArtistMetadata = ArtistMetadata(
        id = items.firstNotNullOfOrNull { it.id },
        name = items.mapNotNull { it.name }.reduceOrNull(::choose),
        description = items.mapNotNull { it.description }.reduceOrNull(::choose),
        profileImageUrl = items.mapNotNull { it.profileImageUrl }.reduceOrNull(::choose),
        debutDate = items.mapNotNull { it.debutDate }.reduceOrNull(::choose),
        genres = items.mapNotNull { it.genres }.reduceOrNull(::choose),
        sourceUrl = items.firstNotNullOfOrNull { it.sourceUrl },
    )

    fun albums(items: List<AlbumMetadata>): AlbumMetadata = AlbumMetadata(
        id = items.firstNotNullOfOrNull { it.id },
        title = items.mapNotNull { it.title }.reduceOrNull(::choose),
        artists = items.mapNotNull { it.artists }.reduceOrNull(::choose),
        imageUrl = items.mapNotNull { it.imageUrl }.reduceOrNull(::choose),
        releaseDate = items.mapNotNull { it.releaseDate }.reduceOrNull(::choose),
        genres = items.mapNotNull { it.genres }.reduceOrNull(::choose),
        sourceUrl = items.firstNotNullOfOrNull { it.sourceUrl },
        description = items.mapNotNull { it.description }.reduceOrNull(::choose),
    )

    fun tracks(items: List<TrackMetadata>): TrackMetadata = TrackMetadata(
        id = items.firstNotNullOfOrNull { it.id },
        title = items.mapNotNull { it.title }.reduceOrNull(::choose),
        artists = items.mapNotNull { it.artists }.reduceOrNull(::choose),
        lyrics = items.mapNotNull { it.lyrics }.reduceOrNull(::choose),
        imageUrl = items.mapNotNull { it.imageUrl }.reduceOrNull(::choose),
        releaseDate = items.mapNotNull { it.releaseDate }.reduceOrNull(::choose),
        genres = items.mapNotNull { it.genres }.reduceOrNull(::choose),
        albumTitle = items.mapNotNull { it.albumTitle }.reduceOrNull(::choose),
        sourceUrl = items.firstNotNullOfOrNull { it.sourceUrl },
        durationMs = items.mapNotNull { it.durationMs }.reduceOrNull(::choose),
    )
}
