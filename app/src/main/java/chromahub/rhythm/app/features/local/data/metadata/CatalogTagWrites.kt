// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import androidx.core.content.edit
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.MediaUtils
import kr.co.metadata.crawler.CatalogFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatalogTagWrites(private val context: Context) {
    private val prefs = context.getSharedPreferences("catalog_tag_writes", Context.MODE_PRIVATE)
    fun add(song: Song) { prefs.edit(commit = true) { putString(CatalogFileStore.hash(song.uri.toString()), CatalogSongCodec.encode(song)) } }
    fun pending(): List<Song> = prefs.all.values.mapNotNull { (it as? String)?.let { json -> runCatching { CatalogSongCodec.decode(json) }.getOrNull() } }
    suspend fun attempt(song: Song): Boolean = withContext(Dispatchers.IO) {
      CatalogEnrichmentService.get(context).withAudioWrite(song) {
        if (!CatalogEnrichmentService.get(context).preferences.options.value.writeTags) return@withAudioWrite false
        val uri = song.id.toLongOrNull()?.let { MediaUtils.getSpecificVolumeUri(context, it) } ?: song.uri
        val writable = runCatching { context.contentResolver.openFileDescriptor(uri, "rw")?.use { true } ?: false }.getOrDefault(false)
        if (!writable) return@withAudioWrite false
        val latest = CatalogEnrichmentService.get(context).apply(song)
        val success = MediaUtils.updateSongMetadata(context, latest, latest.title, latest.artist, latest.album,
            latest.genre.orEmpty(), latest.year, latest.trackNumber, artworkUri = latest.artworkUri,
            newAlbumArtist = latest.albumArtist, newDiscNumber = latest.discNumber)
        if (success) prefs.edit(commit = true) { remove(CatalogFileStore.hash(song.uri.toString())) }
        success
      }
    }
}
