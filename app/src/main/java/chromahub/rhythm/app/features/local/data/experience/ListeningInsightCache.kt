// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import com.google.gson.GsonBuilder
import java.io.File
import java.time.YearMonth
import java.time.ZoneId

/** Expensive detail statistics are materialized only on the charging analysis thread. */
internal object ListeningInsightCache {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().create()
    private fun file(context: Context, month: String) = File(context.cacheDir, "listening-insights/$month.json")
    fun request(context: Context, month: String) {
        YearMonth.parse(month)
        context.getSharedPreferences("insight_cache", Context.MODE_PRIVATE).edit().putString("requested", month).apply()
        RecommendationTrainingWorker.enqueue(context)
    }
    fun read(context: Context, month: String): ListeningInsights? = runCatching {
        val file = file(context, month); require(file.length() in 1..2_000_000)
        gson.fromJson(file.readText(), ListeningInsights::class.java)
    }.getOrNull()
    fun refresh(context: Context, budget: AnalysisBudget) {
        val prefs = context.getSharedPreferences("insight_cache", Context.MODE_PRIVATE)
        val requested = prefs.getString("requested", YearMonth.now().toString())!!
        val month = YearMonth.parse(requested); val zone = ZoneId.systemDefault()
        val ledger = ListeningLedger.get(context)
        budget.blockingCheckpoint()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        // Raw history and lifetime indexes are complete. Detailed examples use a bounded recent window.
        val records = ledger.records(start, end, limit = 5000) + ledger.records(start, end, "action", 5000)
        val result = ListeningInsightAnalysis.analyze(records)
        budget.blockingCheckpoint()
        PersonalArchive.atomicWrite(file(context, requested), gson.toJson(result).toByteArray())
        ledger.invalidate()
    }
}
