/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class RhythmDatabaseMigrationTest {

    private fun recordExecSql(): Pair<SupportSQLiteDatabase, MutableList<String>> {
        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty() && args[0] is String) {
                executedSql.add((args[0] as String).trim())
                return@newProxyInstance Unit
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase
        return db to executedSql
    }

    @Test
    fun migration9To10_coversAllExportedV10TablesIndexesAndFtsTriggers() {
        val (db, executedSql) = recordExecSql()
        RhythmDatabase.MIGRATION_9_10.migrate(db)
        val joined = executedSql.joinToString("\n")

        val schemaCandidates = listOf(
            File("schemas/chromahub.rhythm.app.features.local.data.database.RhythmDatabase/10.json"),
            File("app/schemas/chromahub.rhythm.app.features.local.data.database.RhythmDatabase/10.json")
        )
        val schemaFile = schemaCandidates.firstOrNull { it.exists() }
        assertTrue("Expected KSP-exported schema 10.json at ${schemaCandidates.map { it.path }}", schemaFile != null)

        val root = JSONObject(schemaFile!!.readText()).getJSONObject("database")
        val entities = root.getJSONArray("entities")
        val tableNames = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        val expectedNewTables = listOf(
            "songs_fts",
            "audio_bookmarks",
            "transition_rules",
            "listening_spans",
            "song_engagements",
            "offline_tracks",
            "pending_scrobbles"
        )
        expectedNewTables.forEach { table ->
            assertTrue("Schema missing table $table", table in tableNames)
            assertTrue("MIGRATION_9_10 must create table $table", joined.contains("`$table`"))
        }

        assertTrue(joined.contains("DELETE FROM `playlist_songs` WHERE `playlistId` NOT IN (SELECT `id` FROM `playlists`)"))
        assertTrue(joined.contains("DELETE FROM `song_artists` WHERE `songId` NOT IN (SELECT `id` FROM `songs`)"))
        assertTrue(joined.contains("FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"))
        assertTrue(joined.contains("INSERT INTO `songs_fts`(`songs_fts`) VALUES('rebuild')"))
        assertTrue(joined.contains("index_songs_albumId"))
        assertTrue(joined.contains("index_songs_artist"))
    }

    @Test
    fun ftsQuerySanitizer_formatsSafePrefixTokensAndRejectsSymbols() {
        assertEquals("daft* punk* 2001* discovery*", FtsQuerySanitizer.buildPrefixMatchQuery("Daft Punk (2001) - Discovery!"))
        assertEquals("m83* midnight* city*", FtsQuerySanitizer.buildPrefixMatchQuery("m83: \"midnight\" city*"))
        assertEquals("", FtsQuerySanitizer.buildPrefixMatchQuery("   --- *** \"\"   "))
    }
}
