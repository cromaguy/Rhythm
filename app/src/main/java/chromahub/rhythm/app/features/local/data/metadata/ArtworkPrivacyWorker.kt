// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import androidx.work.*

class ArtworkPrivacyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        PersonalArchive(applicationContext).sync()
        Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { Result.failure() }
    companion object {
        fun enqueue(context: Context, continuation: Boolean = false) {
            val prefs = context.getSharedPreferences("personal_archive", Context.MODE_PRIVATE)
            if (!continuation && prefs.getString("privacy_migration_target", null) == (prefs.getString("tree", null) ?: "default")) return
            WorkManager.getInstance(context).enqueueUniqueWork("private_artwork_migration_v1",
                if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ArtworkPrivacyWorker>().setConstraints(Constraints.Builder()
                    .setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build()).build())
        }
    }
}
