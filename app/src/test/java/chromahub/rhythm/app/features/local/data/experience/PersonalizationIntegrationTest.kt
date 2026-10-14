// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import org.junit.Assert.*
import org.junit.Test

class PersonalizationIntegrationTest {
    @Test fun `seek jumps and duplicate replay never manufacture completion`() {
        val coverage = PlayCoverage()
        coverage.record(0, 20_000, 20_000, 100_000)
        coverage.record(80_000, 100_000, 20_000, 100_000)
        coverage.record(0, 20_000, 20_000, 100_000)
        assertEquals(.4, coverage.ratio(100_000)!!, .00001)
        assertNull(coverage.ratio(0))
    }
    @Test fun `checkpoint snapshots and speed affect real audio coverage`() {
        val coverage = PlayCoverage()
        coverage.record(0, 10_000, 10_000, 100_000)
        coverage.record(0, 20_000, 20_000, 100_000)
        coverage.record(50_000, 70_000, 20_000, 100_000)
        assertEquals(40_000, coverage.coveredMs())
        val jump = PlayCoverage(); jump.record(0, 100_000, 1000, 100_000)
        assertTrue(jump.ratio(100_000)!! < .02)
    }
    @Test fun `average listening percent gives each play equal weight`() {
        fun row(id: String, listened: Long) = ListeningRecord(id, id, "segment", "s", "s", "S", "A", "B", "Pop", 0, listened, listened, 100_000, 0, listened, 1f, "pause")
        val habit = ListeningInsightAnalysis.analyze(listOf(row("short", 10_000), row("long", 90_000))).habits.single()
        assertEquals(.5, habit.averageCompletionRatio!!, .00001)
    }
    @Test fun `weather is timestamped and stale provider data is rejected`() {
        val now = java.time.Instant.parse("2026-10-14T10:05:00Z").toEpochMilli()
        val json = """{"current":{"time":"2026-10-14T10:00","temperature_2m":16.5,"weather_code":61,"is_day":1}}"""
        val weather = ListeningWeather.decode(json, "37.56:126.98", now)
        assertEquals("rain", weather.group); assertEquals(16.5, weather.temperatureC, 0.0)
        assertTrue(runCatching { ListeningWeather.decode(json, "region", now + 2 * 3600_000) }.isFailure)
        assertNull(weather.humidityPercent)
    }
    @Test fun `matching weather and real completion contribute to context ranking`() {
        val weather = WeatherSnapshot(0, 0, "R", 61, 15.0, null, null, 1.0, null, null, null, true)
        val scene = ListeningSituation(weather = weather, hour = 10, weekday = 3)
        val row = ListeningRecord("a", "a", "segment", "a", "a", "A", "Artist", "Album", "Pop", 0, 90_000, 90_000, 100_000, 0, 90_000, 1f, "pause", situation = scene)
        val context = AdaptiveRecommendation.contextPreference(listOf(row), scene, 90_000)
        assertTrue(context.weatherGenres.getValue("pop") > 0)
        assertTrue(context.completionRates.getValue("a") > .5)
        assertTrue(AdaptiveRecommendation.contextPreference(listOf(row), scene.copy(weather = null), 90_000).weatherGenres.isEmpty())
    }
    @Test fun `snooze expiry and permanent exclusions have separate semantics`() {
        val options = PersonalOptions(snoozed = mapOf("sleep" to 100), hidden = setOf("hidden"))
        assertTrue(PersonalCollections.excluded(options, "sleep", 99)); assertFalse(PersonalCollections.excluded(options, "sleep", 100))
        assertTrue(PersonalCollections.excluded(options, "hidden", 10_000)); assertFalse(PersonalCollections.excluded(options, "other", 1))
    }
    @Test fun `tempo handles pulses and refuses silence`() {
        val envelope = (0 until 1000).map { if (it % 25 < 2) 1.0 else 0.0 }
        val tempo = SignalAnalysis.tempo(envelope)
        assertEquals(120.0, tempo.first!!, 1.0); assertTrue(tempo.second > .8)
        assertNull(SignalAnalysis.tempo(List(1000) { 0.0 }).first)
        assertNull(SignalAnalysis.key(List(12) { 0.0 }).first)
    }
    @Test fun `local taste vectors respond to listening weight and reject empty norms`() {
        fun feature(key: String, band: Int) = AudioFeatures(songKey = key, pcmSha256 = key, sampleRate = 8000, channels = 1, decodedDurationMs = 10_000,
            rmsDb = -20.0, peakDb = -6.0, zeroCrossingRate = .1, spectralBands = List(8) { if (it == band) 1.0 else 0.0 }, waveform = List(256) { .3 })
        val a = feature("a", 0); val b = feature("b", 7)
        val taste = AudioVectors.taste(listOf(a to 10.0, b to 1.0))!!
        assertTrue(AudioVectors.cosine(taste, AudioVectors.describe(a)) > AudioVectors.cosine(taste, AudioVectors.describe(b)))
        assertNull(AudioVectors.taste(emptyList()))
        assertEquals(0.0, AudioVectors.cosine(List(4) { 0.0 }, List(4) { 0.0 }), 0.0)
    }

    @Test fun `microphone summaries preserve only finite level information`() {
        val pcm = ShortArray(16000) { (kotlin.math.sin(2 * kotlin.math.PI * 440 * it / 16000) * 16384).toInt().toShort() }
        val summary = ListeningSound.summarize(pcm, pcm.size, 1, 9, true)
        assertEquals(-9.03, summary.rmsDbfs, .1); assertEquals(-6.02, summary.peakDbfs, .1)
        assertEquals(0.0, summary.clippedFraction, 0.0); assertTrue(summary.musicPlaying)
        assertTrue(ListeningSound.summarize(ShortArray(100), 100, 1, 9, false).rmsDbfs.isFinite())
    }
    @Test fun `brightness volume and nearby sound are independent context signals`() {
        val sound = AmbientSound(0, -35.0, -20.0, 0.0, 16000, 9, false)
        val scene = ListeningSituation(brightnessSetting = 30, systemVolume = 3, systemVolumeMax = 15, playerVolume = .7f, ambientSound = sound)
        val row = ListeningRecord("a", "a", "segment", "a", "a", "A", "B", "C", "Pop", 0, 90_000, 90_000, 100_000, 0, 90_000, 1f, "pause", situation = scene)
        val matched = AdaptiveRecommendation.contextPreference(listOf(row), scene, 90_000)
        val different = AdaptiveRecommendation.contextPreference(listOf(row), scene.copy(brightnessSetting = 250, systemVolume = 15, ambientSound = sound.copy(rmsDbfs = -5.0)), 90_000)
        assertTrue(matched.brightnessGenres.getValue("pop") > different.brightnessGenres.getValue("pop"))
        assertTrue(matched.volumeGenres.getValue("pop") > different.volumeGenres.getValue("pop"))
        assertTrue(matched.ambientGenres.getValue("pop") > different.ambientGenres.getValue("pop"))
        assertTrue(AdaptiveRecommendation.contextPreference(listOf(row), scene.copy(ambientSound = null), 90_000).ambientGenres.isEmpty())
    }

}
