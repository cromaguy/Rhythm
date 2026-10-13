// SPDX-License-Identifier: GPL-3.0-or-later

package chromahub.rhythm.app.features.local.data.metadata

import androidx.core.content.edit
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.metadata.crawler.*
import java.io.File
import java.net.URI
import java.util.UUID

/** Shared HTTP client keeps provider requests throttled across searches and details. */
class CatalogImportRepository {
    private val http = PageClient()
    private val search = CatalogSearch(http)
    private val metadata = MusicMetadataRepository(listOf(
        BugsMobileProvider(http), BugsPcProvider(http), GenieProvider(http), MelonProvider(http),
    ))

    suspend fun search(provider: Provider, kind: EntityKind, query: String) = search.search(provider, kind, query)

    suspend fun load(hit: SearchHit): CatalogDocument? = withContext(Dispatchers.IO) {
        val ids = if (hit.provider in setOf(Provider.BUGS_MOBILE, Provider.BUGS_PC)) {
            // Only the two Bugs views share catalog IDs. Never reuse them for Genie or Melon.
            mapOf(Provider.BUGS_MOBILE to hit.providerId, Provider.BUGS_PC to hit.providerId)
        } else mapOf(hit.provider to hit.providerId)
        val lookup = MusicLookup(ids)
        when (hit.kind) {
            EntityKind.TRACK -> metadata.track(lookup)?.let(CatalogDocument::from)
            EntityKind.ALBUM -> metadata.album(lookup)?.let(CatalogDocument::from)
            EntityKind.ARTIST -> metadata.artist(lookup)?.let(CatalogDocument::from)
        }
    }

    suspend fun artwork(context: Context, url: String): Uri = withContext(Dispatchers.IO) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        val result = context.imageLoader.execute(ImageRequest.Builder(context)
            .data(url).size(1200).allowHardware(false).build()) as? SuccessResult
            ?: error("Artwork could not be downloaded")
        val file = File(context.cacheDir, "catalog-artwork-${UUID.randomUUID()}.jpg")
        file.outputStream().use {
            check(result.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 92, it))
        }
        Uri.fromFile(file)
    }
}

/** Verified rules only; no page HTML, picked sample text, or lyrics are persisted here. */
class SiteProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("music_catalog_profiles", Context.MODE_PRIVATE)

    fun list(): List<DynamicSiteProfile> = preferences.all.values.mapNotNull {
        (it as? String)?.let { json -> runCatching { SiteProfileCodec.decode(json) }.getOrNull() }
    }.sortedBy { it.displayName }

    fun save(profile: DynamicSiteProfile) {
        preferences.edit { putString(profile.id, SiteProfileCodec.encode(profile)) }
    }

    fun delete(profile: DynamicSiteProfile) { preferences.edit { remove(profile.id) } }
}
