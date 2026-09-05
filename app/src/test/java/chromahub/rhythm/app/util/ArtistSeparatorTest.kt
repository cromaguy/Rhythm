/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSeparatorTest {

    private val standardDelimiters = ";/"
    private val minimalDelimiters = ";"
    private val extendedDelimiters = ";/,+&"

    @Test
    fun splitArtistNames_standardDefaultDelimiters() {
        assertEquals(
            listOf("Artist1", "Artist2", "Artist3"),
            ArtistSeparator.splitArtistNames("Artist1/Artist2;Artist3", standardDelimiters, true)
        )
        assertEquals(
            listOf("Kendrick Lamar", "SZA"),
            ArtistSeparator.splitArtistNames("Kendrick Lamar; SZA", standardDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_preservesBandsWithCommasAndAmpersandsUnderStandardDefaults() {
        // Standard defaults (;/ ) do not split on , or & or +
        assertEquals(
            listOf("Tyler, The Creator"),
            ArtistSeparator.splitArtistNames("Tyler, The Creator", standardDelimiters, true)
        )
        assertEquals(
            listOf("Simon & Garfunkel"),
            ArtistSeparator.splitArtistNames("Simon & Garfunkel", standardDelimiters, true)
        )
        assertEquals(
            listOf("Earth, Wind & Fire"),
            ArtistSeparator.splitArtistNames("Earth, Wind & Fire", standardDelimiters, true)
        )
        assertEquals(
            listOf("+44"),
            ArtistSeparator.splitArtistNames("+44", standardDelimiters, true)
        )
        assertEquals(
            listOf("Florence + The Machine"),
            ArtistSeparator.splitArtistNames("Florence + The Machine", standardDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_minimalPreset_semicolonOnly() {
        assertEquals(
            listOf("Artist1/Artist2", "Artist3"),
            ArtistSeparator.splitArtistNames("Artist1/Artist2;Artist3", minimalDelimiters, true)
        )
        assertEquals(
            listOf("AC/DC", "Brian Johnson"),
            ArtistSeparator.splitArtistNames("AC/DC; Brian Johnson", minimalDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_customWordDelimiters() {
        val customDelimiters = ArtistSeparator.serializeDelimiters(listOf(";", "/", "feat.", "ft.", " x "))
        
        assertEquals(
            listOf("Artist1", "Artist2"),
            ArtistSeparator.splitArtistNames("Artist1 feat. Artist2", customDelimiters, true)
        )
        assertEquals(
            listOf("Artist1", "Artist2"),
            ArtistSeparator.splitArtistNames("Artist1 ft. Artist2", customDelimiters, true)
        )
        assertEquals(
            listOf("Artist1", "Artist2"),
            ArtistSeparator.splitArtistNames("Artist1 x Artist2", customDelimiters, true)
        )
        assertEquals(
            listOf("Daft Punk", "Pharrell Williams"),
            ArtistSeparator.splitArtistNames("Daft Punk feat. Pharrell Williams", customDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_cjkDelimiters() {
        val cjkDelimiters = ArtistSeparator.serializeDelimiters(listOf("、", "／", "・", "•"))

        assertEquals(
            listOf("YOASOBI", "Ayase"),
            ArtistSeparator.splitArtistNames("YOASOBI、Ayase", cjkDelimiters, true)
        )
        assertEquals(
            listOf("Artist A", "Artist B"),
            ArtistSeparator.splitArtistNames("Artist A／Artist B", cjkDelimiters, true)
        )
        assertEquals(
            listOf("Artist 1", "Artist 2"),
            ArtistSeparator.splitArtistNames("Artist 1・Artist 2", cjkDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_escapedDelimiter_staysTogether() {
        assertEquals(
            listOf("AC/DC"),
            ArtistSeparator.splitArtistNames("AC\\/DC", "/", true)
        )
    }

    @Test
    fun splitArtistNames_escapedAndRealDelimiters() {
        assertEquals(
            listOf("A/B", "C"),
            ArtistSeparator.splitArtistNames("A\\/B/C", "/", true)
        )
    }

    @Test
    fun splitArtistNames_multipleEscapedDelimiters() {
        assertEquals(
            listOf("A/B/C"),
            ArtistSeparator.splitArtistNames("A\\/B\\/C", "/", true)
        )
    }

    @Test
    fun splitArtistNames_disabled_returnsWholeString() {
        assertEquals(
            listOf("Artist1/Artist2"),
            ArtistSeparator.splitArtistNames("Artist1/Artist2", standardDelimiters, false)
        )
    }

    @Test
    fun splitArtistNames_emptyDelimiters_returnsWholeString() {
        assertEquals(
            listOf("Artist1/Artist2"),
            ArtistSeparator.splitArtistNames("Artist1/Artist2", "", true)
        )
    }

    @Test
    fun splitArtistNames_nullOrBlank_returnsEmpty() {
        assertEquals(emptyList<String>(), ArtistSeparator.splitArtistNames(null, standardDelimiters, true))
        assertEquals(emptyList<String>(), ArtistSeparator.splitArtistNames("", standardDelimiters, true))
        assertEquals(emptyList<String>(), ArtistSeparator.splitArtistNames("   ", standardDelimiters, true))
    }

    @Test
    fun splitArtistNames_trailingLeadingAndConsecutiveDelimiters_areFiltered() {
        assertEquals(listOf("Artist1"), ArtistSeparator.splitArtistNames("Artist1/", "/", true))
        assertEquals(listOf("Artist1"), ArtistSeparator.splitArtistNames("/Artist1", "/", true))
        assertEquals(listOf("Artist1"), ArtistSeparator.splitArtistNames("/ Artist1 /", "/", true))
        assertEquals(listOf("A", "B"), ArtistSeparator.splitArtistNames("A//B", "/", true))
        assertEquals(listOf("A", "B"), ArtistSeparator.splitArtistNames("A /// B", "/", true))
    }

    @Test
    fun splitArtistNames_deduplicatesIdenticalCollaborators() {
        assertEquals(
            listOf("Artist1", "Artist2"),
            ArtistSeparator.splitArtistNames("Artist1 / Artist2 / Artist1", "/", true)
        )
    }

    @Test
    fun splitArtistNames_trimSpacesAroundNames() {
        assertEquals(
            listOf("Artist1", "Artist2"),
            ArtistSeparator.splitArtistNames(" Artist1 / Artist2 ", "/", true)
        )
    }

    @Test
    fun splitArtistNames_escapedBackslashBeforeNonDelimiter_isKeptLiteral() {
        assertEquals(
            listOf("AC\\DC"),
            ArtistSeparator.splitArtistNames("AC\\DC", "/", true)
        )
    }

    @Test
    fun parseAndSerializeDelimiters_roundtrip() {
        val simpleList = listOf(";", "/")
        val simpleSerialized = ArtistSeparator.serializeDelimiters(simpleList)
        assertEquals(";", simpleSerialized.substring(0, 1))
        assertEquals(simpleList, ArtistSeparator.parseDelimiters(simpleSerialized))

        val complexList = listOf(";", "/", "feat.", "ft.", " // ")
        val complexSerialized = ArtistSeparator.serializeDelimiters(complexList)
        val parsed = ArtistSeparator.parseDelimiters(complexSerialized)
        assertEquals(listOf(";", "/", "feat.", "ft.", "//"), parsed)
    }

    @Test
    fun getPrimaryArtist_returnsFirstOrOriginal() {
        assertEquals("Artist1", ArtistSeparator.getPrimaryArtist("Artist1 / Artist2", standardDelimiters, true))
        assertEquals("Unknown Artist", ArtistSeparator.getPrimaryArtist(null, standardDelimiters, true))
    }

    @Test
    fun formatArtists_formatsProperly() {
        assertEquals("Artist1", ArtistSeparator.formatArtists(listOf("Artist1")))
        assertEquals("Artist1, Artist2", ArtistSeparator.formatArtists(listOf("Artist1", "Artist2")))
        assertEquals("Artist1, Artist2, Artist3 & 2 more", ArtistSeparator.formatArtists(listOf("Artist1", "Artist2", "Artist3", "Artist4", "Artist5")))
    }

    @Test
    fun escapeArtistName_escapesConfiguredDelimiters() {
        assertEquals("AC\\/DC", ArtistSeparator.escapeArtistName("AC/DC", standardDelimiters))
        assertEquals("A\\;B", ArtistSeparator.escapeArtistName("A;B", standardDelimiters))
    }

    @Test
    fun splitArtistNames_wordDelimiters_doNotSplitInsideWords() {
        val ftDelimiters = ArtistSeparator.serializeDelimiters(listOf(";", "/", "ft.", "ft", "feat."))

        // "ft" or "ft." must not match inside "Daft Punk", "Taylor Swift", or "Leftfield"
        assertEquals(
            listOf("Daft Punk"),
            ArtistSeparator.splitArtistNames("Daft Punk", ftDelimiters, true)
        )
        assertEquals(
            listOf("Taylor Swift"),
            ArtistSeparator.splitArtistNames("Taylor Swift", ftDelimiters, true)
        )
        assertEquals(
            listOf("Leftfield"),
            ArtistSeparator.splitArtistNames("Leftfield", ftDelimiters, true)
        )

        // "with" must not match inside "Within Temptation"
        val withDelimiters = ArtistSeparator.serializeDelimiters(listOf(";", "/", "with"))
        assertEquals(
            listOf("Within Temptation"),
            ArtistSeparator.splitArtistNames("Within Temptation", withDelimiters, true)
        )

        // "and" must not match inside "Band of Horses" or "The Cranberries"
        val andDelimiters = ArtistSeparator.serializeDelimiters(listOf(";", "/", "and"))
        assertEquals(
            listOf("Band of Horses"),
            ArtistSeparator.splitArtistNames("Band of Horses", andDelimiters, true)
        )
        assertEquals(
            listOf("The Cranberries"),
            ArtistSeparator.splitArtistNames("The Cranberries", andDelimiters, true)
        )

        // "x" must not match inside "Phoenix" or "The xx"
        val xDelimiters = ArtistSeparator.serializeDelimiters(listOf(";", "/", "x"))
        assertEquals(
            listOf("Phoenix"),
            ArtistSeparator.splitArtistNames("Phoenix", xDelimiters, true)
        )
        assertEquals(
            listOf("The xx"),
            ArtistSeparator.splitArtistNames("The xx", xDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_parenthesesAndBracketsAroundCollaborations_cleanedProperly() {
        val featuredDelimiters = ArtistSeparator.serializeDelimiters(listOf(";", "/", "ft.", "feat.", "featuring", "&"))

        // Parentheses around feature
        assertEquals(
            listOf("Artist A", "Artist B"),
            ArtistSeparator.splitArtistNames("Artist A (ft. Artist B)", featuredDelimiters, true)
        )
        assertEquals(
            listOf("Artist A", "Artist B"),
            ArtistSeparator.splitArtistNames("Artist A (feat. Artist B)", featuredDelimiters, true)
        )

        // Square brackets around feature
        assertEquals(
            listOf("Artist A", "Artist B"),
            ArtistSeparator.splitArtistNames("Artist A [ft. Artist B]", featuredDelimiters, true)
        )
        assertEquals(
            listOf("Artist A", "Artist B"),
            ArtistSeparator.splitArtistNames("Artist A [feat. Artist B]", featuredDelimiters, true)
        )

        // Multiple artists inside parentheses
        assertEquals(
            listOf("Artist A", "Artist B", "Artist C"),
            ArtistSeparator.splitArtistNames("Artist A (featuring Artist B & Artist C)", featuredDelimiters, true)
        )

        // Balanced brackets within legitimate artist names should be preserved
        assertEquals(
            listOf("Band (UK)"),
            ArtistSeparator.splitArtistNames("Band (UK)", featuredDelimiters, true)
        )
        assertEquals(
            listOf("(Hed) P.E."),
            ArtistSeparator.splitArtistNames("(Hed) P.E.", featuredDelimiters, true)
        )
        assertEquals(
            listOf("Band (UK)", "Artist B"),
            ArtistSeparator.splitArtistNames("Band (UK) ft. Artist B", featuredDelimiters, true)
        )
    }

    @Test
    fun splitArtistNames_tokenListOverload_doesNotSplitOnSingleCharsOfWordDelimiters() {
        val tokens = listOf(";", "/", "ft.")

        // Should NOT split on 't', 'f', or '.' in "The Beatles", "Daft Punk", or "Taylor Swift"
        assertEquals(
            listOf("The Beatles"),
            ArtistSeparator.splitArtistNames("The Beatles", tokens, true)
        )
        assertEquals(
            listOf("Daft Punk"),
            ArtistSeparator.splitArtistNames("Daft Punk", tokens, true)
        )
        assertEquals(
            listOf("Taylor Swift"),
            ArtistSeparator.splitArtistNames("Taylor Swift", tokens, true)
        )
        assertEquals(
            listOf("Foster The People"),
            ArtistSeparator.splitArtistNames("Foster The People", tokens, true)
        )

        // Legitimate collaboration should split cleanly
        assertEquals(
            listOf("Daft Punk", "Pharrell Williams"),
            ArtistSeparator.splitArtistNames("Daft Punk ft. Pharrell Williams", tokens, true)
        )
    }

    @Test
    fun splitArtistNames_unicodeWordDelimiters_preservesUnicodeArtistNames() {
        val tokens = listOf(";", "/", "ft.", "ft", "with", "x")

        // Accented characters and international characters must be respected as word characters
        assertEquals(
            listOf("Beyoncé"),
            ArtistSeparator.splitArtistNames("Beyoncé", tokens, true)
        )
        assertEquals(
            listOf("Björk"),
            ArtistSeparator.splitArtistNames("Björk", tokens, true)
        )
        assertEquals(
            listOf("Mötley Crüe"),
            ArtistSeparator.splitArtistNames("Mötley Crüe", tokens, true)
        )
        assertEquals(
            listOf("Beyoncé", "Shakira"),
            ArtistSeparator.splitArtistNames("Beyoncé ft. Shakira", tokens, true)
        )
        assertEquals(
            listOf("Björk", "Rosalía"),
            ArtistSeparator.splitArtistNames("Björk with Rosalía", tokens, true)
        )
    }
}
