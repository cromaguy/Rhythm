/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network

import chromahub.rhythm.app.infrastructure.service.player.CacheStoragePolicy
import chromahub.rhythm.app.util.NaturalSortComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkCacheAndSortPolicyTest {

    @Test
    fun ipv4FirstDnsResolver_prioritizesIpv4BeforeIpv6PreservingOrder() {
        val v6a = InetAddress.getByName("2001:db8::1")
        val v4a = InetAddress.getByName("192.168.1.10")
        val v6b = InetAddress.getByName("2001:db8::2")
        val v4b = InetAddress.getByName("10.0.0.5")

        val sorted = Ipv4FirstDnsResolver.sortAddresses(listOf(v6a, v4a, v6b, v4b))
        assertEquals(listOf(v4a, v4b, v6a, v6b), sorted)
    }

    @Test
    fun cacheStoragePolicy_scalesDownOnTightStorage() {
        val plenty = CacheStoragePolicy.computeMaxCacheBytes(
            usableSpaceBytes = 10L * 1024L * 1024L * 1024L,
            configuredMaxBytes = CacheStoragePolicy.DEFAULT_MAX_CACHE_BYTES
        )
        assertEquals(CacheStoragePolicy.DEFAULT_MAX_CACHE_BYTES, plenty)

        val tight = CacheStoragePolicy.computeMaxCacheBytes(
            usableSpaceBytes = 500L * 1024L * 1024L,
            configuredMaxBytes = CacheStoragePolicy.DEFAULT_MAX_CACHE_BYTES
        )
        assertTrue(tight in (24L * 1024L * 1024L)..CacheStoragePolicy.MIN_FLOOR_CACHE_BYTES)
    }

    @Test
    fun naturalSortComparator_sortsNumericsAndLeadingArticlesCleanly() {
        val tracks = listOf("Track 10", "Track 2", "Track 1", "Track 02")
        val sorted = tracks.sortedWith(NaturalSortComparator.comparator { it })
        assertEquals(listOf("Track 1", "Track 2", "Track 02", "Track 10"), sorted)

        val artists = listOf("The Beatles", "A Flock of Seagulls", "Bach")
        val sortedArtists = artists.sortedWith(
            NaturalSortComparator.comparator(stripLeadingArticles = true) { it }
        )
        assertEquals(listOf("Bach", "The Beatles", "A Flock of Seagulls"), sortedArtists)
    }

    @Test
    fun ipv4FirstDnsResolver_parsesDohJsonAndSkipsLocalOrLiteralHosts() {
        assertFalse(Ipv4FirstDnsResolver.shouldUseDohFallback("192.168.1.50"))
        assertFalse(Ipv4FirstDnsResolver.shouldUseDohFallback("nas.local"))
        assertFalse(Ipv4FirstDnsResolver.shouldUseDohFallback("navidrome"))
        assertTrue(Ipv4FirstDnsResolver.shouldUseDohFallback("music.example.org"))

        val payload = """
            {
              "Status": 0,
              "Answer": [
                {"name": "music.example.org", "type": 28, "data": "2001:db8::42"},
                {"name": "music.example.org", "type": 1, "data": "203.0.113.10"}
              ]
            }
        """.trimIndent()
        val parsed = Ipv4FirstDnsResolver.parseDnsJsonResponse(payload)
        assertEquals(2, parsed.size)
        assertEquals(InetAddress.getByName("203.0.113.10"), parsed[0])
        assertEquals(InetAddress.getByName("2001:db8::42"), parsed[1])
    }
}
