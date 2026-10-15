// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

internal class AnalysisSliceEnded : Exception("Analysis slice completed")

internal class AnalysisDeferred : Exception("Analysis waits for charging and a cool device")

/** One low-priority analysis thread app-wide, with short CPU bursts and cooperative cooling. */
internal class AnalysisBudget(private val context: Context, private val active: () -> Boolean = { true }, private val maxMillis: Long = Long.MAX_VALUE) {
    private val startedAt = SystemClock.elapsedRealtime()
    private var sliceStarted = SystemClock.elapsedRealtime()
    private var checkedAt = -1000L
    fun allowed(): Boolean {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val status = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val powered = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } ?: battery.isCharging
        if (!powered) return false
        return Build.VERSION.SDK_INT < 29 || (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .currentThermalStatus < PowerManager.THERMAL_STATUS_MODERATE
    }
    suspend fun checkpoint() {
        if (!active()) throw kotlinx.coroutines.CancellationException("Analysis stopped")
        val now = SystemClock.elapsedRealtime()
        if (now - startedAt >= maxMillis) throw AnalysisSliceEnded()
        if (now - checkedAt >= 1000) { checkedAt = now; if (!allowed()) throw AnalysisDeferred() }
        if (now - sliceStarted >= 16) { delay(144); sliceStarted = SystemClock.elapsedRealtime() }
    }
    fun blockingCheckpoint() {
        if (!active()) throw kotlinx.coroutines.CancellationException("Analysis stopped")
        val now = SystemClock.elapsedRealtime()
        if (now - startedAt >= maxMillis) throw AnalysisSliceEnded()
        if (now - checkedAt >= 1000) { checkedAt = now; if (!allowed()) throw AnalysisDeferred() }
        if (now - sliceStarted >= 16) { Thread.sleep(144); sliceStarted = SystemClock.elapsedRealtime() }
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
    }
    companion object {
        val dispatcher = Executors.newSingleThreadExecutor { work -> Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
            work.run()
        }, "T2Rhythm-analysis").apply { isDaemon = true } }.asCoroutineDispatcher()
    }
}
