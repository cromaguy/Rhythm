// SPDX-License-Identifier: GPL-3.0-or-later

package chromahub.rhythm.app.features.local.presentation.metadata

import android.content.Context
import chromahub.rhythm.app.R
import kr.co.metadata.crawler.CatalogField
import kr.co.metadata.crawler.EntityKind

fun EntityKind.label(context: Context): String = context.getString(when (this) {
    EntityKind.TRACK -> R.string.catalog_kind_track
    EntityKind.ALBUM -> R.string.catalog_kind_album
    EntityKind.ARTIST -> R.string.catalog_kind_artist
})

fun CatalogField.label(context: Context): String = context.getString(when (this) {
    CatalogField.TRACK_DURATION_MS -> R.string.catalog_track_duration
    CatalogField.TRACK_TITLE -> R.string.bottomsheet_title
    CatalogField.ARTIST_NAME, CatalogField.TRACK_ARTISTS, CatalogField.ALBUM_ARTISTS -> R.string.player_chip_artist
    CatalogField.TRACK_ALBUM_TITLE, CatalogField.ALBUM_TITLE -> R.string.player_chip_album
    CatalogField.ARTIST_GENRES, CatalogField.TRACK_GENRES, CatalogField.ALBUM_GENRES -> R.string.bottomsheet_genre
    CatalogField.TRACK_RELEASE_DATE, CatalogField.ALBUM_RELEASE_DATE -> R.string.catalog_release_date
    CatalogField.ARTIST_DEBUT_DATE -> R.string.catalog_debut_date
    CatalogField.ALBUM_DESCRIPTION -> R.string.catalog_album_description
    CatalogField.ARTIST_DESCRIPTION -> R.string.catalog_description
    CatalogField.ARTIST_IMAGE, CatalogField.TRACK_IMAGE, CatalogField.ALBUM_IMAGE -> R.string.catalog_image
    CatalogField.TRACK_LYRICS -> R.string.metadata_lyrics
})
