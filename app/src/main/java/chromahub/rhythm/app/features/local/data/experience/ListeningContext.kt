// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Bundle
import android.os.Looper
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlin.math.round

@Keep
data class ListeningSituation(val place: String? = null, val latitude: Double? = null, val longitude: Double? = null,
    val locationAccuracyM: Float? = null, val locationAgeMs: Long? = null, val precise: Boolean = false,
    val routeType: String = "unknown", val routeName: String? = null, val connectedOutputs: List<OutputSnapshot> = emptyList(),
    val routeEvidence: String = "system_route_inferred", val weekday: Int = 0, val hour: Int = 0,
    val altitudeM: Double? = null, val speedMps: Float? = null, val bearingDegrees: Float? = null,
    val locationProvider: String? = null, val systemVolume: Int = 0, val systemVolumeMax: Int = 0, val charging: Boolean = false, val batteryPercent: Int? = null,
    val weather: WeatherSnapshot? = null, val brightnessSetting: Int? = null, val automaticBrightness: Boolean? = null,
    val playerVolume: Float? = null, val ambientSound: AmbientSound? = null, val motion: MotionSnapshot? = null, val daylight: DaylightSnapshot? = null)
@Keep
data class OutputSnapshot(val type: Int, val name: String, val sampleRates: List<Int>, val channelCounts: List<Int>, val encodings: List<Int>)

/** No background location permission. Fresh fixes are requested only while the activity is visible. */
class ListeningContext private constructor(private val context: Context) : LocationListener {
    private val prefs = context.getSharedPreferences("listening_context", Context.MODE_PRIVATE)
    private val locations = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    @Volatile private var latest: Location? = null
    val enabled get() = prefs.getBoolean("location", false)
    val precise get() = prefs.getBoolean("precise", false)
    val place get() = prefs.getString("place", "").orEmpty()
    fun configure(enabled: Boolean = this.enabled, precise: Boolean = this.precise, place: String = this.place) {
        prefs.edit(commit = true) { putBoolean("location", enabled); putBoolean("precise", precise); putString("place", place.trim().take(80)) }
        if (!enabled) latest = null
        stop(); refresh()
        ListeningLedger.get(context).invalidate()
    }
    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun refresh() {
        MobilityContext.get(context).visible(true)
        if (!enabled || !permitted()) return
        try {
            val fine = precise && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val provider = if (fine && locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            latest = locations.getLastKnownLocation(provider)
            locations.requestLocationUpdates(provider, 60_000L, 100f, this, Looper.getMainLooper())
        } catch (_: SecurityException) { latest = null } catch (_: IllegalArgumentException) { latest = null }
    }
    fun stop() { MobilityContext.get(context).visible(false); runCatching { locations.removeUpdates(this) } }
    override fun onLocationChanged(location: Location) { latest = Location(location) }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) { latest = null }
    fun snapshot(now: Long = System.currentTimeMillis()): ListeningSituation {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }.map {
            OutputSnapshot(it.type, it.productName?.toString().orEmpty().take(100), it.sampleRates.toList(), it.channelCounts.toList(), it.encodings.toList())
        } }.getOrDefault(emptyList())
        val route = runCatching { (context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter).getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO) }.getOrNull()
        val routeType = when {
            route?.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH -> "bluetooth"
            route?.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_TV -> "cast"
            audio.isWiredHeadsetOn -> "wired"
            audio.isBluetoothA2dpOn || audio.isBluetoothScoOn -> "bluetooth"
            outputs.any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE } -> "usb"
            else -> "speaker"
        }
        val fine = precise && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fix = latest?.takeIf { enabled && permitted() && now - it.time in 0..15 * 60_000 && it.hasAccuracy() && it.accuracy <= 5000f }
        val date = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
        return ListeningSituation(place.takeIf { it.isNotBlank() }, fix?.latitude?.let { if (fine) it else round(it * 100) / 100 },
            fix?.longitude?.let { if (fine) it else round(it * 100) / 100 }, fix?.accuracy?.let { if (fine) it else maxOf(it, 1100f) },
            fix?.let { now - it.time }, fine && fix != null, routeType, route?.name?.toString()?.take(100), outputs,
            weekday = date.dayOfWeek.value, hour = date.hour,
            altitudeM = fix?.takeIf { fine && it.hasAltitude() }?.altitude,
            speedMps = fix?.takeIf { it.hasSpeed() }?.speed, bearingDegrees = fix?.takeIf { fine && it.hasBearing() }?.bearing,
            locationProvider = fix?.provider,
            systemVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC), systemVolumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            charging = (context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).isCharging,
            batteryPercent = (context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 },
            weather = ListeningWeather.get(context).snapshot(fix?.latitude, fix?.longitude, now),
            brightnessSetting = runCatching { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()?.coerceIn(0, 255),
            automaticBrightness = runCatching { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE) == android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC }.getOrNull(),
            playerVolume = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context).appVolume.value,
            ambientSound = ListeningSound.get(context).snapshot(now), motion = MobilityContext.get(context).snapshot(fix, now),
            daylight = ContextSignals.daylight(now, fix?.latitude, fix?.longitude, ListeningWeather.get(context).snapshot(fix?.latitude, fix?.longitude, now)?.isDay))
    }
    companion object {
        @Volatile private var instance: ListeningContext? = null
        fun get(context: Context): ListeningContext = instance ?: synchronized(this) { instance ?: ListeningContext(context.applicationContext).also { instance = it } }
        fun outputGroup(situation: ListeningSituation): String {
            val types = when (situation.routeType) { "bluetooth" -> setOf(7, 8, 26, 27); "wired" -> setOf(3, 4); "usb" -> setOf(11, 12, 22); else -> emptySet() }
            val names = situation.connectedOutputs.filter { it.type in types }.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.distinct().sorted()
            return situation.routeType + ":" + (names.takeIf { it.isNotEmpty() }?.joinToString("|") ?: situation.routeName.orEmpty().lowercase(java.util.Locale.ROOT))
        }
        fun region(situation: ListeningSituation?): String? = situation?.let { s ->
            if (s.latitude != null && s.longitude != null) "${round(s.latitude * 100) / 100}:${round(s.longitude * 100) / 100}" else s.place?.takeIf { it.isNotBlank() }
        }
    }
}
