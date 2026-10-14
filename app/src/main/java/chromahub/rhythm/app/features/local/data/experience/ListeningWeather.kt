// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import androidx.annotation.Keep
import androidx.core.content.edit
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import chromahub.rhythm.app.core.utils.NetworkUtils
import chromahub.rhythm.app.features.local.data.metadata.readBytesBounded
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.round

@Keep
data class WeatherSnapshot(val at: Long, val fetchedAt: Long, val region: String, val code: Int,
    val temperatureC: Double, val apparentTemperatureC: Double?, val humidityPercent: Double?,
    val precipitationMm: Double?, val cloudPercent: Double?, val windKmh: Double?,
    val pressureHpa: Double?, val isDay: Boolean, val provider: String = "Open-Meteo") {
    val group: String get() = when (code) { 0, 1 -> "clear"; 2, 3 -> "cloudy"; 45, 48 -> "fog";
        71, 73, 75, 77, 85, 86 -> "snow"; 95, 96, 99 -> "storm"; else -> "rain" }
}

/** Optional keyless weather lookup. No precise fix, song or listening history leaves the device. */
class ListeningWeather private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("listening_context", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "music_metadata/context/weather.json")
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cached = runCatching { gson.fromJson(file.readText(), WeatherSnapshot::class.java) }.getOrNull()
    @Volatile private var requesting = false
    val enabled get() = prefs.getBoolean("weather", false)
    fun configure(value: Boolean) { prefs.edit(commit = true) { putBoolean("weather", value) }; ListeningLedger.get(context).invalidate() }
    @Synchronized fun snapshot(latitude: Double?, longitude: Double?, now: Long): WeatherSnapshot? {
        if (!enabled || latitude == null || longitude == null) return null
        // A 0.02 degree grid gives local weather context without exposing the original precise fix.
        val lat = round(latitude * 50) / 50; val lon = round(longitude * 50) / 50
        val region = "$lat:$lon"
        val valid = cached?.takeIf { it.region == region && now - it.at in 0..90 * 60_000L }
        val lastAttempt = prefs.getLong("weather_attempt", 0)
        if (!requesting && now - lastAttempt >= 15 * 60_000L &&
            (valid == null || now - valid.fetchedAt >= 30 * 60_000L) && NetworkUtils.isNetworkAvailable(context)) {
            requesting = true; prefs.edit { putLong("weather_attempt", now) }
            scope.launch {
                try {
                    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=UTC&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,surface_pressure,is_day")
                    val connection = url.openConnection() as HttpURLConnection
                    val weather = try {
                        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                        connection.setRequestProperty("User-Agent", "T2Rhythm/5.5.498.1305 (on-device weather context)")
                        check(connection.responseCode == 200)
                        val bytes = connection.inputStream.use { it.readBytesBounded(64_000) }; check(bytes.size <= 64_000)
                        decode(bytes.toString(Charsets.UTF_8), region, System.currentTimeMillis())
                    } finally { connection.disconnect() }
                    if (!enabled || !ListeningContext.get(context).enabled || !ListeningContext.get(context).permitted()) return@launch
                    PersonalArchive.atomicWrite(file, gson.toJson(weather).toByteArray())
                    cached = weather; ListeningLedger.get(context).invalidate(); PersonalArchive.enqueue(context)
                } catch (_: Exception) { /* Missing weather remains unknown; retry is bounded even after restarts. */ }
                finally { requesting = false }
            }
        }
        return valid
    }
    companion object {
        @Volatile private var instance: ListeningWeather? = null
        fun get(context: Context): ListeningWeather = instance ?: synchronized(this) { instance ?: ListeningWeather(context.applicationContext).also { instance = it } }
        fun decode(json: String, region: String, now: Long): WeatherSnapshot {
            val current = JsonParser.parseString(json).asJsonObject.getAsJsonObject("current")
            fun number(key: String) = current.get(key)?.takeUnless { it.isJsonNull }?.asDouble?.takeIf { it.isFinite() }
            val at = LocalDateTime.parse(current.get("time").asString).toInstant(ZoneOffset.UTC).toEpochMilli()
            require(now - at in -15 * 60_000L..90 * 60_000L)
            val code = current.get("weather_code").asInt
            require(code in setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99))
            return WeatherSnapshot(at, now, region, code, requireNotNull(number("temperature_2m")), number("apparent_temperature"),
                number("relative_humidity_2m"), number("precipitation"), number("cloud_cover"), number("wind_speed_10m"), number("surface_pressure"), current.get("is_day").asInt == 1)
        }
    }
}
