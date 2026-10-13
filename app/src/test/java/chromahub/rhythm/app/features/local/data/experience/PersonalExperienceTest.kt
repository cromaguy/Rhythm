// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PersonalExperienceTest {
    private fun record(id: String, key: String, start: Long, genre: String = "Pop", played: Long = 60_000,
                       playId: String = id, added: Long = 0) = ListeningRecord(id, playId, "segment", key, key, key, "Artist", "Album", genre,
        start, start + played, played, 60_000, 0, played, 1f, "completed", true, added, timezone = "UTC")
    @Test fun `pause segments belong to one play and gaps and repeats stay distinct`() {
        val start = 1_700_000_000_000L
        val rows = listOf(record("1", "a", start, played = 20_000, playId = "play1"),
            record("2", "a", start + 30_000, played = 40_000, playId = "play1"), record("3", "a", start + 90_000),
            record("4", "b", start + 180_000, "Rock"), record("5", "a", start + 86_400_000))
        val analysis = ListeningInsightAnalysis.analyze(rows)
        assertEquals(4, analysis.totalPlays)
        assertEquals(3, analysis.habits.first { it.songKey == "a" }.plays)
        assertEquals(2, analysis.habits.first { it.songKey == "a" }.maxConsecutiveRepeats)
        assertEquals(1, analysis.transitions["Pop" to "Rock"])
        assertNull(analysis.transitions["Rock" to "Pop"])
    }
    @Test fun `listening across midnight is split into its actual calendar months and hours`() {
        val start = java.time.Instant.parse("2026-01-31T23:59:30Z").toEpochMilli()
        val data = ListeningInsightAnalysis.analyze(listOf(record("1", "a", start)))
        assertEquals(30_000L, data.monthly["2026-01"])
        assertEquals(30_000L, data.monthly["2026-02"])
        assertEquals(30_000L, data.hourly[23]); assertEquals(30_000L, data.hourly[0])
    }
    @Test fun `native rules reject incompatible schemas nonfinite weights and unsupported UI sections`() {
        listOf("{\"schemaVersion\":2,\"version\":1}", "{\"schemaVersion\":1,\"version\":1,\"recommendation\":{\"audio\":-1}}",
            "{\"schemaVersion\":1,\"version\":1,\"homeSections\":[\"EXECUTE_CODE\"]}").forEach {
            assertTrue(runCatching { ExperienceProfile.decode(it) }.isFailure)
        }
        assertEquals(2, ExperienceProfile.decode("{\"schemaVersion\":1,\"version\":2}").version)
    }
    @Test fun `recommendations prefer learned interests and suppress current and just played songs`() {
        val now = 1_700_000_000_000L
        val history = listOf(ListeningLedger.Affinity("heard", "Artist", "Pop", 900_000, 5, now))
        val ranked = PersonalRecommendation.rank(listOf(RecommendationCandidate("new", "Artist", "Pop", now),
            RecommendationCandidate("heard", "Artist", "Pop", now), RecommendationCandidate("unrelated", "Other", "Rock", now),
            RecommendationCandidate("current", "Artist", "Pop", now, current = true)), history, ExperienceProfile(), now)
        assertEquals("new", ranked.first().key); assertFalse(ranked.any { it.key == "current" })
        assertTrue("genre" in ranked.first().reasons)
    }
    @Test fun `time preferences and genre transitions boost familiar flows while skips penalize songs`() {
        val candidates = listOf(RecommendationCandidate("a", "Artist", "Rock", 0), RecommendationCandidate("b", "Artist", "Pop", 0))
        val context = RecommendationContext(hourlyGenres = mapOf("Pop" to 1.0), genreTransitions = mapOf("Pop" to 1.0), skipRates = mapOf("a" to 1.0))
        val ranked = PersonalRecommendation.rank(candidates, emptyList(), ExperienceProfile(), context = context)
        assertEquals("b", ranked.first().key); assertTrue("time" in ranked.first().reasons); assertTrue("transition" in ranked.first().reasons)
    }
    @Test fun `streaming spectrum recognizes a tone and remains finite for silence`() {
        val tone = SignalAnalysis(8192, 1000)
        val silence = SignalAnalysis(8192, 1000)
        repeat(8192) { n -> tone.add(.5 * sin(2 * PI * 1000 * n / 8192)); silence.add(0.0) }
        val a = tone.finish("tone", "a", 1); val b = silence.finish("silence", "b", 1)
        assertTrue(a.spectralBands[4] > .9); assertTrue(a.rmsDb in -10.0..-8.0)
        assertTrue(b.rmsDb.isFinite()); assertTrue(b.spectralBands.all { it.isFinite() })
        assertEquals(1.0, SignalAnalysis.similarity(a, a), 0.0)
        assertEquals(256, a.waveform.size)
    }
    @Test fun `waveform dynamics distinguish matching envelopes from opposing envelopes`() {
        val feature = AudioFeatures(songKey = "a", pcmSha256 = "a", sampleRate = 8192, channels = 1, decodedDurationMs = 1000,
            rmsDb = -9.0, peakDb = -6.0, zeroCrossingRate = .1, spectralBands = listOf(.5, .5), waveform = listOf(.1, .2, .4, .8))
        val sameEnvelope = feature.copy(songKey = "b", pcmSha256 = "b")
        val opposite = feature.copy(songKey = "c", pcmSha256 = "c", waveform = feature.waveform.reversed())
        assertTrue(SignalAnalysis.similarity(feature, sameEnvelope) > SignalAnalysis.similarity(feature, opposite))
    }
}
