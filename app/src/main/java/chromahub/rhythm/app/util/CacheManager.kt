/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility class for managing app cache operations
 */
object CacheManager {
    private const val TAG = "CacheManager"
    
    /**
     * Gets the current cache usage in bytes (reported to the settings screen).
     * Includes the stable embedded-artwork cache in filesDir so the number
     * reflects what the user actually sees under Android Settings.
     */
    suspend fun getCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        
        try {
            // Internal cache
            totalSize += calculateDirectorySize(context.cacheDir)
            
            // Embedded artwork extracted during scans lives in filesDir (counts as
            // App data in Android settings, not cache). It is reported here but NOT
            // auto-trimmed — it self-bounds at extraction time and is stable so the
            // app never re-extracts the whole library on every launch.
            totalSize += getEmbeddedArtworkCacheSize(context.filesDir)
            
            // External cache
            context.externalCacheDir?.let { externalCache ->
                totalSize += calculateDirectorySize(externalCache)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
        }
        
        return@withContext totalSize
    }
    
    /**
     * Size of the volatile caches only (cacheDir + externalCacheDir). This is what
     * auto-trim actually manages — the stable filesDir artwork cache is deliberately
     * excluded so trimming it doesn't force a full re-extraction on next launch.
     */
    private suspend fun getTrimDecisionSize(context: Context): Long = withContext(Dispatchers.IO) {
        var size = calculateDirectorySize(context.cacheDir)
        context.externalCacheDir?.let { externalCache ->
            size += calculateDirectorySize(externalCache)
        }
        size
    }
    
    /**
     * Calculates the size of a directory recursively
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        
        try {
            if (!directory.exists()) return 0L
            
            val files = directory.listFiles() ?: return 0L
            
            for (file in files) {
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating directory size: ${directory.absolutePath}", e)
        }
        
        return size
    }
    
    /**
     * Cleans cache if it exceeds the specified size limit
     */
    suspend fun cleanCacheIfNeeded(context: Context, maxSizeBytes: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentSize = getTrimDecisionSize(context)
            Log.d(TAG, "Current cache size: ${currentSize / (1024 * 1024)}MB, limit: ${maxSizeBytes / (1024 * 1024)}MB")
            
            if (currentSize > maxSizeBytes) {
                Log.d(TAG, "Cache size exceeds limit, starting cleanup...")
                
                // First try to clean old files
                val cleaned = cleanOldCacheFiles(context, maxSizeBytes * 0.8.toLong()) // Clean to 80% of limit
                
                if (!cleaned) {
                    // If that didn't work, do a full cache clear
                    clearAllCache(context)
                }
                
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in cache size management", e)
        }
        
        return@withContext false
    }

    /**
     * Automatically trims cache when usage reaches 90% of the max size.
     * Cleans to 80% of the limit when triggered, maintaining headroom.
     */
    suspend fun autoTrimCache(context: Context, maxSizeBytes: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentSize = getTrimDecisionSize(context)
            val threshold = (maxSizeBytes * 0.9).toLong()
            Log.d(TAG, "Current cache size: ${currentSize / (1024 * 1024)}MB, 90% threshold: ${threshold / (1024 * 1024)}MB")
            
            if (currentSize > threshold) {
                Log.d(TAG, "Cache size exceeds 90% threshold (${currentSize / (1024 * 1024)}MB / ${maxSizeBytes / (1024 * 1024)}MB), auto-trimming...")
                val cleaned = cleanOldCacheFiles(context, (maxSizeBytes * 0.8).toLong())
                if (!cleaned) {
                    clearAllCache(context)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto cache trim", e)
        }
        return@withContext false
    }
    
    /**
     * Cleans old cache files based on last modified time
     */
    private suspend fun cleanOldCacheFiles(context: Context, targetSize: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFiles = mutableListOf<File>()
            
            // Collect all cache files with their modification times. The stable
            // filesDir embedded-artwork cache is intentionally NOT included — it is
            // self-bounding and evicting it would trigger full re-extraction later.
            collectCacheFiles(context.cacheDir, cacheFiles)
            context.externalCacheDir?.let { 
                collectCacheFiles(it, cacheFiles) 
            }
            
            // Sort by last modified (oldest first)
            cacheFiles.sortBy { it.lastModified() }
            
            var currentSize = calculateTotalSize(cacheFiles)
            var deletedCount = 0
            
            // Delete files until we're under the target size
            for (file in cacheFiles) {
                if (currentSize <= targetSize) break
                
                try {
                    val fileSize = file.length()
                    if (file.delete()) {
                        currentSize -= fileSize
                        deletedCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete cache file: ${file.absolutePath}", e)
                }
            }
            
            Log.d(TAG, "Cleaned $deletedCount old cache files, new size: ${currentSize / (1024 * 1024)}MB")
            return@withContext currentSize <= targetSize
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old cache files", e)
            return@withContext false
        }
    }
    
    private fun collectCacheFiles(directory: File, fileList: MutableList<File>) {
        try {
            if (!directory.exists()) return
            
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                if (file.isFile) {
                    fileList.add(file)
                } else if (file.isDirectory) {
                    collectCacheFiles(file, fileList)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error collecting cache files from: ${directory.absolutePath}", e)
        }
    }
    
    private fun calculateTotalSize(files: List<File>): Long {
        return files.sumOf { file ->
            try {
                if (file.exists()) file.length() else 0L
            } catch (e: Exception) {
                0L
            }
        }
    }
    
    /**
     * Clears all cached data including:
     * - Internal cache directory
     * - External cache directory (if available)
     * - Coil image cache (memory and disk)
     * 
     * @param context Application context
     * @param imageLoader Coil ImageLoader instance for clearing image cache
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAllCache(
        context: Context, 
        imageLoader: ImageLoader? = null
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting cache cleanup...")
            
            // Clear internal cache directory
            clearCacheDirectory(context.cacheDir)
            
            // Clear scan-time embedded artwork from filesDir (regenerable from tags)
            clearCacheDirectory(File(context.filesDir, "embedded_artwork"))
            context.filesDir.listFiles { file ->
                file.isFile &&
                    (file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_"))
            }?.forEach { file -> file.delete() }
            
            // Clear external cache directory if available
            context.externalCacheDir?.let { externalCache ->
                clearCacheDirectory(externalCache)
            }
            
            MediaUtils.clearRawArtworkCache()
            
            // Clear Coil image cache
            val loader = imageLoader ?: runCatching { coil.Coil.imageLoader(context) }.getOrNull()
            loader?.let { resolvedLoader ->
                try {
                    // Clear memory cache
                    resolvedLoader.memoryCache?.clear()
                    Log.d(TAG, "Cleared Coil memory cache")
                    
                    // Clear disk cache
                    resolvedLoader.diskCache?.clear()
                    Log.d(TAG, "Cleared Coil disk cache")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing Coil cache", e)
                }
            }
            
            Log.d(TAG, "Cache cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
        }
    }
    
    /**
     * Clears a specific cache directory recursively
     * 
     * @param cacheDir The cache directory to clear
     */
    private fun clearCacheDirectory(cacheDir: File) {
        try {
            if (!cacheDir.exists()) {
                Log.d(TAG, "Cache directory doesn't exist: ${cacheDir.absolutePath}")
                return
            }
            
            val filesDeleted = deleteRecursively(cacheDir, false) // Don't delete the directory itself
            Log.d(TAG, "Cleared cache directory: ${cacheDir.absolutePath}, files deleted: $filesDeleted")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache directory: ${cacheDir.absolutePath}", e)
        }
    }
    
    /**
     * Recursively deletes files and directories
     * 
     * @param file The file or directory to delete
     * @param deleteRoot Whether to delete the root directory itself
     * @return Number of files/directories deleted
     */
    private fun deleteRecursively(file: File, deleteRoot: Boolean = true): Int {
        var deletedCount = 0
        
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deletedCount += deleteRecursively(child, true)
                }
                
                if (deleteRoot && file.delete()) {
                    deletedCount++
                }
            } else {
                if (file.delete()) {
                    deletedCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
        }
        
        return deletedCount
    }
    
    
    /**
     * Gets detailed cache size breakdown
     * 
     * @param context Application context
     * @return Map of cache type to size in bytes
     */
    suspend fun getDetailedCacheSize(
        context: Context
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        val details = mutableMapOf<String, Long>()
        
        try {
            val embeddedArtworkSize = getEmbeddedArtworkCacheSize(context.cacheDir)
            val internalCacheSize = getDirectorySize(context.cacheDir)
            val nonArtworkInternalSize = (internalCacheSize - embeddedArtworkSize).coerceAtLeast(0L)

            details["Internal Cache (Other)"] = nonArtworkInternalSize
            details["Embedded Artwork Cache"] = embeddedArtworkSize

            // Scan-time artwork is written to filesDir (counts as App data)
            details["Embedded Artwork (App Data)"] = getEmbeddedArtworkCacheSize(context.filesDir)
            
            // External cache size
            context.externalCacheDir?.let { externalCache ->
                details["External Cache"] = getDirectorySize(externalCache)
            } ?: run {
                details["External Cache"] = 0L
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating detailed cache size", e)
        }
        
        return@withContext details
    }

    private fun getEmbeddedArtworkCacheSize(cacheDir: File): Long {
        return collectEmbeddedArtworkFiles(cacheDir).sumOf { file ->
            try {
                file.length()
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * Collects embedded artwork files in a base dir: the `embedded_artwork`
     * subdir plus any legacy `embedded_art_*` files directly in the base dir.
     */
    private fun collectEmbeddedArtworkFiles(baseDir: File): List<File> {
        val files = mutableListOf<File>()

        val artworkCacheDir = File(baseDir, "embedded_artwork")
        artworkCacheDir.listFiles { file -> file.isFile }?.let { files.addAll(it) }

        baseDir.listFiles { file ->
            file.isFile &&
                (file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_"))
        }?.let { files.addAll(it) }

        return files
    }
    
    /**
     * Calculates the size of a directory recursively
     * 
     * @param directory The directory to calculate size for
     * @return Size in bytes
     */
    fun getDirectorySize(directory: File): Long {
        var size = 0L
        
        try {
            if (!directory.exists()) return 0L
            
            if (directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) {
                        getDirectorySize(file)
                    } else {
                        file.length()
                    }
                }
            } else {
                size = directory.length()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating directory size: ${directory.absolutePath}", e)
        }
        
        return size
    }
    
    /**
     * Formats bytes into a human-readable string
     * 
     * @param bytes Size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return if (unitIndex == 0) {
            "${size.toInt()} ${units[unitIndex]}"
        } else {
            "%.1f %s".format(size, units[unitIndex])
        }
    }
    
    /**
     * Clears only image-related cache
     * 
     * @param context Application context
     * @param imageLoader Coil ImageLoader instance
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearImageCache(context: Context, imageLoader: ImageLoader? = null) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Clearing image cache...")
            
            MediaUtils.clearRawArtworkCache()
            MediaUtils.clearArtworkDiskCache(context.cacheDir)
            
            // Clear Coil image cache
            imageLoader?.let { loader ->
                try {
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    Log.d(TAG, "Cleared Coil image cache")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing Coil image cache", e)
                }
            }
            
            // Clear generated placeholder images from internal cache
            val cacheDir = context.cacheDir
            cacheDir.listFiles { _, name ->
                name.startsWith("placeholder_") && name.endsWith(".png")
            }?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted placeholder image: ${file.name}")
                }
            }
            
            Log.d(TAG, "Image cache cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing image cache", e)
        }
    }
}
