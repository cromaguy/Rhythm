// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.pow

/** Disposable projection of immutable journals. Streaming offsets + SQLite IDs bound memory.
 * Ordinary new outcomes train only the tail. Reversible intent changes request a throttled replay. */
internal class RecommendationTrainingIndex(private val context: Context) {
    private val gson = Gson()
    fun train(root: File, budget: AnalysisBudget): RecommendationModel {
        if (!budget.allowed()) throw AnalysisDeferred()
        val path = File(context.cacheDir, "recommendation-index-${context.filesDir.path.hashCode()}.db")
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS events (id TEXT PRIMARY KEY, kind TEXT NOT NULL, item_key TEXT NOT NULL, at INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS latest (kind TEXT NOT NULL, item_key TEXT NOT NULL, event_id TEXT NOT NULL, PRIMARY KEY(kind,item_key))")
            db.execSQL("CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            fun state(key: String) = db.rawQuery("SELECT value FROM state WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
            fun putState(key: String, value: String) { db.insertWithOnConflict("state", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
            for (file in root.listFiles { f -> f.extension == "jsonl" }.orEmpty().sortedBy { it.name }) {
                budget.blockingCheckpoint()
                val offset = state("offset:${file.name}")?.toLongOrNull()?.takeIf { it in 0..file.length() } ?: 0L
                RandomAccessFile(file, "r").use { input ->
                    input.seek(offset); var rows = 0
                    db.beginTransaction()
                    try {
                        while (true) {
                            budget.blockingCheckpoint()
                            val before = input.filePointer
                            val raw = input.readLine() ?: break
                            val after = input.filePointer
                            input.seek(after - 1); val complete = input.read() == 10; input.seek(after)
                            if (!complete) { input.seek(before); break }
                            val line = raw.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
                            val e = if (line.length <= 64_000) runCatching { gson.fromJson(line, RecommendationEvent::class.java) }.getOrNull() else null
                            if (e != null && !e.id.isNullOrBlank() && !e.key.isNullOrBlank() && e.schema in 1..AdaptiveRecommendation.SCHEMA && e.type in setOf("explicit", "collection_explicit", "outcome")) {
                                val inserted = db.insertWithOnConflict("events", null, ContentValues().apply {
                                    put("id", e.id); put("kind", e.type); put("item_key", e.key); put("at", e.at); put("payload", line)
                                }, SQLiteDatabase.CONFLICT_IGNORE)
                                if (inserted != -1L && e.type != "outcome") {
                                    val old = db.rawQuery("SELECT e.at FROM latest l JOIN events e ON l.event_id=e.id WHERE l.kind=? AND l.item_key=?", arrayOf(e.type, e.key)).use { if (it.moveToFirst()) it.getLong(0) else null }
                                    if (old == null || e.at >= old) {
                                        if (old != null) putState("reset", "1")
                                        db.insertWithOnConflict("latest", null, ContentValues().apply { put("kind", e.type); put("item_key", e.key); put("event_id", e.id) }, SQLiteDatabase.CONFLICT_REPLACE)
                                    }
                                }
                            }
                            if (++rows % 128 == 0) {
                                putState("offset:${file.name}", input.filePointer.toString()); db.setTransactionSuccessful(); db.endTransaction(); db.beginTransaction()
                            }
                        }
                        putState("offset:${file.name}", input.filePointer.toString()); db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
            }
            val reset = state("reset") == "1" || state("schema") != AdaptiveRecommendation.SCHEMA.toString()
            var result = if (reset) RecommendationModel() else runCatching { gson.fromJson(state("model"), RecommendationModel::class.java) }.getOrNull() ?: RecommendationModel()
            var cursor = if (reset) 0L else state("cursor")?.toLongOrNull() ?: 0L
            if (reset) {
                db.beginTransaction()
                try { putState("model", gson.toJson(result)); putState("cursor", "0"); putState("reset", "0"); putState("schema", AdaptiveRecommendation.SCHEMA.toString()); db.setTransactionSuccessful() }
                finally { db.endTransaction() }
            }
            db.rawQuery("SELECT rowid,payload FROM events WHERE rowid>? AND (kind='outcome' OR id IN (SELECT event_id FROM latest)) ORDER BY rowid", arrayOf(cursor.toString())).use { rows ->
                var batch = 0
                while (rows.moveToNext()) {
                    budget.blockingCheckpoint()
                    val e = gson.fromJson(rows.getString(1), RecommendationEvent::class.java)
                    val raw = e.features.orEmpty()
                    val features = if (raw.size in 13..AdaptiveRecommendation.featureNames.size) raw + List(AdaptiveRecommendation.featureNames.size - raw.size) { 0.0 } else emptyList()
                    val reward = e.reward
                    if (features.size == AdaptiveRecommendation.featureNames.size && features.all { it.isFinite() && it in 0.0..1.0 } && reward != null && reward.isFinite() && reward in 0.0..1.0) {
                        val prediction = e.prediction?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: AdaptiveRecommendation.probability(result.weights, features)
                        result = result.copy(weights = AdaptiveRecommendation.learn(result.weights, features, reward, result.samples), samples = result.samples + 1,
                            squaredError = result.squaredError + (prediction - reward).pow(2), baselineError = result.baselineError + (.5 - reward).pow(2),
                            positive = result.positive + if (reward >= .5) 1 else 0, negative = result.negative + if (reward < .5) 1 else 0)
                    }
                    cursor = rows.getLong(0)
                    if (++batch % 128 == 0) {
                        db.beginTransaction()
                        try { putState("model", gson.toJson(result)); putState("cursor", cursor.toString()); db.setTransactionSuccessful() } finally { db.endTransaction() }
                    }
                }
            }
            val explicit = mutableMapOf<String, Double>(); val collections = mutableMapOf<String, Double>()
            db.rawQuery("SELECT e.payload FROM latest l JOIN events e ON e.id=l.event_id", null).use { rows ->
                while (rows.moveToNext()) {
                    budget.blockingCheckpoint()
                    val e = gson.fromJson(rows.getString(0), RecommendationEvent::class.java)
                    e.reward?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { (if (e.type == "explicit") explicit else collections)[e.key] = it }
                }
            }
            db.beginTransaction()
            try { putState("model", gson.toJson(result)); putState("cursor", cursor.toString()); db.setTransactionSuccessful() } finally { db.endTransaction() }
            return result.copy(explicit = explicit, collections = collections)
        } finally { db.close() }
    }
}
