// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.Keep
import com.google.gson.Gson

/** Disposable, bounded-memory projection. Only charging workers visit the raw listening history. */
internal class ContextPreferenceIndex(context: Context) : SQLiteOpenHelper(context,
    java.io.File(context.cacheDir, "context-preferences-v1.db").absolutePath, null, 2) {
    private val gson = Gson()
    @Keep data class Contribution(val dimension: String, val bucket: String, val key: String, val value: Double, val count: Double = 0.0)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY,recorded INTEGER NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE totals(dimension TEXT,bucket TEXT,item TEXT,value REAL NOT NULL,n REAL NOT NULL,PRIMARY KEY(dimension,bucket,item))")
        db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY,value INTEGER)")
        habitTables(db)
    }
    private fun habitTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS played_ids(id TEXT PRIMARY KEY,song_key TEXT NOT NULL,started INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS play_times ON played_ids(song_key,started)")
        db.execSQL("CREATE TABLE IF NOT EXISTS play_habits(song_key TEXT PRIMARY KEY,first_started INTEGER,last_started INTEGER,plays INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        habitTables(db)
        // Disposable summaries only; replay complete original records while charging.
        db.delete("entries", null, null); db.delete("totals", null, null); db.delete("state", null, null)
    }
    fun reset() {
        writableDatabase.beginTransaction()
        try { writableDatabase.delete("entries", null, null); writableDatabase.delete("totals", null, null); writableDatabase.delete("state", null, null); writableDatabase.delete("played_ids", null, null); writableDatabase.delete("play_habits", null, null); writableDatabase.setTransactionSuccessful() }
        finally { writableDatabase.endTransaction() }
    }
    fun rebuild(ledger: ListeningLedger, budget: AnalysisBudget) {
        val generation = ledger.state("history_generation").orEmpty()
        val indexedGeneration = readableDatabase.rawQuery("SELECT value FROM state WHERE id=2", null).use { if (it.moveToFirst()) it.getString(0) else null }
        if (indexedGeneration != generation) { reset(); writableDatabase.execSQL("INSERT OR REPLACE INTO state VALUES (2,?)", arrayOf(generation)) }
        var cursor = readableDatabase.rawQuery("SELECT value FROM state WHERE id=1", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
        while (true) {
            budget.blockingCheckpoint()
            val rows = ledger.trainingRows(cursor)
            if (rows.isEmpty()) break
            for ((row, record) in rows) {
                budget.blockingCheckpoint()
                // A clear can run while an analysis slice is queued. Do not reinsert deleted history.
                if (ledger.state("history_generation").orEmpty() != generation) throw AnalysisDeferred()
                val contributions = contributions(record, ledger)
                val db = writableDatabase
                db.beginTransaction()
                try {
                    if (ledger.state("history_generation").orEmpty() != generation) throw AnalysisDeferred()
                    val old = db.rawQuery("SELECT recorded,payload FROM entries WHERE id=?", arrayOf(record.id)).use {
                        if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null
                    }
                    if (old == null || old.first < record.recordedAt) {
                        fun apply(c: Contribution, sign: Double) {
                            db.execSQL("INSERT OR IGNORE INTO totals VALUES (?,?,?,0,0)", arrayOf(c.dimension, c.bucket, c.key))
                            db.execSQL("UPDATE totals SET value=value+?,n=n+? WHERE dimension=? AND bucket=? AND item=?",
                                arrayOf<Any>(c.value * sign, c.count * sign, c.dimension, c.bucket, c.key))
                        }
                        old?.let { gson.fromJson(it.second, Array<Contribution>::class.java).forEach { c -> apply(c, -1.0) } }
                        contributions.forEach { apply(it, 1.0) }
                        val previousStart = db.rawQuery("SELECT started FROM played_ids WHERE id=?", arrayOf(record.playId)).use { if (it.moveToFirst()) it.getLong(0) else null }
                        if (previousStart == null || record.startedAt < previousStart) {
                            db.execSQL("INSERT OR REPLACE INTO played_ids VALUES (?,?,?)", arrayOf<Any>(record.playId, record.songKey, record.startedAt))
                            db.execSQL("INSERT OR IGNORE INTO play_habits VALUES (?,0,0,0)", arrayOf(record.songKey))
                            db.execSQL("UPDATE play_habits SET first_started=(SELECT MIN(started) FROM played_ids WHERE song_key=?),last_started=(SELECT MAX(started) FROM played_ids WHERE song_key=?),plays=plays+? WHERE song_key=?",
                                arrayOf<Any>(record.songKey, record.songKey, if (previousStart == null) 1 else 0, record.songKey))
                        }
                        db.insertWithOnConflict("entries", null, ContentValues().apply {
                            put("id", record.id); put("recorded", record.recordedAt); put("payload", gson.toJson(contributions))
                        }, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    db.execSQL("INSERT OR REPLACE INTO state VALUES (1,?)", arrayOf(row))
                    db.setTransactionSuccessful(); cursor = row
                } finally { db.endTransaction() }
            }
        }
    }
    private fun contributions(r: ListeningRecord, ledger: ListeningLedger): List<Contribution> = buildList {
        if (r.kind == "play") {
            r.playCompletionRatio?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let { add(Contribution("completion", "all", r.songKey, it, 1.0)) }
            ledger.previousGenre(r)?.let { before ->
                val genre = AdaptiveRecommendation.normalizeGenre(r.genre)
                if (genre.isNotEmpty()) add(Contribution("transition", AdaptiveRecommendation.normalizeGenre(before), genre, 1.0))
            }
        }
        if (r.kind != "segment" || r.listenedMs <= 0) return@buildList
        val genre = AdaptiveRecommendation.normalizeGenre(r.genre)
        if (genre.isEmpty()) return@buildList
        val date = java.time.Instant.ofEpochMilli(r.startedAt).atZone(runCatching { java.time.ZoneId.of(r.timezone) }.getOrDefault(java.time.ZoneId.systemDefault()))
        val s = r.situation ?: ListeningSituation(hour = date.hour, weekday = date.dayOfWeek.value)
        add(Contribution("recent", date.toLocalDate().toEpochDay().toString().padStart(8, '0'), genre, r.listenedMs / 60_000.0))
        buckets(s).forEach { (dimension, bucket) -> add(Contribution(dimension, bucket, genre, r.listenedMs / 60_000.0)) }
    }
    private fun buckets(s: ListeningSituation): Map<String, String> = buildMap {
        put("hour", s.hour.toString()); put("weekday", s.weekday.toString())
        ListeningContext.region(s)?.let { put("place", it) }
        if (s.routeType != "unknown") put("output", ListeningContext.outputGroup(s))
        s.weather?.let { put("weather", it.group) }
        s.brightnessSetting?.let { put("brightness", (it / 64).toString()) }
        if (s.systemVolumeMax > 0) put("volume", ((s.systemVolume.toDouble() / s.systemVolumeMax * (s.playerVolume ?: 1f) * 4).toInt().coerceIn(0, 3)).toString())
        s.ambientSound?.let { put("ambient", (it.rmsDbfs / 10).toInt().toString()) }
        s.motion?.takeIf { it.confidence >= .4 && it.kind != "unknown" }?.let { put("motion", it.kind) }
        s.daylight?.let { put("daylight", it.isDay.toString()) }
    }
    fun preferences(situation: ListeningSituation, previousGenre: String?): RecommendationContext {
        val buckets = buckets(situation)
        fun values(dimension: String, bucket: String? = buckets[dimension], ratios: Boolean = false): Map<String, Double> {
            if (bucket == null) return emptyMap()
            val result = mutableMapOf<String, Double>()
            readableDatabase.rawQuery("SELECT item,value,n FROM totals WHERE dimension=? AND bucket=? AND (value>0 OR n>0) ORDER BY value DESC LIMIT 20000", arrayOf(dimension, bucket)).use { c ->
                while (c.moveToNext()) result[c.getString(0)] = if (ratios) ((c.getDouble(1) + 1) / (c.getDouble(2) + 2)).coerceIn(0.0, 1.0) else c.getDouble(1)
            }
            if (ratios) return result
            val max = (result.values.maxOrNull() ?: 0.0) + 3
            return result.mapValues { (it.value / max).coerceIn(0.0, 1.0) }
        }
        val completion = values("completion", "all", true)
        val today = java.time.LocalDate.now().toEpochDay()
        val recent = mutableMapOf<String, Double>()
        readableDatabase.rawQuery("SELECT bucket,item,value FROM totals WHERE dimension='recent' AND bucket>=? AND value>0 ORDER BY bucket DESC LIMIT 20000",
            arrayOf((today - 90).toString().padStart(8, '0'))).use { c ->
            while (c.moveToNext()) {
                val age = (today - c.getString(0).toLong()).coerceAtLeast(0)
                recent[c.getString(1)] = (recent[c.getString(1)] ?: 0.0) + c.getDouble(2) * kotlin.math.exp(-age / 30.0)
            }
        }
        val recentMax = (recent.values.maxOrNull() ?: 0.0) + 3
        val due = mutableMapOf<String, Double>(); val now = System.currentTimeMillis()
        readableDatabase.rawQuery("SELECT song_key,first_started,last_started,plays FROM play_habits WHERE plays>1 ORDER BY last_started DESC LIMIT 20000", null).use { c ->
            while (c.moveToNext()) {
                val gap = (c.getLong(2) - c.getLong(1)).toDouble() / (c.getLong(3) - 1)
                if (gap > 0) due[c.getString(0)] = ((now - c.getLong(2)).coerceAtLeast(0) / gap).coerceIn(0.0, 1.0)
            }
        }
        return RecommendationContext(hourlyGenres = values("hour"), weekdayGenres = values("weekday"),
            placeGenres = values("place"), outputGenres = values("output"), weatherGenres = values("weather"),
            brightnessGenres = values("brightness"), volumeGenres = values("volume"), ambientGenres = values("ambient"),
            motionGenres = values("motion"), daylightGenres = values("daylight"), completionRates = completion,
            skipRates = completion.mapValues { (1 - it.value).coerceAtLeast(0.0) * .5 },
            recentGenres = recent.mapValues { (it.value / recentMax).coerceIn(0.0, 1.0) }, replayDue = due,
            genreTransitions = values("transition", previousGenre?.let(AdaptiveRecommendation::normalizeGenre)))
    }
}
