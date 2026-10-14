// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import org.junit.Assert.*
import org.junit.Test

class AdaptiveRecommendationTest {
    @Test fun `learning rewards useful features without unbounded weights`() {
        val features = List(13) { if (it == 8) 1.0 else 0.0 }
        var positive = List(13) { 0.0 }; var negative = positive
        repeat(500) { n -> positive = AdaptiveRecommendation.learn(positive, features, 1.0, n.toLong()); negative = AdaptiveRecommendation.learn(negative, features, 0.0, n.toLong()) }
        assertTrue(AdaptiveRecommendation.probability(positive, features) > .8)
        assertTrue(AdaptiveRecommendation.probability(negative, features) < .2)
        assertTrue(positive.all { it.isFinite() && it in -3.0..3.0 })
        assertEquals(0.0, positive[0], 0.0)
    }
    @Test fun `malformed learning inputs are rejected`() {
        assertTrue(runCatching { AdaptiveRecommendation.learn(List(13) { 0.0 }, List(13) { Double.NaN }, 1.0, 1) }.isFailure)
        assertTrue(runCatching { AdaptiveRecommendation.learn(List(13) { 0.0 }, List(12) { 1.0 }, 1.0, 1) }.isFailure)
    }
    @Test fun `context uses complete plays and smooths sparse matching places and outputs`() {
        val now = java.time.Instant.parse("2026-10-14T10:00:00Z").toEpochMilli()
        val scene = ListeningSituation(place = "Home", routeType = "wired", weekday = 3, hour = 10)
        fun row(key: String, genre: String, context: ListeningSituation, played: Long) = ListeningRecord(key, key, "segment", key, key, key, "A", "B", genre,
            now - 300_000, now - 300_000 + played, played, 60_000, 0, played, 1f, "pause", timezone = "UTC", situation = context)
        val result = AdaptiveRecommendation.contextPreference(listOf(row("a", "POP", scene, 60_000), row("b", "Rock", scene.copy(place = "Office", routeType = "speaker"), 5000)), scene, now)
        assertTrue(result.placeGenres.getValue("pop") > 0)
        assertFalse(result.placeGenres.containsKey("rock"))
        assertTrue(result.outputGenres.getValue("pop") < .3)
        assertTrue(result.completionRates.getValue("a") > result.completionRates.getValue("b"))
    }
    @Test fun `different headphones retain separate learned output contexts`() {
        fun device(name: String) = ListeningSituation(routeType = "bluetooth", connectedOutputs = listOf(OutputSnapshot(8, name, listOf(48000), listOf(2), emptyList())))
        assertNotEquals(ListeningContext.outputGroup(device("Headphones A")), ListeningContext.outputGroup(device("Headphones B")))
        assertEquals(ListeningContext.outputGroup(device("HEADPHONES A")), ListeningContext.outputGroup(device("headphones a")))
    }
    @Test fun `explicit negative feedback excludes a song even if taste strongly matches`() {
        val ranked = PersonalRecommendation.rank(listOf(RecommendationCandidate("a", "Artist", "Pop", 0), RecommendationCandidate("b", "Other", "Rock", 0)),
            emptyList(), ExperienceProfile(), context = RecommendationContext(explicitFeedback = mapOf("a" to 0.0)))
        assertEquals(listOf("b"), ranked.map { it.key })
    }
    @Test fun `mix diversity considers all selected artists and reserves discovery`() {
        val candidates = (0..9).map { RecommendationCandidate("$it", if (it < 7) "A" else "B$it", "Pop", 0) }
        val scores = candidates.mapIndexed { n, c -> RecommendationScore(c.key, 1.0 - n * .01, if (n == 9) listOf("unplayed") else emptyList()) }
        val result = AdaptiveRecommendation.diversify(scores, candidates, 8, .25)
        assertEquals(8, result.map { it.key }.distinct().size)
        assertTrue(result.take(4).any { it.key.toInt() >= 7 })
        assertTrue(result.any { "exploration" in it.reasons })
    }
}
