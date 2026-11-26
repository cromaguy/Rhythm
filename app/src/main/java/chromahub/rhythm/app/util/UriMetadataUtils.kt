/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Focused MediaStore content-URI & volume resolution helpers split out from [MediaUtils].
 */
object UriMetadataUtils {

    fun getSpecificVolumeUri(context: Context, songId: Long): Uri? {
        if (songId <= 0L) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val volumeName = try {
                val projection = arrayOf(MediaStore.Audio.Media.VOLUME_NAME)
                val selection = "${MediaStore.Audio.Media._ID} = ?"
                val selectionArgs = arrayOf(songId.toString())
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME))
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }

            val targetVolume = if (volumeName != null && volumeName != MediaStore.VOLUME_EXTERNAL) {
                volumeName
            } else {
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            }
            MediaStore.Audio.Media.getContentUri(targetVolume, songId)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        }
    }
}
