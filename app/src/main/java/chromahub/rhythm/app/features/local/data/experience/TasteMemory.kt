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
import java.io.File
import kotlin.math.floor

@Keep enum class TasteKind { SONG, ALBUM, ARTIST }
@Keep data class TasteState(val kind: TasteKind, val key: String, val label: String, val liked: Boolean,
    val firstLikedAt: Long? = null, val changedAt: Long, val nextReviewAt: Long, val cycle: Int = 0,
    val pending: Boolean = false, val exposedAt: Long? = null)
@Keep data class TasteEvent(val id: String, val at: Long, val action: String, val state: TasteState)

/** Permanent intentions and explicit exposures. A review never removes the underlying like. */
class TasteMemory private constructor(private val context: Context) : SQLiteOpenHelper(context, "taste-memory.db", null, 1) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("taste_memory", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    val reviewsEnabled get() = prefs.getBoolean("reviews_enabled", true)
    fun reviewsEnabled(value: Boolean) { check(prefs.edit().putBoolean("reviews_enabled", value).commit()); revision.value++ }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entities(kind TEXT,item TEXT,payload TEXT NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(kind,item))")
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE offsets(name TEXT PRIMARY KEY,value INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun state(kind: TasteKind, key: String): TasteState? = readableDatabase.rawQuery(
        "SELECT payload FROM entities WHERE kind=? AND item=?", arrayOf(kind.name, key)).use { if (it.moveToFirst()) gson.fromJson(it.getString(0), TasteState::class.java) else null }
    /** Called only after explicit user navigation/tap, never from autoplay or ordinary recomposition. */
    @Synchronized fun inspect(kind: TasteKind, key: String, label: String, existingSongLike: Boolean = false, now: Long = System.currentTimeMillis()): TasteState? {
        var old = state(kind, key)
        if (old == null && existingSongLike) {
            old = TasteState(kind, key, label, true, changedAt = now, nextReviewAt = now + interval(key, 0))
            append("import_like_time_unknown", old, now)
        }
        if (old == null || !reviewsEnabled || !old.liked || old.nextReviewAt > now) return old
        if (!old.pending || old.exposedAt == null) {
            old = old.copy(pending = true, exposedAt = now)
            append("review_exposed", old, now)
        }
        return old
    }
    @Synchronized fun set(kind: TasteKind, key: String, label: String, liked: Boolean, now: Long = System.currentTimeMillis()): TasteState {
        val old = state(kind, key)
        val reaffirm = liked && old?.liked == true && old.pending && reviewsEnabled
        if (old != null && old.liked == liked && !reaffirm) return old
        val cycle = (old?.cycle ?: -1) + 1
        val next = TasteState(kind, key, label, liked,
            old?.firstLikedAt ?: if (liked && old == null) now else null,
            now, now + interval(key, cycle), cycle, false, null)
        append(if (reaffirm) "reconfirmed" else if (liked) "liked" else "unliked", next, now)
        return next
    }
    fun visibleLiked(state: TasteState?): Boolean = state?.liked == true && !(reviewsEnabled && state.pending)
    @Synchronized fun likedKeys(kind: TasteKind): Set<String> = buildSet {
        readableDatabase.rawQuery("SELECT payload FROM entities WHERE kind=?", arrayOf(kind.name)).use { c ->
            while (c.moveToNext()) runCatching { gson.fromJson(c.getString(0), TasteState::class.java) }.getOrNull()?.takeIf { it.liked }?.let { add(it.key) }
        }
    }
    @Synchronized fun weights(kind: TasteKind, now: Long = System.currentTimeMillis()): Map<String, Double> = buildMap {
        readableDatabase.rawQuery("SELECT payload FROM entities WHERE kind=?", arrayOf(kind.name)).use { c ->
            while (c.moveToNext()) runCatching { gson.fromJson(c.getString(0), TasteState::class.java) }.getOrNull()?.takeIf { it.liked }?.let {
                // Positive evidence ages naturally. A pending/unanswered review has no negative weight.
                put(it.key, .3 + .7 * kotlin.math.exp(-(now - it.changedAt).coerceAtLeast(0).toDouble() / (180 * 86_400_000L)))
            }
        }
    }
    internal fun recover(budget: AnalysisBudget) {
        val files = File(context.filesDir, "music_metadata/preferences").listFiles { f -> f.extension == "jsonl" }.orEmpty().sortedBy { it.name }
        for (file in files) {
            var offset = readableDatabase.rawQuery("SELECT value FROM offsets WHERE name=?", arrayOf(file.name)).use { if (it.moveToFirst()) it.getLong(0).coerceAtMost(file.length()) else 0L }
            java.io.RandomAccessFile(file, "r").use { input ->
                input.seek(offset)
                while (true) {
                    budget.blockingCheckpoint()
                    val line = input.readLine() ?: break
                    val end = input.filePointer
                    input.seek(end - 1); val complete = input.read() == 10; input.seek(end)
                    if (!complete) break
                    synchronized(this) {
                        if (line.length <= 64_000) runCatching { gson.fromJson(String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8), TasteEvent::class.java) }.getOrNull()?.let(::apply)
                        writableDatabase.execSQL("INSERT OR REPLACE INTO offsets VALUES (?,?)", arrayOf<Any>(file.name, end))
                    }
                    offset = end
                }
            }
        }
    }
    private fun append(action: String, state: TasteState, now: Long) {
        val event = TasteEvent(java.util.UUID.randomUUID().toString(), now, action, state)
        val root = File(context.filesDir, "music_metadata/preferences").apply { mkdirs() }
        val file = File(root, "${java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().take(7)}.jsonl")
        if (file.length() > 0) java.io.RandomAccessFile(file, "r").use { input ->
            input.seek(file.length() - 1)
            if (input.read() != 10) java.io.FileOutputStream(file, true).use { it.write(10); it.fd.sync() }
        }
        java.io.FileOutputStream(file, true).use { it.write((gson.toJson(event) + "\n").toByteArray()); it.fd.sync() }
        apply(event)
        revision.value++; PersonalArchive.enqueue(context)
    }
    private fun apply(event: TasteEvent) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val added = db.insertWithOnConflict("events", null, ContentValues().apply { put("id", event.id) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            val previousAt = db.rawQuery("SELECT updated FROM entities WHERE kind=? AND item=?", arrayOf(event.state.kind.name, event.state.key)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
            if (added && previousAt <= event.at) {
                db.insertWithOnConflict("entities", null, ContentValues().apply {
                    put("kind", event.state.kind.name); put("item", event.state.key); put("payload", gson.toJson(event.state)); put("updated", event.at)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun importJournal(file: File) {
        file.useLines { lines -> lines.forEach { line ->
            if (line.length <= 64_000) runCatching { gson.fromJson(line, TasteEvent::class.java) }.getOrNull()
                ?.takeIf { it.id.isNotBlank() && it.state.key.isNotBlank() }?.let(::apply)
        } }
        revision.value++
    }
    companion object {
        @Volatile private var instance: TasteMemory? = null
        fun get(context: Context): TasteMemory = instance ?: synchronized(this) { instance ?: TasteMemory(context.applicationContext).also { instance = it } }
        fun artistKey(name: String) = name.trim().lowercase(java.util.Locale.ROOT)
        fun albumKey(title: String, artist: String) = artistKey(title) + "\u001f" + artistKey(artist)
        /** Low-discrepancy rotation: each item samples the interval evenly over repeated reviews. */
        fun interval(key: String, cycle: Int): Long {
            val phase = (key.hashCode().toLong() and 0xffffffffL).toDouble() / 4294967296.0
            val x = phase + cycle * .6180339887498949
            return ((7.0 + 14.0 * (x - floor(x))) * 86_400_000L).toLong()
        }
    }
}
