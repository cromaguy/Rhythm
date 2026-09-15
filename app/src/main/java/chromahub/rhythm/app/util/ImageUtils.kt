/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.compose.AsyncImagePainter
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Utility class for handling image-related operations
 */
object ImageUtils {
    
    private const val TAG = "ImageUtils"
    
    /**
     * Generates a placeholder image with the first letter of the name
     * @param name The name to use for the placeholder
     * @param size The size of the bitmap to generate
     * @param cacheDir The directory to cache the generated image
     * @return Uri to the generated image or null if generation failed
     */
    fun generatePlaceholderImage(name: String?, size: Int = 300, cacheDir: File): Uri? {
        // Handle null or empty name
        val safeName = if (name.isNullOrBlank()) "?" else name
        
        return try {
            // Ensure cache directory exists
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs() && !cacheDir.exists()) {
                    Log.e(TAG, "Failed to create cache directory")
                    // Try to use app-specific cache as fallback
                    if (cacheDir.parentFile == null || !cacheDir.parentFile!!.exists()) {
                        Log.e(TAG, "Parent cache directory doesn't exist")
                        return null
                    }
                }
            }
            
            val letter = safeName.firstOrNull()?.uppercase() ?: "?"
            val color = getColorForName(safeName)
            
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw background
            val paint = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            
            // Draw text
            paint.apply {
                this.color = Color.WHITE
                textSize = size * 0.5f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            
            val textBounds = Rect()
            paint.getTextBounds(letter, 0, letter.length, textBounds)
            
            val x = size / 2f
            val y = size / 2f + textBounds.height() / 2f - textBounds.bottom
            
            canvas.drawText(letter, x, y, paint)
            
            // Create a unique filename based on name and size
            val filename = "placeholder_${safeName.hashCode()}_${size}.png"
            val file = File(cacheDir, filename)
            
            // Check if file already exists
            if (file.exists()) {
                return file.toUri()
            }
            
            // Save to cache
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            
            file.toUri()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating placeholder image for '$safeName': ${e.message}", e)
            null
        }
    }
    
    /**
     * Generates a consistent color based on the name
     */
    private fun getColorForName(name: String): Int {
        val seed = name.hashCode()
        val random = Random(seed)
        
        // Generate a vibrant color
        val hue = random.nextFloat() * 360f
        val saturation = 0.7f + random.nextFloat() * 0.3f
        val value = 0.5f + random.nextFloat() * 0.3f
        
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    
    /**
     * Builds an image request with a placeholder
     * 
     * NOTE: For Compose UI, use M3ImageUtils instead which provides Material 3
     * placeholders directly in Compose.
     */
    fun buildImageRequest(
        data: Any?,
        name: String?,
        cacheDir: File,
        type: M3PlaceholderType = M3PlaceholderType.GENERAL,
        lossless: Boolean = false
    ): ImageRequest.Builder.() -> Unit = {
        // Set the main data source
        data(data)
        
        // Enable crossfade animation with minimal duration to reduce blocking
        crossfade(true)
        crossfade(300) // Further reduced to minimize main thread work
        
        // Use a simple drawable placeholder instead of generating one dynamically
        // This avoids expensive file I/O operations on the main thread
        placeholder(R.drawable.rhythm_logo)
        error(R.drawable.rhythm_logo)
        
        // Add memory caching with a safe key
        val safeKey = when {
            data != null -> try { data.toString() } catch (e: Exception) { "default_key" }
            !name.isNullOrBlank() -> name
            else -> "default_key"
        }
        memoryCacheKey(safeKey)
        
        // Optimize cache policies for better performance
        networkCachePolicy(coil.request.CachePolicy.ENABLED)
        diskCachePolicy(coil.request.CachePolicy.ENABLED)
        memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        
        // Set bitmap format — use ARGB_8888 for lossless quality, RGB_565 for memory savings
        bitmapConfig(if (lossless) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        
        // Add a listener to handle the result
        listener(
            onSuccess = { _, result ->
                // Disable success logging entirely to eliminate main thread overhead
                // Success is implied by the image being displayed
            },
            onError = { _, result ->
                // Only log critical errors occasionally to reduce overhead
                if (System.currentTimeMillis() % 100 == 0L) {
                    Log.w(TAG, "Image load error: ${result.throwable.message}")
                }
            }
        )
        
        // Set reasonable timeouts
        networkCachePolicy(coil.request.CachePolicy.ENABLED)
        diskCachePolicy(coil.request.CachePolicy.ENABLED)
        memoryCachePolicy(coil.request.CachePolicy.ENABLED)
    }

    /**
     * Loads an artwork bitmap safely with downsampling to prevent OutOfMemoryError.
     * Supports both remote (http/https) and local (content/file) URIs.
     */
    suspend fun loadArtworkBitmap(
        context: android.content.Context,
        uri: Uri,
        maxSize: Int = 512
    ): Bitmap? {
        return try {
            val isRemote = uri.scheme == "http" || uri.scheme == "https"
            if (isRemote) {
                val request = ImageRequest.Builder(context)
                    .data(uri.toString())
                    .memoryCacheKey(uri.toString())
                    .size(maxSize)
                    .crossfade(false)
                    .allowHardware(false)
                    .build()
                val result = coil.Coil.imageLoader(context).execute(request)
                val bitmapDrawable = result.drawable as? android.graphics.drawable.BitmapDrawable
                if (bitmapDrawable != null) {
                    return bitmapDrawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
                val bytes = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: run {
                        val url = java.net.URL(uri.toString())
                        val peekOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        val conn = url.openConnection()
                        conn.connectTimeout = 3000
                        conn.readTimeout = 5000
                        conn.getInputStream().use { android.graphics.BitmapFactory.decodeStream(it, null, peekOpts) }
                        val sample = calculateInSampleSize(peekOpts.outWidth, peekOpts.outHeight, maxSize)
                        val conn2 = url.openConnection()
                        conn2.connectTimeout = 3000
                        conn2.readTimeout = 5000
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                        conn2.getInputStream().use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                    }
                bytes
            } else {
                val peekOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, peekOpts)
                }
                val srcW = peekOpts.outWidth
                val srcH = peekOpts.outHeight
                val sample = calculateInSampleSize(srcW, srcH, maxSize)
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading artwork bitmap for $uri", e)
            null
        }
    }

    /**
     * Calculate a power-of-two [inSampleSize] so the decoded bitmap fits within [maxSize]
     * on its longest dimension. On zero / missing dimensions returns 1 (no downscale).
     */
    fun calculateInSampleSize(srcW: Int, srcH: Int, maxSize: Int): Int {
        if (srcW <= 0 || srcH <= 0 || maxSize <= 0) return 1
        val longest = maxOf(srcW, srcH)
        var sample = 1
        while (longest / (sample * 2) >= maxSize) sample *= 2
        return sample
    }
}
