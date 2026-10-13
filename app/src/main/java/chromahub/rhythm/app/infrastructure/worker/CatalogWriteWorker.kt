// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.infrastructure.worker

import android.content.Context
import androidx.work.*
import chromahub.rhythm.app.features.local.data.metadata.*
import java.util.concurrent.TimeUnit

/** Pending file writes resume after MediaStore consent; active audio files are left alone. */
class CatalogWriteWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val catalog = CatalogEnrichmentService.get(applicationContext)
        val lyrics = AutomaticLyricsStore.get(applicationContext)
        val tags = CatalogTagWrites(applicationContext)
        lyrics.pending().forEach { lyrics.attemptEmbedding(it) }
        tags.pending().forEach { tags.attempt(it) }
        catalog.notifyChanged()
        return if ((lyrics.pending() + tags.pending()).any(catalog::audioBusy)) Result.retry() else Result.success()
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("catalog_file_writes", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CatalogWriteWorker>().setInitialDelay(15, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build()).build())
        }
    }
}
