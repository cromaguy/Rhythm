package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong

/**
 * Songs grouped by each (case-insensitive) artist name they credit, built once per batch.
 *
 * Mapping a provider artist list used to scan the whole song cache for every artist
 * (O(artists x songs) substring checks); with a few hundred artists and a few thousand cached
 * songs that took ~2 s per sync on the main thread. Looking artists up in this index is O(1).
 *
 * A song matches an artist when one of the names produced by [splitArtistNames] equals the
 * artist name ignoring case, which is the rule the per-artist scan applied. Songs are
 * de-duplicated by id, keeping the first occurrence, and keep their input order.
 */
internal class ArtistSongIndex(
    songs: List<StreamingSong>,
    splitArtistNames: (String) -> List<String>
) {
    private val songsByArtist: Map<String, List<StreamingSong>>

    init {
        val index = HashMap<String, MutableList<StreamingSong>>()
        val seenIds = HashSet<String>()
        for (song in songs) {
            if (song.artist.isBlank() || !seenIds.add(song.id)) continue
            splitArtistNames(song.artist)
                .map { it.lowercase() }
                .distinct()
                .forEach { name -> index.getOrPut(name) { mutableListOf() }.add(song) }
        }
        songsByArtist = index
    }

    fun songsFor(artistName: String): List<StreamingSong> {
        if (artistName.isBlank()) return emptyList()
        return songsByArtist[artistName.lowercase()].orEmpty()
    }
}
