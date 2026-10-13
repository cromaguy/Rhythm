// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import chromahub.rhythm.app.shared.data.model.Song
import java.io.File

/** Store checkpoint only after each song's permanent records and pending writes have committed. */
data class CatalogBatchPlan(val keys: List<String>, val next: Int = 0, val changed: Int = 0,
                            val skipped: Int = 0, val failed: Int = 0, val runId: String = java.util.UUID.randomUUID().toString()) {
    companion object {
        private fun file(context: Context) = File(CatalogEnrichmentService.get(context).root, "batch.json")
        fun read(context: Context): CatalogBatchPlan? = runCatching {
            val file = file(context)
            if (!file.isFile) return@runCatching null
            val manifest = JSONObject(file.readText())
            val array = manifest.getJSONArray("keys")
            val progressFile = File(file.parentFile, "batch-progress.json")
            val progress = if (progressFile.isFile) JSONObject(progressFile.readText()) else JSONObject()
            val data = if (progress.optString("runId") == manifest.getString("runId")) progress else JSONObject()
            CatalogBatchPlan(List(array.length()) { array.getString(it) }, data.optInt("next"), data.optInt("changed"),
                data.optInt("skipped"), data.optInt("failed"), manifest.getString("runId"))
        }.getOrNull()
        fun create(context: Context, songs: List<Song>): CatalogBatchPlan = CatalogBatchPlan(
            songs.map(CatalogEnrichmentService.get(context)::songKey).distinct()).also {
                val file = file(context); file.parentFile?.mkdirs()
                val manifest = JSONObject().apply { put("runId", it.runId); put("keys", JSONArray(it.keys)) }.toString()
                val atomic = AtomicFile(file); val stream = atomic.startWrite()
                try { stream.write(manifest.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
                catch (error: Exception) { atomic.failWrite(stream); throw error }
                it.save(context)
            }
    }
    fun save(context: Context) {
        val destination = File(file(context).parentFile, "batch-progress.json")
        destination.parentFile?.mkdirs()
        val data = JSONObject().apply { put("runId", runId); put("next", next); put("changed", changed)
            put("skipped", skipped); put("failed", failed) }.toString()
        val atomic = AtomicFile(destination)
        val stream = atomic.startWrite()
        try { stream.write(data.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}
