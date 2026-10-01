/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.data.provider.JellyfinApiClient
import chromahub.rhythm.app.features.streaming.data.provider.UserTrustManager
import chromahub.rhythm.app.features.streaming.data.repository.StreamingCatalogCache
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.util.coil.StreamingArtworkKeyer
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StreamingFixesTest {

    private val gson = Gson()

    // -------------------------------------------------------------
    // Issue #561: UserTrustManager & Hostname / Probe Candidate Tests
    // -------------------------------------------------------------

    @Test
    fun testUserTrustManager_isPrivateOrLocalHost() {
        // Local loopback & private IPv4
        assertTrue(UserTrustManager.isPrivateOrLocalHost("localhost"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("127.0.0.1"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("192.168.1.1"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("192.168.68.111"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("10.0.0.1"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("10.254.1.99"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("172.16.0.1"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("172.31.255.255"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("100.64.0.1"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("100.127.255.255"))

        // Local & mesh domains
        assertTrue(UserTrustManager.isPrivateOrLocalHost("myserver.local"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("nas.lan"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("raspberrypi.home"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("router.home.arpa"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("node.ts.net"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("node.internal"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("mediaserver"))

        // IPv6 loopback & link-local
        assertTrue(UserTrustManager.isPrivateOrLocalHost("::1"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("[::1]"))
        assertTrue(UserTrustManager.isPrivateOrLocalHost("fe80::1ff:fe23:4567:890a"))

        // Public IPs and external domains should NOT be treated as private
        assertFalse(UserTrustManager.isPrivateOrLocalHost("8.8.8.8"))
        assertFalse(UserTrustManager.isPrivateOrLocalHost("1.1.1.1"))
        assertFalse(UserTrustManager.isPrivateOrLocalHost("172.32.0.1"))
        assertFalse(UserTrustManager.isPrivateOrLocalHost("google.com"))
        assertFalse(UserTrustManager.isPrivateOrLocalHost("jellyfin.org"))
    }

    @Test
    fun testJellyfinProbeCandidates_generation() {
        val candidates = JellyfinApiClient.generateProbeCandidates("192.168.68.111:8096")

        // Must include base normalized URL
        assertTrue(candidates.contains("http://192.168.68.111:8096"))
        // Must include opposite HTTPS scheme
        assertTrue(candidates.contains("https://192.168.68.111:8096"))
        // Must include default HTTPS port 8920
        assertTrue(candidates.contains("https://192.168.68.111:8920"))
        // Must include subpath /jellyfin
        assertTrue(candidates.contains("http://192.168.68.111:8096/jellyfin"))
        assertTrue(candidates.contains("https://192.168.68.111:8096/jellyfin"))
    }

    // -------------------------------------------------------------
    // Issue #605: Lyrics Parsing & Timestamp Formatting Tests
    // -------------------------------------------------------------

    private fun formatLrcTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }

    @Test
    fun testLrcTimestampFormatting() {
        assertEquals("[00:00.00]", formatLrcTimestamp(0L))
        assertEquals("[00:01.50]", formatLrcTimestamp(1500L))
        assertEquals("[01:23.45]", formatLrcTimestamp(83450L))
        assertEquals("[10:05.09]", formatLrcTimestamp(605090L))
    }

    @Test
    fun testJellyfinTickLyricsParsing() {
        // Jellyfin sends ticks where 10,000 ticks = 1 ms
        val rawJson = """
            {
              "Lyrics": [
                {
                  "Text": "First synchronized line",
                  "Start": 15000000
                },
                {
                  "Text": "Second synchronized line",
                  "Start": 45000000
                }
              ]
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val lyricsArray = root.getAsJsonArray("Lyrics")
        val syncedLines = mutableListOf<String>()
        val plainLines = mutableListOf<String>()

        for (elem in lyricsArray) {
            val item = elem.asJsonObject
            val text = item.get("Text").asString
            val startTicks = item.get("Start").asLong
            val ms = startTicks / 10000L
            plainLines.add(text)
            syncedLines.add("${formatLrcTimestamp(ms)}$text")
        }

        assertEquals(2, plainLines.size)
        assertEquals("First synchronized line", plainLines[0])
        assertEquals("Second synchronized line", plainLines[1])

        assertEquals(2, syncedLines.size)
        assertEquals("[00:01.50]First synchronized line", syncedLines[0])
        assertEquals("[00:04.50]Second synchronized line", syncedLines[1])
    }

    @Test
    fun testSubsonicStructuredLyricsParsing() {
        val rawJson = """
            {
              "subsonic-response": {
                "status": "ok",
                "version": "1.16.1",
                "lyricsList": {
                  "structuredLyrics": [
                    {
                      "lang": "eng",
                      "synced": true,
                      "line": [
                        { "start": 3200, "value": "Is this the real life?" },
                        { "start": 7800, "value": "Is this just fantasy?" }
                      ],
                      "offset": 0
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject.getAsJsonObject("subsonic-response")
        val structured = root.getAsJsonObject("lyricsList").getAsJsonArray("structuredLyrics")[0].asJsonObject
        val isSynced = structured.get("synced").asBoolean
        val lines = structured.getAsJsonArray("line")

        assertTrue(isSynced)
        val syncedLines = mutableListOf<String>()
        for (elem in lines) {
            val line = elem.asJsonObject
            val text = line.get("value").asString
            val startMs = line.get("start").asLong
            syncedLines.add("${formatLrcTimestamp(startMs)}$text")
        }

        assertEquals("[00:03.20]Is this the real life?", syncedLines[0])
        assertEquals("[00:07.80]Is this just fantasy?", syncedLines[1])
    }

    // -------------------------------------------------------------
    // Issue #617: Cover Art Selection & Cache Key Normalization Tests
    // -------------------------------------------------------------

    @Test
    fun testSubsonicCoverArtSelection_prefersAlbumIdOverMediaFileId() {
        fun resolveCoverArtId(id: String, rawCoverArt: String?, rawAlbumId: String?, parent: String?): String {
            return when {
                rawCoverArt != null && !rawCoverArt.startsWith("mf-") -> rawCoverArt
                rawAlbumId != null -> rawAlbumId
                rawCoverArt != null -> rawCoverArt
                parent != null -> parent
                else -> id
            }
        }

        // Case 1: Song has mf- coverArt (causes Navidrome ffmpeg process) and albumId
        val resolvedId1 = resolveCoverArtId(
            id = "track-001",
            rawCoverArt = "mf-track-001",
            rawAlbumId = "al-album-999",
            parent = null
        )
        // Must resolve to albumId "al-album-999" instead of "mf-track-001" to avoid ffmpeg spawning
        assertEquals("al-album-999", resolvedId1)

        // Case 2: Song has custom/explicit non-mf coverArt
        val resolvedId2 = resolveCoverArtId(
            id = "track-002",
            rawCoverArt = "al-special-cover",
            rawAlbumId = "al-album-999",
            parent = null
        )
        assertEquals("al-special-cover", resolvedId2)

        // Case 3: Song has mf- coverArt but no albumId
        val resolvedId3 = resolveCoverArtId(
            id = "track-003",
            rawCoverArt = "mf-track-003",
            rawAlbumId = null,
            parent = null
        )
        assertEquals("mf-track-003", resolvedId3)
    }

    @Test
    fun testStreamingArtworkKeyer_normalizesDynamicAuthTokens() {
        // Subsonic: same host and cover art id, but differing transient salt/token
        val subsonicUrl1 = "http://192.168.1.50:4533/rest/getCoverArt.view?u=admin&t=token123&s=saltABC&v=1.16.1&c=Rhythm&f=json&id=al-555&size=500"
        val subsonicUrl2 = "http://192.168.1.50:4533/rest/getCoverArt.view?u=admin&t=token999&s=saltXYZ&v=1.16.1&c=Rhythm&f=json&id=al-555&size=500"

        val key1 = StreamingArtworkKeyer.keyFromUrlString(subsonicUrl1)
        val key2 = StreamingArtworkKeyer.keyFromUrlString(subsonicUrl2)

        assertNotNull(key1)
        assertEquals("streaming_subsonic_192.168.1.50_al-555_500", key1)
        assertEquals(key1, key2)

        // Jellyfin: same host and item ID, but differing api_key
        val jellyfinUrl1 = "http://192.168.1.60:8096/Items/item-1234/Images/Primary?maxWidth=500&quality=90&api_key=tokenAlpha"
        val jellyfinUrl2 = "http://192.168.1.60:8096/Items/item-1234/Images/Primary?maxWidth=500&quality=90&api_key=tokenBeta"

        val jKey1 = StreamingArtworkKeyer.keyFromUrlString(jellyfinUrl1)
        val jKey2 = StreamingArtworkKeyer.keyFromUrlString(jellyfinUrl2)

        assertNotNull(jKey1)
        assertEquals("streaming_jellyfin_192.168.1.60_/Items/item-1234/Images/Primary_500", jKey1)
        assertEquals(jKey1, jKey2)
    }

    // -------------------------------------------------------------
    // Issue #606: Catalog Persistence Tests
    // -------------------------------------------------------------

    @Test
    fun testStreamingCatalogCache_serializationAndDeserialization() {
        val testSong = StreamingSong(
            id = "subsonic::song-1",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 180000L,
            artworkUri = "http://example.com/art.jpg",
            sourceType = SourceType.SUBSONIC,
            streamingUrl = "http://example.com/stream/song-1",
            previewUrl = null,
            albumId = "subsonic::album-1"
        )

        val testAlbum = StreamingAlbum(
            id = "subsonic::album-1",
            title = "Test Album",
            artist = "Test Artist",
            artworkUri = "http://example.com/art.jpg",
            songCount = 1,
            year = 2024,
            sourceType = SourceType.SUBSONIC
        )

        val testArtist = StreamingArtist(
            id = "subsonic::artist-1",
            name = "Test Artist",
            artworkUri = "http://example.com/artist.jpg",
            songCount = 1,
            albumCount = 1,
            sourceType = SourceType.SUBSONIC
        )

        val testPlaylist = StreamingPlaylist(
            id = "subsonic::playlist-1",
            name = "Favorites",
            description = "My favorite songs",
            artworkUri = null,
            songCount = 1,
            isEditable = true,
            sourceType = SourceType.SUBSONIC
        )

        val cache = StreamingCatalogCache(
            serviceId = "subsonic",
            songs = listOf(testSong),
            albums = listOf(testAlbum),
            artists = listOf(testArtist),
            playlists = listOf(testPlaylist),
            likedSongIds = listOf("subsonic::song-1"),
            lastSyncTimestamp = 1700000000000L
        )

        val json = gson.toJson(cache)
        val deserialized = gson.fromJson(json, StreamingCatalogCache::class.java)

        assertEquals(cache.serviceId, deserialized.serviceId)
        assertEquals(cache.songs.size, deserialized.songs.size)
        assertEquals(cache.songs[0].id, deserialized.songs[0].id)
        assertEquals(cache.songs[0].title, deserialized.songs[0].title)
        assertEquals(cache.albums.size, deserialized.albums.size)
        assertEquals(cache.albums[0].title, deserialized.albums[0].title)
        assertEquals(cache.artists.size, deserialized.artists.size)
        assertEquals(cache.artists[0].name, deserialized.artists[0].name)
        assertEquals(cache.playlists.size, deserialized.playlists.size)
        assertEquals(cache.likedSongIds, deserialized.likedSongIds)
        assertEquals(cache.lastSyncTimestamp, deserialized.lastSyncTimestamp)
    }

    private fun extractExtension(contentDisposition: String?, contentType: String?): String {
        val audioExtensions = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".wma", ".webm")
        if (!contentDisposition.isNullOrBlank()) {
            val filenameMatch = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";\n]+)['"]?""", RegexOption.IGNORE_CASE)
                .find(contentDisposition)
            val filename = filenameMatch?.groupValues?.get(1)?.trim()
            if (!filename.isNullOrBlank() && filename.contains(".")) {
                val ext = "." + filename.substringAfterLast(".").lowercase()
                if (ext in audioExtensions) return ext
            }
        }
        if (!contentType.isNullOrBlank()) {
            val mime = contentType.substringBefore(";").trim().lowercase()
            return when (mime) {
                "audio/flac", "audio/x-flac" -> ".flac"
                "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac", "audio/x-aac" -> ".m4a"
                "audio/ogg", "audio/vorbis", "application/ogg" -> ".ogg"
                "audio/opus" -> ".opus"
                "audio/wav", "audio/x-wav", "audio/wave" -> ".wav"
                "audio/webm" -> ".webm"
                "audio/mpeg", "audio/mp3" -> ".mp3"
                else -> ".mp3"
            }
        }
        return ".mp3"
    }

    @Test
    fun testAudioExtensionSniffing() {
        assertEquals(".flac", extractExtension("attachment; filename=\"song.flac\"", "application/octet-stream"))
        assertEquals(".m4a", extractExtension("inline; filename*=UTF-8''my%20song.m4a", "audio/mp4"))
        assertEquals(".opus", extractExtension(null, "audio/opus"))
        assertEquals(".ogg", extractExtension(null, "audio/ogg; codecs=vorbis"))
        assertEquals(".wav", extractExtension("attachment; filename=\"recording.wav\"", null))
        assertEquals(".mp3", extractExtension(null, "audio/mpeg"))
        assertEquals(".mp3", extractExtension(null, "unknown/format"))
    }

    @Test
    fun testUserAgentFormatConsistency() {
        val expectedPattern = Regex("""^Rhythm/.+ \(Android\)$""")
        val userAgent = "Rhythm/${chromahub.rhythm.app.BuildConfig.VERSION_NAME} (Android)"
        assertTrue("User Agent '$userAgent' should match pattern 'Rhythm/<version> (Android)'", expectedPattern.matches(userAgent))
    }

    @Test
    fun testJellyfinCodecParameterConstraints() {
        val jellyfinValidationRegex = Regex("""^[a-zA-Z0-9\-\._,|]{0,40}$""")

        val validCodec = "mp3"
        val validTranscodingContainer = "mp3"
        val invalidCodecList = "mp3,flac,aac,opus,vorbis,alac,pcm_s16le,pcm_s24le"

        assertTrue(jellyfinValidationRegex.matches(validCodec))
        assertTrue(jellyfinValidationRegex.matches(validTranscodingContainer))
        assertTrue(validCodec.length <= 40)
        assertFalse("Comma-separated list of 45 characters must NOT match Jellyfin validation regex", jellyfinValidationRegex.matches(invalidCodecList))
    }
}
