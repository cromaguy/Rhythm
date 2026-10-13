// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicProfileTest {
    private fun sample(url: String, text: String, selector: String = ".track-title") = ElementSelection(
        pageUrl = url,
        tagName = "h2",
        textSample = text,
        attributes = mapOf("class" to "track-title"),
        candidates = listOf(
            SelectorCandidate(selector, matchCount = 1, selectsClickedElement = true, stabilityScore = 88),
            SelectorCandidate("article > h2$selector", matchCount = 1, selectsClickedElement = true, stabilityScore = 75),
        ),
    )

    @Test
    fun `builds profile only from same selector uniquely chosen on two pages`() {
        val firstUrl = "https://catalog.example/song/first"
        val secondUrl = "https://catalog.example/song/second"
        val wizard = SelectorProfileWizard(firstUrl, EntityKind.TRACK, "Example Music")
        wizard.recordFirstPage(CatalogField.TRACK_TITLE, sample(firstUrl, "First song"))
        wizard.beginSecondPage(secondUrl)

        val rule = wizard.recordSecondPage(CatalogField.TRACK_TITLE, sample(secondUrl, "Second song"))
        val profile = wizard.buildProfile()

        assertEquals(".track-title", rule.selector)
        assertEquals(2, rule.verifiedPageUrls.size)
        assertTrue(rule.confidence in 0.5..0.99)
        assertEquals("https://catalog.example", profile.siteOrigin)
        assertEquals(CatalogField.TRACK_TITLE, profile.rules.single().field)
    }

    @Test
    fun `rejects selectors that are unique on each page but do not generalize`() {
        val firstUrl = "https://catalog.example/song/first"
        val secondUrl = "https://catalog.example/song/second"
        val wizard = SelectorProfileWizard(firstUrl, EntityKind.TRACK, "Example Music")
        wizard.recordFirstPage(CatalogField.TRACK_TITLE, sample(firstUrl, "First song", ".first-title"))
        wizard.beginSecondPage(secondUrl)

        val error = assertFailsWith<IllegalArgumentException> {
            wizard.recordSecondPage(CatalogField.TRACK_TITLE, sample(secondUrl, "Second song", ".second-title"))
        }
        assertTrue(error.message.orEmpty().contains("No CSS selector"))
    }

    @Test
    fun `profile survives JSON round trip and extracts image and text`() {
        val imageRule = SelectorRule(
            field = CatalogField.TRACK_IMAGE,
            selector = "img.cover",
            extraction = ExtractionMode.IMAGE_URL,
            confidence = 0.9,
            verifiedPageUrls = listOf("https://catalog.example/a", "https://catalog.example/b"),
        )
        val titleRule = SelectorRule(
            field = CatalogField.TRACK_TITLE,
            selector = "h2.track-title",
            extraction = ExtractionMode.TEXT,
            confidence = 0.92,
            verifiedPageUrls = listOf("https://catalog.example/a", "https://catalog.example/b"),
        )
        val profile = DynamicSiteProfile(
            id = "profile-1",
            siteOrigin = "https://catalog.example",
            displayName = "Example Music",
            kind = EntityKind.TRACK,
            rules = listOf(imageRule, titleRule),
            createdAt = "2026-10-13T00:00:00Z",
        )
        val decoded = SiteProfileCodec.decode(SiteProfileCodec.encode(profile))
        val values = DynamicProfileExtractor.extract(
            decoded,
            "https://catalog.example/song/next",
            "<h2 class='track-title'>Next Song</h2><img class='cover' src='/images/next.jpg'>",
        ).associateBy { it.field }

        assertEquals(profile, decoded)
        assertEquals("Next Song", values[CatalogField.TRACK_TITLE]?.value)
        assertEquals("https://catalog.example/images/next.jpg", values[CatalogField.TRACK_IMAGE]?.value)
    }

    @Test
    fun `profile cannot be used on another origin`() {
        val profile = DynamicSiteProfile(
            id = "p", siteOrigin = "https://catalog.example", displayName = "x",
            kind = EntityKind.TRACK,
            rules = listOf(SelectorRule(CatalogField.TRACK_TITLE, ".title", ExtractionMode.TEXT, confidence = 0.8,
                verifiedPageUrls = listOf("https://catalog.example/a", "https://catalog.example/b"))),
        )
        assertFailsWith<IllegalArgumentException> {
            DynamicProfileExtractor.extract(profile, "https://other.example/song", "<div class=title>x</div>")
        }
    }

    @Test
    fun `decodes WebView picker messages and ignores selectors that become ambiguous`() {
        val selection = SelectionMessageCodec.decode(
            """{"pageUrl":"https://catalog.example/a","tagName":"h2","textSample":"Title","attributes":{"class":"title"},"candidates":[{"selector":"h2.title","matchCount":1,"selectsClickedElement":true,"stabilityScore":88}]}"""
        )
        assertEquals("h2", selection.tagName)
        assertEquals("h2.title", selection.candidates.single().selector)

        val profile = DynamicSiteProfile(
            id = "p", siteOrigin = "https://catalog.example", displayName = "x", kind = EntityKind.TRACK,
            rules = listOf(SelectorRule(CatalogField.TRACK_TITLE, "h2.title", ExtractionMode.TEXT, confidence = 0.8,
                verifiedPageUrls = listOf("https://catalog.example/a", "https://catalog.example/b"))),
        )
        val result = DynamicProfileExtractor.extract(
            profile, "https://catalog.example/c", "<h2 class='title'>one</h2><h2 class='title'>two</h2>"
        )
        assertTrue(result.isEmpty())
    }
}
