// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import kotlin.test.*

class CatalogIdentityTest {
    private fun hit(id: String, title: String, artist: String?) = SearchHit(Provider.BUGS_MOBILE, EntityKind.TRACK, id, title, "https://music.bugs.co.kr/track/$id", artist)
    @Test fun `matches bilingual artist aliases and normalized title punctuation`() {
        assertTrue(CatalogIdentity.artistMatches("IU", "아이유(IU)"))
        assertTrue(CatalogIdentity.titleMatches("밤 편지", "밤편지"))
        assertEquals("1", CatalogIdentity.select(listOf(hit("1", "밤편지", "아이유(IU)")), "밤 편지", "IU")?.providerId)
    }
    @Test fun `does not attach another artists cover or a different song`() {
        assertNull(CatalogIdentity.select(listOf(hit("1", "밤편지", "Another artist")), "밤편지", "아이유"))
        assertNull(CatalogIdentity.select(listOf(hit("1", "다른 노래", "아이유")), "밤편지", "아이유"))
        assertFalse(CatalogIdentity.artistMatches("Unknown Artist", "Unknown Artist"))
    }
    @Test fun `ambiguous versions need a human while duplicate links are harmless`() {
        val first = hit("1", "밤편지", "아이유")
        assertNull(CatalogIdentity.select(listOf(first, first.copy(providerId = "2")), "밤편지", "아이유"))
        assertEquals(first, CatalogIdentity.select(listOf(first, first), "밤편지", "아이유"))
    }
}
