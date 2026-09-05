/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import chromahub.rhythm.app.infrastructure.widget.glance.GlanceShapeBitmaps
import chromahub.rhythm.app.infrastructure.widget.glance.RhythmCookieWidget
import chromahub.rhythm.app.infrastructure.widget.glance.RhythmMusicWidget
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.ANRWatchdog
import chromahub.rhythm.app.util.CacheManager
import chromahub.rhythm.app.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom Application class for Rhythm Music Player.
 * Handles initialization of:
 * - AppSettings
 * - CrashReporter
 * - NetworkClient
 * - LeakCanary (debug builds)
 * - ANR Watchdog (debug builds)
 */
class RhythmApplication : Application(), ImageLoaderFactory {
    
    companion object {
        private const val TAG = "RhythmApplication"

        
        // Static reference to the application instance
        // Using a static reference is safe for Application class
        lateinit var instance: RhythmApplication
            private set
    }
    
    private var anrWatchdog: ANRWatchdog? = null
    
    override fun onCreate() {
        super.onCreate()
        
        instance = this
        
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "RhythmApplication onCreate")
        Log.d(TAG, "Build Type: ${BuildConfig.BUILD_TYPE}")
        Log.d(TAG, "Version: ${BuildConfig.VERSION_NAME}")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        
        // Initialize AppSettings early (singleton, uses application context)
        val settings = AppSettings.getInstance(applicationContext)
        Log.d(TAG, "✓ AppSettings initialized")

        // Apply the user's theme preference to the system configuration so the
        // system splash screen and window background match the app theme
        // (light in light mode, dark in dark mode) instead of always being dark.
        val nightMode = if (settings.useSystemTheme.value) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else if (settings.darkMode.value) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        Log.d(TAG, "✓ Applied night mode: $nightMode")
        
        // Initialize CrashReporter
        CrashReporter.init(this)
        Log.d(TAG, "✓ CrashReporter initialized")
        
        // Initialize NetworkClient with AppSettings
        chromahub.rhythm.app.network.NetworkClient.initialize(
            AppSettings.getInstance(applicationContext)
        )
        Log.d(TAG, "✓ NetworkClient initialized")
        
        // Configure LeakCanary for debug builds
        if (BuildConfig.DEBUG) {
            configureLeakCanary()
            startANRWatchdog()
        }
        
        Log.d(TAG, "RhythmApplication initialization complete")
        
        // Trim caches on startup so storage stays within the user's chosen limit
        // even if the app was force-stopped (onDestroy trim never ran). Gated to
        // at most once per day so low-end devices don't walk the cache directories
        // on every single launch.
        val trimPrefs = getSharedPreferences("cache_trim", android.content.Context.MODE_PRIVATE)
        val lastTrimMs = trimPrefs.getLong("last_startup_trim_ms", 0L)
        val dayMs = 24L * 60 * 60 * 1000
        if (System.currentTimeMillis() - lastTrimMs >= dayMs) {
            trimPrefs.edit { putLong("last_startup_trim_ms", System.currentTimeMillis()) }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CacheManager.autoTrimCache(applicationContext, currentMaxCacheSize())
                } catch (e: Exception) {
                    Log.e(TAG, "Error during startup cache trim", e)
                }
            }
        }
    }

    /**
     * Returns the user-configured max cache size or a sane fallback if
     * settings are unavailable.
     */
    private fun currentMaxCacheSize(): Long {
        return try {
            AppSettings.getInstance(this).maxCacheSize.value
        } catch (e: Exception) {
            300L * 1024 * 1024
        }
    }

    /**
     * Bounded Coil ImageLoader used app-wide with on-demand audio artwork decoding.
     * The factory defaults keep the disk cache at ~2% of total disk space and the memory
     * cache at 25% of heap, which is far too large for an offline music app — bound both explicitly.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(chromahub.rhythm.app.util.coil.AudioArtworkKeyer())
                add(chromahub.rhythm.app.util.coil.StreamingArtworkKeyer())
                add(chromahub.rhythm.app.util.coil.AudioArtworkFetcher.Factory(applicationContext))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // 15% of app heap (default is 25%)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128 MB (default is ~2% of disk)
                    .build()
            }
            .crossfade(true)
            .build()
    }
    
    /**
     * Configure LeakCanary for optimal memory leak detection
     */
    private fun configureLeakCanary() {
        try {
            // LeakCanary 2.x auto-configures itself, but we can still apply debug-only tuning.
            // Reflection keeps main source free from debugImplementation class references.
            val debugConfigClass = Class.forName("chromahub.rhythm.app.debug.LeakCanaryDebugConfig")
            val applyMethod = debugConfigClass.getDeclaredMethod("applyKnownReferenceMatchers")
            applyMethod.invoke(null)

            Log.d(TAG, "✓ LeakCanary configured (auto-init + debug matcher tuning)")
        } catch (_: ClassNotFoundException) {
            Log.d(TAG, "✓ LeakCanary configured (auto-init)")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring LeakCanary", e)
        }
    }
    
    /**
     * Start ANR watchdog to monitor UI thread responsiveness
     */
    private fun startANRWatchdog() {
        try {
            // Start with 5 second timeout (standard ANR threshold)
            anrWatchdog = ANRWatchdog(timeoutMs = 5000).apply {
                start()
            }
            Log.d(TAG, "✓ ANR Watchdog started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ANR Watchdog", e)
        }
    }
    
    override fun onTerminate() {
        Log.d(TAG, "RhythmApplication onTerminate")
        
        // Stop ANR watchdog
        anrWatchdog?.stopWatching()
        anrWatchdog = null
        
        super.onTerminate()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        val levelName = when {
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            level == ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            level == 5 -> "RUNNING_MODERATE"
            level == 10 -> "RUNNING_LOW"
            level == 15 -> "RUNNING_CRITICAL"
            level == 60 -> "MODERATE"
            level == 80 -> "COMPLETE"
            else -> "UNKNOWN($level)"
        }
        
        Log.w(TAG, "onTrimMemory: $levelName")
        
        // Trim levels are inverted: lower numbers mean MORE pressure (5 = critical,
        // 10 = low, 15 = moderate). Release image caches whenever the app is under
        // real pressure OR fully backgrounded — this is the main OOM fix when many
        // apps are open.
        val isBackgroundOrCritical = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        if (isBackgroundOrCritical) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Coil.imageLoader(applicationContext).memoryCache?.clear()
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing Coil memory cache", e)
                }
                GlanceShapeBitmaps.clearCache()
                RhythmMusicWidget.clearArtCache()
                RhythmCookieWidget.clearArtCache()
            }
        }
        
        // Light cleanup when the app moves to the background
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Log.d(TAG, "App backgrounded - trimming caches")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CacheManager.autoTrimCache(applicationContext, currentMaxCacheSize())
                } catch (e: Exception) {
                    Log.e(TAG, "Error during background cache trim", e)
                }
            }
        }
    }
}
