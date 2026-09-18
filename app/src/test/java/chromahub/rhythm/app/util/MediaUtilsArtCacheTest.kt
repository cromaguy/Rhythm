/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MediaUtilsArtCacheTest {

    @Before
    @After
    fun cleanup() {
        MediaUtils.clearRawArtworkCache()
    }

    @Test
    fun testUniqueByteAccounting() {
        val art1 = ByteArray(1024) { 1 }
        val art2 = ByteArray(2048) { 2 }

        MediaUtils.putRawArtworkForTesting("key1", art1)
        assertEquals(1, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(1024L, MediaUtils.getRawArtworkCacheBytesForTesting())

        MediaUtils.putRawArtworkForTesting("key2", art2)
        assertEquals(2, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(3072L, MediaUtils.getRawArtworkCacheBytesForTesting())

        MediaUtils.clearRawArtworkCache()
        assertEquals(0, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(0L, MediaUtils.getRawArtworkCacheBytesForTesting())
    }

    @Test
    fun testSharedByteArrayAccountingDoesNotLeak() {
        // A single shared byte array (e.g. folder cover shared across 3 songs)
        val sharedCover = ByteArray(4096) { 7 }

        MediaUtils.putRawArtworkForTesting("song1", sharedCover)
        assertEquals(1, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(4096L, MediaUtils.getRawArtworkCacheBytesForTesting())

        // Adding second and third reference to the same instance should NOT duplicate byte count
        MediaUtils.putRawArtworkForTesting("song2", sharedCover)
        assertEquals(2, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(4096L, MediaUtils.getRawArtworkCacheBytesForTesting())

        MediaUtils.putRawArtworkForTesting("song3", sharedCover)
        assertEquals(3, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(4096L, MediaUtils.getRawArtworkCacheBytesForTesting())

        // Overwriting song1 with a new byte array
        val newArt = ByteArray(1024) { 8 }
        MediaUtils.putRawArtworkForTesting("song1", newArt)
        assertEquals(3, MediaUtils.getRawArtworkCacheSizeForTesting())
        // sharedCover still referenced by song2 and song3 (4096) + newArt (1024)
        assertEquals(5120L, MediaUtils.getRawArtworkCacheBytesForTesting())

        // Clearing drops everything back to zero
        MediaUtils.clearRawArtworkCache()
        assertEquals(0, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(0L, MediaUtils.getRawArtworkCacheBytesForTesting())
    }

    @Test
    fun testEmptySentinelDoesNotLeakBytes() {
        val empty = ByteArray(0)
        MediaUtils.putRawArtworkForTesting("song_no_art_1", empty)
        MediaUtils.putRawArtworkForTesting("song_no_art_2", empty)

        assertEquals(2, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(0L, MediaUtils.getRawArtworkCacheBytesForTesting())

        MediaUtils.clearRawArtworkCache()
        assertEquals(0, MediaUtils.getRawArtworkCacheSizeForTesting())
        assertEquals(0L, MediaUtils.getRawArtworkCacheBytesForTesting())
    }
}
