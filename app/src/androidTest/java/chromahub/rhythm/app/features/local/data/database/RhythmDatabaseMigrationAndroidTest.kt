/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration verification using Room's [MigrationTestHelper] against exported schemas.
 */
@RunWith(AndroidJUnit4::class)
class RhythmDatabaseMigrationAndroidTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RhythmDatabase::class.java
    )

    @Test
    fun migrate9To10_preservesPlaylistsAndRebuildsFts() {
        val dbName = "rhythm-migration-test"
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `songs` (
                    `id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL, `albumId` TEXT NOT NULL, `duration` INTEGER NOT NULL,
                    `uri` TEXT NOT NULL, `artworkUri` TEXT, `trackNumber` INTEGER NOT NULL,
                    `year` INTEGER NOT NULL, `genre` TEXT, `dateAdded` INTEGER NOT NULL,
                    `dateModified` INTEGER NOT NULL, `albumArtist` TEXT, `bitrate` INTEGER,
                    `sampleRate` INTEGER, `channels` INTEGER, `codec` TEXT,
                    `discNumber` INTEGER NOT NULL, `path` TEXT, PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` TEXT NOT NULL, `name` TEXT NOT NULL, `dateCreated` INTEGER NOT NULL,
                    `dateModified` INTEGER NOT NULL, `artworkUri` TEXT, PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_songs` (
                    `playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `songId`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `song_artists` (
                    `songId` TEXT NOT NULL, `artistName` TEXT NOT NULL, `groupByAlbumArtist` INTEGER NOT NULL,
                    PRIMARY KEY(`songId`, `artistName`, `groupByAlbumArtist`),
                    FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO songs (id, title, artist, album, albumId, duration, uri, trackNumber, year, dateAdded, dateModified, discNumber)
                VALUES ('s1', 'Veridis Quo', 'Daft Punk', 'Discovery', 'a1', 345000, 'content://1', 11, 2001, 1700000000000, 1700000000000, 1)
                """.trimIndent()
            )
            execSQL("INSERT INTO playlists VALUES ('p1', 'Favorites', 1000, 1000, NULL)")
            execSQL("INSERT INTO playlist_songs VALUES ('p1', 's1', 0)")
            execSQL("INSERT INTO playlist_songs VALUES ('missing_pl', 's1', 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 10, true, RhythmDatabase.MIGRATION_9_10)
        migrated.query("SELECT songId FROM playlist_songs").use { cursor ->
            assert(cursor.count == 1)
        }
        migrated.query("SELECT COUNT(*) FROM songs_fts WHERE songs_fts MATCH 'veridis*'").use { cursor ->
            cursor.moveToFirst()
            assert(cursor.getInt(0) == 1)
        }
    }
}
