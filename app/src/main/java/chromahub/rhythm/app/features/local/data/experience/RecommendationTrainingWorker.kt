// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RecommendationTrainingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(AnalysisBudget.dispatcher) {
        try {
            if (!AnalysisBudget(applicationContext).allowed()) return@withContext Result.retry()
            RecommendationLearning.get(applicationContext).rebuild()
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: AnalysisSliceEnded) { enqueue(applicationContext, true); Result.success() }
        catch (_: AnalysisDeferred) { Result.retry() }
        catch (_: Exception) { Result.failure() }
    }
    companion object {
        fun enqueue(context: Context, continuation: Boolean = false) = WorkManager.getInstance(context).enqueueUniqueWork("recommendation_training_v4",
            if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RecommendationTrainingWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true)
                    .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build())
    }
}
