/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * ANRWatchdog monitors the UI thread for Application Not Responding (ANR) situations.
 * It posts a task to the main thread and checks if it executes within the timeout period.
 * If the task doesn't execute in time, it logs an ANR with the main thread's stack trace.
 * 
 * Note: This watchdog is designed for DEBUG builds only and logs to logcat.
 * It does NOT show any user-facing dialogs or popups.
 */
class ANRWatchdog(
    private val timeoutMs: Long = 5000,
    private val onStallDetected: ((blockedMs: Long, stackTrace: Array<StackTraceElement>) -> Unit)? = null
) : Thread("ANRWatchdog") {
    
    companion object {
        private const val TAG = "ANRWatchdog"
        private const val STARTUP_GRACE_PERIOD_MS = 15000L // 15 seconds grace period after start
    }
    
    @Volatile
    private var shouldContinue = true
    private val uiHandler = Handler(Looper.getMainLooper())
    private val startTime = System.currentTimeMillis()
    
    init {
        isDaemon = true // Make this a daemon thread so it doesn't prevent app shutdown
    }
    
    override fun run() {
        Log.d(TAG, "ANR Watchdog started with timeout ${timeoutMs}ms")
        
        while (shouldContinue) {
            try {
                // Skip monitoring during startup grace period to avoid false positives
                val timeSinceStart = System.currentTimeMillis() - startTime
                if (timeSinceStart < STARTUP_GRACE_PERIOD_MS) {
                    sleep(1000)
                    continue
                }
                
                val start = System.currentTimeMillis()
                var responded = false
                
                // Post a task to the UI thread
                uiHandler.post {
                    responded = true
                }
                
                // Wait for the timeout period
                sleep(timeoutMs)
                
                // Check if the UI thread responded
                if (!responded && shouldContinue) {
                    val blockedTime = System.currentTimeMillis() - start
                    Log.e(TAG, "")
                    Log.e(TAG, "╔═══════════════════════════════════════════════════════════╗")
                    Log.e(TAG, "║                    ANR DETECTED!                          ║")
                    Log.e(TAG, "║  UI thread blocked for ${blockedTime}ms (threshold: ${timeoutMs}ms)  ║")
                    Log.e(TAG, "╚═══════════════════════════════════════════════════════════╝")
                    Log.e(TAG, "")
                    
                    // Get the main thread's stack trace
                    val mainThread = Looper.getMainLooper().thread
                    val stackTrace = mainThread.stackTrace
                    onStallDetected?.let { callback ->
                        runCatching { callback(blockedTime, stackTrace) }
                    }
                    
                    Log.e(TAG, "📍 Main thread (UI thread) stack trace:")
                    Log.e(TAG, "───────────────────────────────────────────────────────────")
                    stackTrace.forEach { element ->
                        Log.e(TAG, "    at $element")
                    }
                    Log.e(TAG, "───────────────────────────────────────────────────────────")
                    Log.e(TAG, "")
                    
                    // Also log all thread stack traces for more context
                    logAllThreads()
                }
                
                // Small sleep before next check to avoid excessive CPU usage
                if (responded) {
                    sleep(1000)
                }
                
            } catch (e: InterruptedException) {
                Log.d(TAG, "ANR Watchdog interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in ANR Watchdog", e)
            }
        }
        
        Log.d(TAG, "ANR Watchdog stopped")
    }
    
    /**
     * Logs all thread stack traces for debugging purposes
     */
    private fun logAllThreads() {
        try {
            val allThreads = Thread.getAllStackTraces()
            Log.e(TAG, "All threads (${allThreads.size} total):")
            allThreads.forEach { (thread, stackTrace) ->
                Log.e(TAG, "Thread: ${thread.name} (${thread.state})")
                stackTrace.take(10).forEach { element ->
                    Log.e(TAG, "    at $element")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging all threads", e)
        }
    }
    
    /**
     * Stops the ANR watchdog
     */
    fun stopWatching() {
        Log.d(TAG, "Stopping ANR Watchdog")
        shouldContinue = false
        interrupt()
    }
}
