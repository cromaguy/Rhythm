// SPDX-License-Identifier: GPL-3.0-or-later
package kr.co.metadata.crawler

import java.nio.file.Files
import kotlin.test.*

class CatalogPersistenceTest {
    private fun value(field: CatalogField, text: String, source: String = "https://source.test/track/1") = CatalogValue(field, text, source)

    @Test fun `fills missing fields without replacing existing fields or their provenance`() {
        val root = Files.createTempDirectory("catalog").toFile()
        try {
            val store = CatalogFileStore(root)
            val kind = EntityKind.TRACK
            store.merge(kind, "노래/01", CatalogDocument(kind, listOf(value(CatalogField.TRACK_TITLE, "Manual title", "file:///song.mp3"),
                value(CatalogField.TRACK_RELEASE_DATE, "0"))), replace = true, imageFile = "cover.jpg")
            store.merge(kind, "노래/01", CatalogDocument(kind, listOf(value(CatalogField.TRACK_TITLE, "Remote title"),
                value(CatalogField.TRACK_RELEASE_DATE, "2024.01.02"), value(CatalogField.TRACK_GENRES, "Pop"))),
                retryAfter = 42, attemptedFields = setOf(CatalogField.TRACK_LYRICS))
            val result = assertNotNull(CatalogFileStore(root).read(kind, "노래/01")) // Reopen, no memory cache.
            assertEquals("Manual title", result.document[CatalogField.TRACK_TITLE])
            assertEquals("file:///song.mp3", result.document.values.first { it.field == CatalogField.TRACK_TITLE }.sourceUrl)
            assertEquals("2024.01.02", result.document[CatalogField.TRACK_RELEASE_DATE])
            assertEquals("Pop", result.document[CatalogField.TRACK_GENRES])
            assertEquals("cover.jpg", result.imageFile)
            assertEquals(42, result.retryAfter)
            assertEquals(setOf(CatalogField.TRACK_LYRICS), result.attemptedFields)
        } finally { root.deleteRecursively() }
    }

    @Test fun `Unicode and unsafe path keys remain distinct and confined to the store`() {
        val root = Files.createTempDirectory("catalog").toFile()
        try {
            val keys = listOf("아이유/밤편지", "아이유/마음", "../../escape", "a:b", "a?b")
            keys.forEach { key -> CatalogFileStore(root).merge(EntityKind.TRACK, key,
                CatalogDocument(EntityKind.TRACK, listOf(value(CatalogField.TRACK_TITLE, key)))) }
            assertEquals(keys.size, root.listFiles()!!.size)
            keys.forEach { assertEquals(it, CatalogFileStore(root).read(EntityKind.TRACK, it)?.document?.get(CatalogField.TRACK_TITLE)) }
            assertNull(CatalogFileStore(root).read(EntityKind.ALBUM, keys.first()))
        } finally { root.deleteRecursively() }
    }

    @Test fun `corrupt records are ignored and a later import repairs them`() {
        val root = Files.createTempDirectory("catalog").toFile()
        try {
            val store = CatalogFileStore(root)
            val doc = CatalogDocument(EntityKind.TRACK, listOf(value(CatalogField.TRACK_TITLE, "Song")))
            store.merge(EntityKind.TRACK, "key", doc)
            root.listFiles()!!.single().writeText("{truncated")
            assertNull(store.read(EntityKind.TRACK, "key"))
            store.merge(EntityKind.TRACK, "key", doc)
            assertEquals(doc, store.read(EntityKind.TRACK, "key")?.document)
        } finally { root.deleteRecursively() }
    }

    @Test fun `manual replacement can clear a field and subsequent missing fill restores it`() {
        val root = Files.createTempDirectory("catalog").toFile()
        try {
            val store = CatalogFileStore(root)
            val field = CatalogField.TRACK_GENRES
            fun doc(text: String) = CatalogDocument(EntityKind.TRACK, listOf(value(field, text)))
            store.merge(EntityKind.TRACK, "key", doc("Rock"))
            store.merge(EntityKind.TRACK, "key", doc(""), replace = true)
            assertFalse(store.read(EntityKind.TRACK, "key")!!.document.has(field))
            store.merge(EntityKind.TRACK, "key", doc("Jazz"))
            assertEquals("Jazz", store.read(EntityKind.TRACK, "key")?.document?.get(field))
        } finally { root.deleteRecursively() }
    }
}
