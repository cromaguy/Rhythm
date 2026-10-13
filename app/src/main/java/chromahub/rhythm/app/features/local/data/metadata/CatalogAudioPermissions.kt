// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Consent uses the same volume-specific URI as the actual tag and lyrics writers. */
object CatalogAudioPermissions {
    suspend fun requests(context: Context, songs: List<Song>): List<IntentSender> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext emptyList()
        val uris = songs.map { song -> song.id.toLongOrNull()?.let { MediaUtils.getSpecificVolumeUri(context, it) } ?: song.uri }
            .filter { it.toString().startsWith("content://media/") }.distinct()
        if (Build.VERSION.SDK_INT >= 30) {
            uris.filter { uri ->
                !runCatching { context.contentResolver.openFileDescriptor(uri, "rw")?.use { true } == true }.getOrDefault(false)
            }.chunked(2000).map { MediaStore.createWriteRequest(context.contentResolver, it).intentSender }
        } else {
            uris.mapNotNull { uri ->
                try { context.contentResolver.openFileDescriptor(uri, "rw")?.close(); null }
                catch (denied: RecoverableSecurityException) { denied.userAction.actionIntent.intentSender }
                catch (_: SecurityException) { null }
            }
        }
    }
}
