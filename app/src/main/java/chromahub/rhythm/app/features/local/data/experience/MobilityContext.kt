// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.annotation.Keep
import kotlin.math.*

@Keep data class MotionSnapshot(val kind: String, val confidence: Double, val evidence: String,
    val at: Long, val accelerationRms: Double? = null, val speedMps: Float? = null)
@Keep data class DaylightSnapshot(val isDay: Boolean, val evidence: String)

object ContextSignals {
    fun motion(speed: Float?, altitude: Double?, gait: Double, sustainedSeconds: Long, now: Long, rms: Double? = null): MotionSnapshot {
        if (speed != null && (!speed.isFinite() || speed < 0 || speed > 400)) return MotionSnapshot("unknown", 0.0, "invalid_speed", now)
        val result = when {
            speed != null && speed >= 90 && altitude != null && altitude >= 1800 && sustainedSeconds >= 60 -> Triple("airborne", .85, "sustained_speed_and_altitude")
            speed != null && speed >= 7.5 && sustainedSeconds >= 20 -> Triple("vehicle", .8, "sustained_ground_speed")
            speed != null && speed in 2.4f..7.5f && gait < .3 && sustainedSeconds >= 30 -> Triple("cycling", .45, "cycling_or_slow_vehicle_inferred")
            gait >= .5 && (speed == null || speed <= 3f) -> Triple("walking", minOf(.85, .5 + gait * .3), "periodic_acceleration_inferred")
            speed != null && speed < .5 && (rms ?: 0.0) < .3 -> Triple("still", .75, "low_speed_and_motion")
            else -> Triple("unknown", 0.0, "insufficient_evidence")
        }
        return MotionSnapshot(result.first, result.second, result.third, now, rms, speed)
    }
    /** Approximate solar elevation; works offline, including polar day/night. */
    fun daylight(now: Long, latitude: Double?, longitude: Double?, weatherDay: Boolean? = null): DaylightSnapshot {
        if (weatherDay != null) return DaylightSnapshot(weatherDay, "weather_model")
        val utc = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC)
        if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            val n = utc.dayOfYear.toDouble()
            val b = 2 * PI * (n - 81) / 364
            val equation = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
            val hours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0 + longitude / 15 + equation / 60
            val declination = Math.toRadians(23.44) * sin(2 * PI * (n - 81) / 365.2422)
            val lat = Math.toRadians(latitude); val angle = PI * (hours - 12) / 12
            val elevation = asin((sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(angle)).coerceIn(-1.0, 1.0))
            return DaylightSnapshot(elevation >= Math.toRadians(-.833), "solar_estimate")
        }
        val hour = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
        return DaylightSnapshot(hour in 6..17, "local_clock_estimate")
    }
}

/** Five-Hz, batched accelerometer summaries; no raw sensor traces are retained. */
class MobilityContext private constructor(private val context: Context) : SensorEventListener {
    private val prefs = context.getSharedPreferences("listening_context", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var visible = false; private var playback = false; private var registered = false
    private val gravity = DoubleArray(3)
    private var sampleAt = 0L; private var peakAt = 0L; private var lastPeak = 0.0
    private var gait = 0.0; private var rms = 0.0
    private var speedClass = ""; private var speedSince = 0L
    val enabled get() = prefs.getBoolean("motion", true)
    val manual get() = prefs.getString("motion_override", "auto").orEmpty()
    fun configure(enabled: Boolean = this.enabled, manual: String = this.manual) {
        require(manual in setOf("auto", "still", "walking", "cycling", "vehicle", "airborne"))
        prefs.edit().putBoolean("motion", enabled).putString("motion_override", manual).apply(); update()
    }
    fun visible(value: Boolean) { visible = value; update() }
    fun playback(value: Boolean) { playback = value; update() }
    private fun update() {
        val wanted = enabled && (visible || playback)
        if (wanted == registered) return
        if (wanted) registered = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(this, it, 200_000, 5_000_000)
        } == true else { manager.unregisterListener(this); registered = false }
    }
    override fun onSensorChanged(event: SensorEvent) {
        val at = event.timestamp / 1_000_000
        if (at - sampleAt < 180) return
        sampleAt = at
        var squared = 0.0
        repeat(3) { i -> gravity[i] = gravity[i] * .8 + event.values[i] * .2; squared += (event.values[i] - gravity[i]).pow(2) }
        val value = sqrt(squared); rms = rms * .9 + value * .1
        if (value > 1.2 && value > lastPeak && at - peakAt in 350..1200) { gait = (gait + .12).coerceAtMost(1.0); peakAt = at }
        else if (value > 1.2 && at - peakAt > 1200) peakAt = at
        if (at - peakAt > 3000) gait *= .9
        lastPeak = value
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    @Synchronized fun snapshot(fix: Location?, now: Long): MotionSnapshot? {
        if (!enabled) return null
        if (manual != "auto") return MotionSnapshot(manual, 1.0, "user_selected", now)
        val location = fix?.takeIf { now - it.time in 0..90_000 && it.hasAccuracy() && it.accuracy <= 80 && it.hasSpeed() && (!it.hasSpeedAccuracy() || it.speedAccuracyMetersPerSecond <= 3f) }
        val speed = location?.speed
        val category = when { speed == null -> "unknown"; speed >= 90 -> "air"; speed >= 7.5 -> "vehicle"; speed >= 2.4 -> "cycle"; else -> "slow" }
        if (category != speedClass) { speedClass = category; speedSince = now }
        val recent = android.os.SystemClock.elapsedRealtime() - sampleAt in 0..30_000
        return ContextSignals.motion(speed, location?.takeIf { it.hasAltitude() }?.altitude,
            if (recent) gait else 0.0, (now - speedSince) / 1000, now, if (recent) rms else null)
    }
    companion object {
        @Volatile private var instance: MobilityContext? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: MobilityContext(context.applicationContext).also { instance = it } }
    }
}
