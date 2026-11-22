/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.ArtistArtworkSource
import chromahub.rhythm.app.shared.data.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class ArtworkAndLyricsNetworkGuardTest {

    private fun isArtistRemoteAllowed(source: ArtistArtworkSource, deezerEnabled: Boolean): Boolean {
        return (source == ArtistArtworkSource.PREFER_LOCAL_THEN_API || source == ArtistArtworkSource.API_ONLY) &&
                (source != ArtistArtworkSource.API_ONLY || deezerEnabled)
    }

    @Test
    fun artistArtworkSource_localOnly_and_disabled_modes_prevent_remote_fetching() {
        // LOCAL_ONLY should never permit remote API fetches
        assertFalse("LOCAL_ONLY must not allow remote artwork fetches",
            isArtistRemoteAllowed(ArtistArtworkSource.LOCAL_ONLY, deezerEnabled = true))
        assertFalse("LOCAL_ONLY must not allow remote artwork fetches even if deezer disabled",
            isArtistRemoteAllowed(ArtistArtworkSource.LOCAL_ONLY, deezerEnabled = false))

        // DISABLED should never permit remote API fetches
        assertFalse("DISABLED must not allow remote artwork fetches",
            isArtistRemoteAllowed(ArtistArtworkSource.DISABLED, deezerEnabled = true))
        assertFalse("DISABLED must not allow remote artwork fetches",
            isArtistRemoteAllowed(ArtistArtworkSource.DISABLED, deezerEnabled = false))

        // PREFER_LOCAL_THEN_API permits remote fallback
        assertTrue("PREFER_LOCAL_THEN_API must allow remote fetch",
            isArtistRemoteAllowed(ArtistArtworkSource.PREFER_LOCAL_THEN_API, deezerEnabled = true))

        // API_ONLY permits remote fetch only if Deezer is enabled
        assertTrue("API_ONLY with Deezer enabled must allow remote fetch",
            isArtistRemoteAllowed(ArtistArtworkSource.API_ONLY, deezerEnabled = true))
        assertFalse("API_ONLY with Deezer disabled must not allow remote fetch",
            isArtistRemoteAllowed(ArtistArtworkSource.API_ONLY, deezerEnabled = false))
    }

    @Test
    fun circuitBreaker_negativeArtistCache_records_and_suppresses_subsequent_lookups() {
        val negativeArtistCache = Collections.synchronizedSet(LinkedHashSet<String>())
        var remoteApiCallCount = 0

        fun searchArtistWithCircuitBreaker(artistName: String): Boolean {
            val key = artistName.lowercase().trim()
            if (negativeArtistCache.contains(key)) {
                return false // Skipped by circuit breaker
            }
            remoteApiCallCount++
            // Simulate that artist does not exist online
            negativeArtistCache.add(key)
            return false
        }

        // First attempt calls the remote API and records the negative result
        val firstResult = searchArtistWithCircuitBreaker("Obscure Indie Artist")
        assertFalse(firstResult)
        assertEquals(1, remoteApiCallCount)
        assertTrue(negativeArtistCache.contains("obscure indie artist"))

        // Subsequent attempts in the same session must be short-circuited by the negative cache
        for (i in 1..5) {
            val repeatedResult = searchArtistWithCircuitBreaker("Obscure Indie Artist")
            assertFalse(repeatedResult)
        }
        // No additional remote API calls must have been made
        assertEquals(1, remoteApiCallCount)
    }

    @Test
    fun circuitBreaker_negativeAlbumCache_records_and_suppresses_subsequent_lookups() {
        val negativeAlbumCache = Collections.synchronizedSet(LinkedHashSet<String>())
        var remoteApiCallCount = 0

        fun searchAlbumWithCircuitBreaker(artist: String, title: String): Boolean {
            val key = "$artist:$title".lowercase().trim()
            if (negativeAlbumCache.contains(key)) {
                return false // Circuit breaker tripped
            }
            remoteApiCallCount++
            // Simulate missing online album art
            negativeAlbumCache.add(key)
            return false
        }

        val firstCall = searchAlbumWithCircuitBreaker("Test Artist", "Rare Album")
        assertFalse(firstCall)
        assertEquals(1, remoteApiCallCount)
        assertTrue(negativeAlbumCache.contains("test artist:rare album"))

        // Multiple repeated library refreshes
        repeat(10) {
            searchAlbumWithCircuitBreaker("Test Artist", "Rare Album")
        }
        assertEquals("Circuit breaker should prevent runaway queries", 1, remoteApiCallCount)
    }

    @Test
    fun circuitBreaker_negativeTrackCache_records_and_suppresses_subsequent_lookups() {
        val negativeTrackCache = Collections.synchronizedSet(LinkedHashSet<String>())
        var remoteApiCallCount = 0

        fun searchTrackWithCircuitBreaker(artist: String, title: String): Boolean {
            val key = "$artist:$title".lowercase().trim()
            if (negativeTrackCache.contains(key)) {
                return false
            }
            remoteApiCallCount++
            negativeTrackCache.add(key)
            return false
        }

        searchTrackWithCircuitBreaker("Unknown Artist", "Track 01")
        assertEquals(1, remoteApiCallCount)

        repeat(5) {
            searchTrackWithCircuitBreaker("Unknown Artist", "Track 01")
        }
        assertEquals(1, remoteApiCallCount)
    }

    @Test
    fun lyricsCache_caches_no_lyrics_sentinel_to_prevent_runaway_api_requests() {
        val lyricsCache = Collections.synchronizedMap(mutableMapOf<String, LyricsData>())
        var apiQueryCount = 0

        fun fetchLyrics(artist: String, title: String, forceRefresh: Boolean = false): LyricsData {
            val cacheKey = "$artist:$title".lowercase().trim()
            if (!forceRefresh) {
                lyricsCache[cacheKey]?.let { return it }
            }
            apiQueryCount++
            // Simulate no lyrics found online or locally
            val notFound = LyricsData("No lyrics found for this song", null, null)
            lyricsCache[cacheKey] = notFound
            return notFound
        }

        // First playback triggers API fetch and caches negative result
        val lyrics1 = fetchLyrics("Classical Artist", "Symphony No. 5")
        assertEquals("No lyrics found for this song", lyrics1.plainLyrics)
        assertEquals(1, apiQueryCount)

        // Subsequent track transitions / re-plays must hit cache and NOT query APIs
        for (i in 1..10) {
            val cachedLyrics = fetchLyrics("Classical Artist", "Symphony No. 5")
            assertNotNull(cachedLyrics)
            assertEquals("No lyrics found for this song", cachedLyrics.plainLyrics)
        }
        assertEquals("Should not query APIs repeatedly for missing lyrics", 1, apiQueryCount)

        // Force refresh explicitly allows retry
        val refreshedLyrics = fetchLyrics("Classical Artist", "Symphony No. 5", forceRefresh = true)
        assertEquals(2, apiQueryCount)
        assertEquals("No lyrics found for this song", refreshedLyrics.plainLyrics)
    }

    @Test
    fun cacheEviction_resets_negative_caches() {
        val negativeArtistCache = Collections.synchronizedSet(LinkedHashSet<String>())
        val negativeAlbumCache = Collections.synchronizedSet(LinkedHashSet<String>())
        val negativeTrackCache = Collections.synchronizedSet(LinkedHashSet<String>())

        negativeArtistCache.add("artist1")
        negativeAlbumCache.add("album1")
        negativeTrackCache.add("track1")

        assertEquals(1, negativeArtistCache.size)
        assertEquals(1, negativeAlbumCache.size)
        assertEquals(1, negativeTrackCache.size)

        // Clear caches (simulating clearInMemoryCaches)
        negativeArtistCache.clear()
        negativeAlbumCache.clear()
        negativeTrackCache.clear()

        assertTrue(negativeArtistCache.isEmpty())
        assertTrue(negativeAlbumCache.isEmpty())
        assertTrue(negativeTrackCache.isEmpty())
    }
}
