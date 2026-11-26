/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.graphics.BitmapFactory
import kotlin.math.max

/**
 * Focused artwork sizing & decode safety helpers split from [MediaUtils] so embedded
 * cover decoding can downsample cleanly without OOM allocations.
 */
object ArtworkMediaUtils {

    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    fun decodeBoundedBitmap(bytes: ByteArray, maxDimensionPx: Int): android.graphics.Bitmap? {
        if (bytes.isEmpty() || maxDimensionPx <= 0) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimensionPx, maxDimensionPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }
}
