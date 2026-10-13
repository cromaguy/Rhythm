// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.Keep
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant
import java.time.ZoneId

@Keep
data class ListeningRecord(
    val id: String,
    val playId: String,
    val kind: String,
    val songId: String,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val startedAt: Long,
    val endedAt: Long,
    val listenedMs: Long,
    val audioDurationMs: Long,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val speed: Float,
    val reason: String,
    val completed: Boolean = false,
    val libraryAddedAt: Long = 0,
    val timezone: String = ZoneId.systemDefault().id,
    val outputTypes: List<Int> = emptyList(),
    val repeatMode: Int = 0,
    val shuffled: Boolean = false,
    val volume: Float = 1f,
    val favorite: Boolean = false,
    val recordedAt: Long = System.currentTimeMillis(),
    val albumArtist: String? = null,
    val libraryModifiedAt: Long = 0,
    val codec: String? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val bitrate: Int? = null,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
)

/** Indexed on-device source of truth. Raw journals are retained forever and can rebuild the DB. */
class ListeningLedger private constructor(private val context: Context) : SQLiteOpenHelper(context, "listening_ledger.db", null, 1) {
    private val gson = Gson()
    private val lock = Any()
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    private val issue = MutableStateFlow<String?>(null)
    val storageIssue = issue.asStateFlow()
    fun storageIssue(value: String?) { issue.value = value }
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, play_id TEXT NOT NULL, kind TEXT NOT NULL, song_key TEXT NOT NULL, song_id TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER NOT NULL, listened INTEGER NOT NULL, artist TEXT NOT NULL, genre TEXT, completed INTEGER NOT NULL, recorded INTEGER NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX records_time ON records(kind, started, ended)")
        db.execSQL("CREATE INDEX records_song ON records(song_key, kind, started)")
        db.execSQL("CREATE INDEX records_play ON records(play_id)")
        db.execSQL("CREATE TABLE features (song_key TEXT PRIMARY KEY, signature TEXT NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun record(record: ListeningRecord) = synchronized(lock) {
        require(record.startedAt >= 0 && record.endedAt >= record.startedAt && record.listenedMs >= 0)
        val json = gson.toJson(record)
        // Journal first: a failed DB update remains recoverable from the durable raw record.
        val root = File(context.filesDir, "listening_history").apply { mkdirs() }
        val month = Instant.ofEpochMilli(record.startedAt).atZone(ZoneId.of(record.timezone)).toLocalDate().toString().take(7)
        val journal = File(root, "$month.jsonl")
        // Separate an interrupted last line before appending the next valid record.
        if (journal.length() > 0) java.io.RandomAccessFile(journal, "r").use { input ->
            input.seek(journal.length() - 1)
            if (input.read() != 10) java.io.FileOutputStream(journal, true).use { it.write(10); it.fd.sync() }
        }
        java.io.FileOutputStream(journal, true).use { out ->
            out.write((json + "\n").toByteArray(Charsets.UTF_8)); out.fd.sync()
        }
        writableDatabase.beginTransaction()
        try { upsert(record, json); state("journal_offset:${journal.name}", journal.length().toString()); writableDatabase.setTransactionSuccessful() }
        finally { writableDatabase.endTransaction() }
        revision.update { it + 1 }
        PersonalArchive.enqueue(context)
    }
    private fun upsert(record: ListeningRecord, json: String) {
        val values = ContentValues().apply {
            put("id", record.id); put("play_id", record.playId); put("kind", record.kind)
            put("song_key", record.songKey); put("song_id", record.songId); put("started", record.startedAt)
            put("ended", record.endedAt); put("listened", record.listenedMs); put("artist", record.artist)
            put("genre", record.genre); put("completed", if (record.completed) 1 else 0)
            put("recorded", record.recordedAt); put("payload", json)
        }
        check(writableDatabase.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    fun records(start: Long? = null, end: Long = Long.MAX_VALUE, kind: String = "segment"): List<ListeningRecord> {
        val result = mutableListOf<ListeningRecord>()
        readableDatabase.rawQuery("SELECT payload FROM records WHERE kind = ? AND ended >= ? AND started <= ? ORDER BY started", arrayOf(kind, (start ?: 0).toString(), end.toString())).use { cursor ->
            while (cursor.moveToNext()) result += gson.fromJson(cursor.getString(0), ListeningRecord::class.java)
        }
        return result
    }
    data class Affinity(val songKey: String, val artist: String, val genre: String?, val listenedMs: Long, val plays: Int, val lastAt: Long)
    fun affinities(): List<Affinity> {
        val result = mutableListOf<Affinity>()
        readableDatabase.rawQuery("SELECT song_key, artist, genre, SUM(listened), COUNT(DISTINCT play_id), MAX(ended) FROM records WHERE kind = 'segment' GROUP BY song_key ORDER BY SUM(listened) DESC", null).use { c ->
            while (c.moveToNext()) result += Affinity(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4), c.getLong(5))
        }
        return result
    }
    data class Monthly(val month: String, val listenedMs: Long, val plays: Int, val songs: Int)
    fun months(): List<Monthly> {
        val result = mutableListOf<Monthly>()
        readableDatabase.rawQuery("SELECT strftime('%Y-%m', started / 1000, 'unixepoch', 'localtime'), SUM(listened), COUNT(DISTINCT play_id), COUNT(DISTINCT song_key) FROM records WHERE kind = 'segment' GROUP BY 1 ORDER BY 1 DESC", null).use { c ->
            while (c.moveToNext()) result += Monthly(c.getString(0), c.getLong(1), c.getInt(2), c.getInt(3))
        }
        return result
    }
    fun feature(key: String, signature: String? = null): AudioFeatures? = readableDatabase.rawQuery(
        "SELECT signature, payload FROM features WHERE song_key = ?", arrayOf(key)).use { c ->
        if (!c.moveToFirst() || signature != null && c.getString(0) != signature) null else gson.fromJson(c.getString(1), AudioFeatures::class.java)
    }
    fun saveFeature(key: String, signature: String, feature: AudioFeatures) = synchronized(lock) {
        val payload = gson.toJson(feature.copy(sourceSignature = signature))
        writableDatabase.insertWithOnConflict("features", null, ContentValues().apply { put("song_key", key); put("signature", signature); put("payload", payload) }, SQLiteDatabase.CONFLICT_REPLACE)
        val directory = File(context.filesDir, "music_metadata/audio_features").apply { mkdirs() }
        PersonalArchive.atomicWrite(File(directory, "${kr.co.metadata.crawler.CatalogFileStore.hash(key)}.json"), payload.toByteArray())
        revision.update { it + 1 }; PersonalArchive.enqueue(context)
    }
    /** Restore is idempotent; older checkpoints cannot replace a newer complete record. */
    fun importJournal(file: File): Int = synchronized(lock) {
        var count = 0
        writableDatabase.beginTransaction()
        try {
            file.useLines { lines -> lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val r = runCatching { gson.fromJson(line, ListeningRecord::class.java) }.getOrNull() ?: return@forEach
                if (r.id.isBlank() || r.songKey.isBlank() || r.endedAt < r.startedAt || r.listenedMs < 0) return@forEach
                val current = readableDatabase.rawQuery("SELECT recorded FROM records WHERE id = ?", arrayOf(r.id)).use { if (it.moveToFirst()) it.getLong(0) else -1 }
                if (r.recordedAt >= current) { upsert(r, line); count++ }
            } }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        revision.update { it + 1 }; count
    }
    fun recover() = synchronized(lock) {
        val root = File(context.filesDir, "listening_history")
        root.listFiles { file -> file.extension == "jsonl" }?.forEach { file ->
            val offset = state("journal_offset:${file.name}")?.toLongOrNull()?.coerceAtMost(file.length()) ?: 0L
            if (offset == file.length()) return@forEach
            val tail = File.createTempFile("journal-tail-", ".jsonl", context.cacheDir)
            try {
                file.inputStream().use { input ->
                    var remaining = offset
                    while (remaining > 0) { val skipped = input.skip(remaining); if (skipped <= 0) break; remaining -= skipped }
                    tail.outputStream().use { input.copyTo(it) }
                }
                importJournal(tail); state("journal_offset:${file.name}", file.length().toString())
            } finally { tail.delete() }
        }
    }
    fun clearRecords() = synchronized(lock) {
        writableDatabase.delete("records", null, null)
        File(context.filesDir, "listening_history").listFiles()?.forEach { it.delete() }
        revision.update { it + 1 }
    }
    fun copyJournal(source: File, destination: File) = synchronized(lock) { source.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } } }
    fun invalidate() { revision.update { it + 1 } }
    fun state(key: String): String? = readableDatabase.rawQuery("SELECT value FROM state WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun state(key: String, value: String) { writableDatabase.insertWithOnConflict("state", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
    companion object {
        @Volatile private var instance: ListeningLedger? = null
        fun get(context: Context): ListeningLedger = instance ?: synchronized(this) { instance ?: ListeningLedger(context.applicationContext).also { instance = it } }
    }
}
