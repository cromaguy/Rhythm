// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Optional data-only updates from this fork. No code, APK or personal history is transferred. */
class ExperienceRulesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!enabled(applicationContext)) return@withContext Result.success()
        try {
            val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url("https://raw.githubusercontent.com/tak2-08/T2Rhythm/main/experience/default.json").build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.retry()
                val source = response.body.source(); source.request(64_001)
                require(source.buffer.size <= 64_000)
                val json = source.readUtf8(); val profile = ExperienceProfile.decode(json)
                if (profile.version > ExperienceProfile.read(applicationContext).version) ExperienceProfile.apply(applicationContext, json)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enabled(context: Context) = context.getSharedPreferences("personal_experience", Context.MODE_PRIVATE).getBoolean("rulesUpdates", false)
        fun configure(context: Context, enabled: Boolean) {
            context.getSharedPreferences("personal_experience", Context.MODE_PRIVATE).edit().putBoolean("rulesUpdates", enabled).apply()
            val manager = WorkManager.getInstance(context)
            if (enabled) manager.enqueueUniquePeriodicWork("experience_rules_updates", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExperienceRulesWorker>(1, TimeUnit.DAYS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build())
            else manager.cancelUniqueWork("experience_rules_updates")
        }
    }
}
