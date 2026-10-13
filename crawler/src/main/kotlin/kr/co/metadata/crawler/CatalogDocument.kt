// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlinx.serialization.json.*

/** One user-selected catalog entry. Keep field provenance through preview and import. */
data class CatalogValue(val field: CatalogField, val value: String, val sourceUrl: String)

data class CatalogDocument(val kind: EntityKind, val values: List<CatalogValue>) {
    fun has(field: CatalogField): Boolean = values.firstOrNull { it.field == field }?.hasValue() == true
    operator fun get(field: CatalogField): String? = values.firstOrNull { it.field == field }?.value

    companion object {
        private fun <T> value(field: CatalogField, evidence: Evidence<T>?, format: (T) -> String): CatalogValue? =
            evidence?.let { CatalogValue(field, format(it.value), it.pageUrl) }?.takeIf { it.value.isNotBlank() }

        fun from(track: TrackMetadata) = CatalogDocument(EntityKind.TRACK, listOfNotNull(
            value(CatalogField.TRACK_TITLE, track.title) { it },
            value(CatalogField.TRACK_ARTISTS, track.artists) { it.joinToString(", ") },
            value(CatalogField.TRACK_ALBUM_TITLE, track.albumTitle) { it },
            value(CatalogField.TRACK_GENRES, track.genres) { it.joinToString(", ") },
            value(CatalogField.TRACK_RELEASE_DATE, track.releaseDate) { it },
            value(CatalogField.TRACK_IMAGE, track.imageUrl) { it },
            value(CatalogField.TRACK_LYRICS, track.lyrics) { it },
            value(CatalogField.TRACK_DURATION_MS, track.durationMs) { it.toString() },
        ))

        fun from(album: AlbumMetadata) = CatalogDocument(EntityKind.ALBUM, listOfNotNull(
            value(CatalogField.ALBUM_TITLE, album.title) { it },
            value(CatalogField.ALBUM_ARTISTS, album.artists) { it.joinToString(", ") },
            value(CatalogField.ALBUM_GENRES, album.genres) { it.joinToString(", ") },
            value(CatalogField.ALBUM_RELEASE_DATE, album.releaseDate) { it },
            value(CatalogField.ALBUM_IMAGE, album.imageUrl) { it },
            value(CatalogField.ALBUM_DESCRIPTION, album.description) { it },
        ))

        fun from(artist: ArtistMetadata) = CatalogDocument(EntityKind.ARTIST, listOfNotNull(
            value(CatalogField.ARTIST_NAME, artist.name) { it },
            value(CatalogField.ARTIST_DESCRIPTION, artist.description) { it },
            value(CatalogField.ARTIST_IMAGE, artist.profileImageUrl) { it },
            value(CatalogField.ARTIST_DEBUT_DATE, artist.debutDate) { it },
            value(CatalogField.ARTIST_GENRES, artist.genres) { it.joinToString(", ") },
        ))

        fun from(profile: DynamicSiteProfile, values: List<DynamicFieldValue>) =
            CatalogDocument(profile.kind, values.map { CatalogValue(it.field, it.value, it.sourceUrl) })
    }
}

/** Stable hand-written JSON keys also work in minified Android builds without reflection. */
object CatalogDocumentCodec {
    fun encode(document: CatalogDocument): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("kind", JsonPrimitive(document.kind.name))
            put("values", JsonArray(document.values.map { value ->
                buildJsonObject {
                    put("field", JsonPrimitive(value.field.name))
                    put("value", JsonPrimitive(value.value))
                    put("sourceUrl", JsonPrimitive(value.sourceUrl))
                }
            }))
        },
    )

    fun decode(source: String): CatalogDocument {
        require(source.length <= 1_000_000)
        val root = Json.parseToJsonElement(source) as JsonObject
        fun JsonObject.string(key: String) =
            (getValue(key) as JsonPrimitive).content
        val kind = EntityKind.valueOf(root.string("kind"))
        val values = root.getValue("values") as JsonArray
        require(values.size <= CatalogField.entries.size)
        return CatalogDocument(kind, values.map {
            val item = it as JsonObject
            val field = CatalogField.valueOf(item.string("field"))
            require(field.kind == kind)
            CatalogValue(field, item.string("value"), item.string("sourceUrl"))
        }.also { require(it.map { value -> value.field }.distinct().size == it.size) })
    }
}

fun CatalogValue.hasValue(): Boolean = !CatalogIdentity.missing(value) && when (field) {
    CatalogField.TRACK_RELEASE_DATE, CatalogField.ALBUM_RELEASE_DATE, CatalogField.ARTIST_DEBUT_DATE -> value.yearOrNull() != null
    else -> true
}
