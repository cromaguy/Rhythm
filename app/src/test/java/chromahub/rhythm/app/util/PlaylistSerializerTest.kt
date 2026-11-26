/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSerializerTest {

    private val trackA = Song(
        id = "1",
        title = "Rock & Roll <Live>",
        artist = "Daft Punk",
        album = "Alive",
        duration = 215_000L,
        uri = TestUri("content", "/audio/1"),
        path = "/storage/emulated/0/Music/rock_and_roll.flac"
    )
    private val trackB = Song(
        id = "2",
        title = "Veridis Quo",
        artist = "Daft Punk",
        album = "Discovery",
        duration = 345_000L,
        uri = TestUri("content", "/audio/2"),
        path = "/storage/emulated/0/Music/veridis_quo.flac"
    )
    private val playlist = Playlist(id = "p1", name = "Synth & \"Funk\"", songs = listOf(trackA, trackB))

    @Test
    fun serializeAndParseAllPlaylistFormats_roundTripsCleanly() {
        val m3u8 = PlaylistSerializer.serializeM3u(playlist, extended = true)
        val parsedM3u8 = PlaylistSerializer.parseM3u(m3u8)
        assertEquals(2, parsedM3u8.entries.size)
        assertEquals(215_000L, parsedM3u8.entries[0].durationMs)

        val pls = PlaylistSerializer.serializePls(playlist)
        val parsedPls = PlaylistSerializer.parsePls(pls)
        assertEquals(2, parsedPls.entries.size)
        assertTrue(parsedPls.entries[1].location.endsWith("veridis_quo.flac"))

        val xspf = PlaylistSerializer.serializeXspf(playlist)
        val parsedXspf = PlaylistSerializer.parseXspf(xspf)
        assertEquals("Synth & \"Funk\"", parsedXspf.name)
        assertEquals("Rock & Roll <Live>", parsedXspf.entries[0].title)
        assertEquals("Discovery", parsedXspf.entries[1].album)

        val wpl = PlaylistSerializer.serializeWpl(playlist)
        val parsedWpl = PlaylistSerializer.parseWpl(wpl)
        assertEquals(2, parsedWpl.entries.size)
        assertEquals(
            listOf("1", "2"),
            PlaylistSerializer.resolveEntries(parsedWpl.entries, listOf(trackA, trackB)).map { it.id }
        )
    }

    @Test
    fun parseXspf_rejectsXxeDoctypePayloads() {
        val maliciousXspf = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <playlist version="1"><trackList><track><title>&xxe;</title></track></trackList></playlist>
        """.trimIndent()

        val threw = runCatching { PlaylistSerializer.parseXspf(maliciousXspf) }.isFailure
        assertTrue(threw)
    }
}
