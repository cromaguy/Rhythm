/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming

import chromahub.rhythm.app.features.streaming.data.provider.SubsonicApiClient.LibraryChangeState
import chromahub.rhythm.app.features.streaming.data.repository.StreamingCatalogCache
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubsonicLibraryMarkerTest {

    private val gson = Gson()

    @Test
    fun catalogMarker_isStableWhileLibraryUnchanged() {
        val first = LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false).catalogMarker("account")
        val second = LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false).catalogMarker("account")

        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun catalogMarker_changesWithLibraryOrAccount() {
        val base = LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false)

        assertNotEquals(base.catalogMarker("account"), base.copy(lastModified = 1_700_000_000_001L).catalogMarker("account"))
        assertNotEquals(base.catalogMarker("account"), base.catalogMarker("other-account"))
    }

    @Test
    fun catalogMarker_isNullWhenServerCannotVouchForLibrary() {
        // Scan in progress: the library is in flux.
        assertNull(LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = true).catalogMarker("account"))
        // Server did not report lastModified.
        assertNull(LibraryChangeState(lastModified = 0L, scanning = false).catalogMarker("account"))
        // Not logged in.
        assertNull(LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false).catalogMarker(null))
    }

    @Test
    fun catalogCache_withoutMarkerLoadsAsUnmarked() {
        // Caches written before the marker existed must load and never count as current.
        val json = """{"serviceId":"subsonic","songs":[],"lastSyncTimestamp":123}"""
        val cache = gson.fromJson(json, StreamingCatalogCache::class.java)

        assertEquals("subsonic", cache.serviceId)
        assertNull(cache.libraryMarker)
    }

    @Test
    fun catalogCache_roundTripsMarker() {
        val cache = StreamingCatalogCache(serviceId = "subsonic", libraryMarker = "account:1700000000000")
        val restored = gson.fromJson(gson.toJson(cache), StreamingCatalogCache::class.java)

        assertEquals("account:1700000000000", restored.libraryMarker)
    }
}
