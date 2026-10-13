// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.core.domain.scan

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rule editing must see excluded media too; the playback library intentionally cannot. */
object ScanRuleLibrary {
    suspend fun load(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val fields = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA)
        val result = mutableListOf<Song>()
        context.contentResolver.query(collection, fields, null, null, MediaStore.Audio.Media.TITLE + " ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                result += Song(id.toString(), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(), cursor.getString(3).orEmpty(),
                    albumId = cursor.getLong(4).toString(), duration = cursor.getLong(5), uri = ContentUris.withAppendedId(collection, id), path = cursor.getString(6))
            }
        }
        result
    }
}
