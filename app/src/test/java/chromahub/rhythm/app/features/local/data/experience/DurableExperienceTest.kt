// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import chromahub.rhythm.app.util.ArtworkEnvelope
import chromahub.rhythm.app.shared.presentation.components.player.CoverGestures
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DurableExperienceTest {
    @Test fun normalizedTransitionGenresStillContributeAfterMaterialization() {
        val result = PersonalRecommendation.rank(listOf(RecommendationCandidate("song", "Artist", "Pop", 0)), emptyList(),
            ExperienceProfile(), context = RecommendationContext(genreTransitions = mapOf("pop" to 1.0))).single()
        assertEquals(1.0, result.features[6], .00001)
    }
    @Test fun nearbyArtworkKeysIgnoreDateHeadersAndKeepCompositeSongIds() {
        assertEquals("123", chromahub.rhythm.app.util.ArtworkPrefetch.logicalKey("datesong_123"))
        assertEquals("file_123", chromahub.rhythm.app.util.ArtworkPrefetch.logicalKey("song_file_123_content://media/audio/123"))
        assertEquals("album123", chromahub.rhythm.app.util.ArtworkPrefetch.logicalKey("album123"))
    }
    @Test fun artworkContainerPreservesEveryOriginalByteAndDetectsCorruption() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val original = File(root, "original.webp")
            original.writeBytes("RIFFxxxxWEBP".toByteArray() + ByteArray(80_000) { (it * 37).toByte() })
            val wrapped = File(root, "cover.t2art"); val restored = File(root, "restored.webp")
            ArtworkEnvelope.wrap(original, wrapped)
            assertFalse(ArtworkEnvelope.isImage(wrapped)); wrapped.inputStream().use(ArtworkEnvelope::verify)
            wrapped.inputStream().use { ArtworkEnvelope.unwrap(it, restored) }
            assertArrayEquals(original.readBytes(), restored.readBytes())
            val corrupt = wrapped.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertTrue(runCatching { ArtworkEnvelope.verify(corrupt.inputStream()) }.isFailure)
            assertTrue(runCatching { ArtworkEnvelope.verify(corrupt.copyOf(20).inputStream()) }.isFailure)
            assertArrayEquals(original.readBytes(), restored.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun inferredTransportRequiresSustainedEvidenceAndLeavesAmbiguityUnknown() {
        assertEquals("unknown", ContextSignals.motion(20f, 30.0, 0.0, 1, 0).kind)
        assertEquals("vehicle", ContextSignals.motion(20f, 30.0, 0.0, 30, 0).kind)
        assertEquals("airborne", ContextSignals.motion(180f, 6000.0, 0.0, 70, 0).kind)
        assertEquals("walking", ContextSignals.motion(null, null, .8, 0, 0).kind)
        assertTrue(ContextSignals.motion(4f, null, 0.0, 40, 0).confidence < .5)
        assertEquals("unknown", ContextSignals.motion(Float.NaN, null, 0.0, 100, 0).kind)
    }
    @Test fun solarContextHandlesNoonMidnightAndPolarNight() {
        fun time(value: String) = java.time.Instant.parse(value).toEpochMilli()
        assertTrue(ContextSignals.daylight(time("2026-03-20T12:00:00Z"), 0.0, 0.0).isDay)
        assertFalse(ContextSignals.daylight(time("2026-03-20T00:00:00Z"), 0.0, 0.0).isDay)
        assertFalse(ContextSignals.daylight(time("2026-12-21T12:00:00Z"), 85.0, 0.0).isDay)
        assertTrue(ContextSignals.daylight(time("2026-06-21T00:00:00Z"), 85.0, 0.0).isDay)
    }
    @Test fun recurringReviewIntervalsRemainBoundedAndBalancedForEveryItem() {
        val days = (0 until 1400).map { TasteMemory.interval("favourite", it) / 86_400_000.0 }
        assertTrue(days.all { it >= 7 && it < 21 })
        val buckets = days.groupingBy { ((it - 7) / 2).toInt() }.eachCount()
        assertEquals(7, buckets.size)
        assertTrue(buckets.values.max() - buckets.values.min() <= 4)
    }
    @Test fun centreLikeAndSideSeeksHaveNonOverlappingZonesAndClampToTrackBounds() {
        assertEquals(0, CoverGestures.zone(50f, 100f)); assertEquals(-1, CoverGestures.zone(10f, 100f)); assertEquals(1, CoverGestures.zone(90f, 100f))
        assertEquals(0f, CoverGestures.seek(.01f, 100_000, -1, 10), .0001f)
        assertEquals(1f, CoverGestures.seek(.99f, 100_000, 1, 30), .0001f)
        assertEquals(.5f, CoverGestures.seek(.5f, 0, 1, 10), .0001f)
    }
}
