// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.time.Instant

class MetadataMergerTest {
    @Test
    fun `fills missing fields and preserves source evidence`() {
        val bugs = ArtistMetadata(
            id = "1",
            name = Evidence("Artist", Provider.BUGS_MOBILE, "https://m.bugs.co.kr/artist/1", 0.98, Instant.EPOCH),
            genres = Evidence(listOf("Ballad"), Provider.BUGS_MOBILE, "https://m.bugs.co.kr/artist/1", 0.9, Instant.EPOCH),
        )
        val melon = ArtistMetadata(
            name = Evidence("Artist", Provider.MELON, "https://www.melon.com/artist/detail.htm?artistId=2", 0.96, Instant.EPOCH),
            description = Evidence("Bio", Provider.MELON, "https://www.melon.com/artist/detail.htm?artistId=2", 0.8, Instant.EPOCH),
            debutDate = Evidence("2008.09.18", Provider.MELON, "https://www.melon.com/artist/detail.htm?artistId=2", 0.96, Instant.EPOCH),
        )
        val merged = MetadataMerger.artists(listOf(bugs, melon))
        assertEquals("Artist", merged.name?.value)
        assertEquals(Provider.BUGS_MOBILE, merged.name?.provider)
        assertEquals("Bio", merged.description?.value)
        assertEquals("2008.09.18", merged.debutDate?.value)
        assertEquals(listOf("Ballad"), merged.genres?.value)
    }

    @Test
    fun `year extraction handles ISO and Korean formatted dates`() {
        assertEquals(2008, "2008.09.18".yearOrNull())
        assertEquals(2024, "2024-02-21".yearOrNull())
        assertNull(null.yearOrNull())
    }
}
