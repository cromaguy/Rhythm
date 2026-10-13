// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import kotlin.test.*
class TrackDurationTest {
    @Test fun `public duration formats are bounded and invalid values are rejected`() {
        assertEquals(183_000, TrackDuration.parse("03:03")); assertEquals(183_000, TrackDuration.parse("PT3M3S"))
        assertEquals(183_000, TrackDuration.parse("183")); assertEquals(3_661_000, TrackDuration.parse("1:01:01"))
        listOf("NaN", "Infinity", "3:99", "-1", "2026", "2026-10-13").forEach { value ->
            if (value != "2026") assertNull(TrackDuration.parse(value))
        }
    }
    @Test fun `wrong length or absent length prevents automatic audio match`() {
        assertTrue(CatalogIdentity.durationMatches(180_000, 181_000))
        assertFalse(CatalogIdentity.durationMatches(180_000, 230_000))
        assertFalse(CatalogIdentity.durationMatches(180_000, null))
        assertFalse(CatalogIdentity.durationMatches(180_000, 0))
    }
    @Test fun `intro tolerance is configurable as a percentage with a rounding floor`() {
        assertFalse(CatalogIdentity.durationMatches(200_000, 210_000, 3.0))
        assertTrue(CatalogIdentity.durationMatches(200_000, 210_000, 5.0))
        assertFalse(CatalogIdentity.durationMatches(200_000, 210_001, 5.0))
        assertTrue(CatalogIdentity.durationMatches(200_000, 200_999, 0.0))
        assertFailsWith<IllegalArgumentException> { CatalogIdentity.durationMatches(200_000, 210_000, Double.NaN) }
    }
    @Test fun `service priority is Bugs then Melon then Genie`() {
        assertEquals(listOf(Provider.BUGS_MOBILE, Provider.MELON, Provider.GENIE),
            setOf(Provider.GENIE, Provider.MELON, Provider.BUGS_MOBILE).sortedByDescending { it.priority })
    }
}
