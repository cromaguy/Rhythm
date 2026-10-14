// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Shared optimized artwork format for catalog, manual edits, and extracted artwork. */
object ArtworkStorage {
    @Suppress("DEPRECATION")
    fun format(lossless: Boolean = false): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= 30)
        if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP
    fun write(bitmap: Bitmap, target: File, lossless: Boolean = false) {
        require(target.extension == "webp")
        val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
        val resized = if (scale < 1f && !lossless) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        try {
            val bytes = ByteArrayOutputStream().use { stream -> check(resized.compress(format(lossless), if (lossless) 100 else 86, stream)); stream.toByteArray() }
            PersonalArchive.atomicWrite(target, bytes)
        } finally { if (resized !== bitmap) resized.recycle() }
    }
    fun convertFile(source: File, target: File) {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(source.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = android.graphics.BitmapFactory.Options()
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        options.inSampleSize = sample
        val bitmap = android.graphics.BitmapFactory.decodeFile(source.path, options) ?: error("Invalid image")
        try { write(bitmap, target) } finally { bitmap.recycle() }
    }
    fun mime(bytes: ByteArray): String = when {
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        bytes.size >= 8 && bytes[0] == 137.toByte() && String(bytes, 1, 3, Charsets.US_ASCII) == "PNG" -> "image/png"
        bytes.size >= 2 && bytes[0] == 255.toByte() && bytes[1] == 216.toByte() -> "image/jpeg"
        else -> "application/octet-stream"
    }
    /** Audio cover containers and external players commonly require JPEG/PNG; keep app files WebP. */
    fun forEmbedding(bytes: ByteArray): Pair<ByteArray, String> {
        val mime = mime(bytes)
        if (mime != "image/webp") return bytes to mime
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Invalid cover")
        try {
            val alpha = bitmap.hasAlpha()
            val encoded = ByteArrayOutputStream().use { stream -> check(bitmap.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, stream)); stream.toByteArray() }
            return encoded to if (alpha) "image/png" else "image/jpeg"
        } finally { bitmap.recycle() }
    }
    suspend fun copy(context: Context, source: Uri, target: File, lossless: Boolean = false): Uri = withContext(Dispatchers.IO) {
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(source).size(1600).allowHardware(false).build()) as? SuccessResult
            ?: error("Invalid artwork")
        write(result.drawable.toBitmap(), target, lossless)
        Uri.fromFile(target)
    }
}
