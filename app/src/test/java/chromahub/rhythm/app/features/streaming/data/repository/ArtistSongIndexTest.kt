package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.util.ArtistSeparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSongIndexTest {

    private val split: (String) -> List<String> = {
        ArtistSeparator.splitArtistNames(it, delimiters = "&;/", enabled = true)
    }

    private fun song(id: String, artist: String) = StreamingSong(
        id = id,
        title = "t$id",
        artist = artist,
        album = "album",
        duration = 0L,
        artworkUri = null,
        sourceType = SourceType.SUBSONIC,
        streamingUrl = null,
        previewUrl = null
    )

    private val songs = listOf(
        song("1", "Billy Joel"),
        song("2", "billy joel"),
        song("3", "Billy Joel & Elton John"),
        song("4", "Billy Idol"),
        song("5", "Elton John"),
        song("1", "Billy Joel"), // duplicate id, e.g. present in both the flow and the cache
        song("6", ""),
        song("7", "Joel")
    )

    /** The per-artist scan the index replaces (StreamingMusicRepositoryImpl.cachedSongsForArtist). */
    private fun linearScan(artistName: String): List<StreamingSong> {
        if (artistName.isBlank()) return emptyList()
        return songs.asSequence()
            .filter { song ->
                song.artist.contains(artistName, ignoreCase = true) &&
                    split(song.artist).let { names ->
                        names.isEmpty() || names.any { it.equals(artistName, ignoreCase = true) }
                    }
            }
            .distinctBy { it.id }
            .toList()
    }

    @Test
    fun matchesTheLinearScanForEveryArtist() {
        val index = ArtistSongIndex(songs, split)
        listOf("Billy Joel", "BILLY JOEL", "Elton John", "Billy Idol", "Joel", "Billy", "Nobody", "", "  ")
            .forEach { name ->
                assertEquals("artist '$name'", linearScan(name).map { it.id }, index.songsFor(name).map { it.id })
            }
    }

    @Test
    fun collaborationsAreCreditedToEachArtist() {
        val index = ArtistSongIndex(songs, split)
        assertEquals(listOf("1", "2", "3"), index.songsFor("Billy Joel").map { it.id })
        assertEquals(listOf("3", "5"), index.songsFor("elton john").map { it.id })
    }

    @Test
    fun substringsOfAnArtistNameDoNotMatch() {
        val index = ArtistSongIndex(songs, split)
        assertEquals(listOf("7"), index.songsFor("Joel").map { it.id })
        assertTrue(index.songsFor("Billy").isEmpty())
    }
}
