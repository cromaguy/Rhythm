// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlin.test.*

class CatalogDocumentCodecTest {
    @Test fun `round trip preserves Korean lyrics line breaks and source`() {
        val document = CatalogDocument(EntityKind.TRACK, listOf(
            CatalogValue(CatalogField.TRACK_LYRICS, "첫 줄\n둘째 줄", "https://example.com/song/1"),
        ))
        assertEquals(document, CatalogDocumentCodec.decode(CatalogDocumentCodec.encode(document)))
    }

    @Test fun `rejects fields from another entity kind`() {
        assertFailsWith<IllegalArgumentException> {
            CatalogDocumentCodec.decode("""{"kind":"TRACK","values":[{"field":"ARTIST_NAME","value":"x","sourceUrl":"https://example.com"}]}""")
        }
    }

    @Test fun `rejects duplicate import fields`() {
        val value = CatalogValue(CatalogField.TRACK_TITLE, "Title", "https://example.com/song/1")
        assertFailsWith<IllegalArgumentException> {
            CatalogDocumentCodec.decode(CatalogDocumentCodec.encode(CatalogDocument(EntityKind.TRACK, listOf(value, value))))
        }
    }
}
