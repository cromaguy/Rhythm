/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import chromahub.rhythm.app.features.local.data.database.dao.ArtistDao
import chromahub.rhythm.app.features.local.data.database.dao.AudioBookmarkDao
import chromahub.rhythm.app.features.local.data.database.dao.ListeningStatsDao
import chromahub.rhythm.app.features.local.data.database.dao.OfflineTrackDao
import chromahub.rhythm.app.features.local.data.database.dao.PendingScrobbleDao
import chromahub.rhythm.app.features.local.data.database.dao.PlaylistDao
import chromahub.rhythm.app.features.local.data.database.dao.SongArtistDao
import chromahub.rhythm.app.features.local.data.database.dao.SongDao
import chromahub.rhythm.app.features.local.data.database.dao.TransitionRuleDao
import chromahub.rhythm.app.features.local.data.database.entity.ArtistEntity
import chromahub.rhythm.app.features.local.data.database.entity.AudioBookmarkEntity
import chromahub.rhythm.app.features.local.data.database.entity.ListeningSpanEntity
import chromahub.rhythm.app.features.local.data.database.entity.OfflineTrackEntity
import chromahub.rhythm.app.features.local.data.database.entity.PendingScrobbleEntity
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistEntity
import chromahub.rhythm.app.features.local.data.database.entity.PlaylistSongEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongArtistEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEngagementEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongFtsEntity
import chromahub.rhythm.app.features.local.data.database.entity.TransitionRuleEntity

@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        ArtistEntity::class,
        SongArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        AudioBookmarkEntity::class,
        TransitionRuleEntity::class,
        ListeningSpanEntity::class,
        SongEngagementEntity::class,
        OfflineTrackEntity::class,
        PendingScrobbleEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class RhythmDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun songArtistDao(): SongArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun audioBookmarkDao(): AudioBookmarkDao
    abstract fun transitionRuleDao(): TransitionRuleDao
    abstract fun listeningStatsDao(): ListeningStatsDao
    abstract fun offlineTrackDao(): OfflineTrackDao
    abstract fun pendingScrobbleDao(): PendingScrobbleDao

    companion object {
        @Volatile
        private var INSTANCE: RhythmDatabase? = null

        // Migration from version 1 to 2: Add artist and song-artist tables
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create artists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `artists` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `artworkUri` TEXT, 
                        `numberOfAlbums` INTEGER NOT NULL, 
                        `numberOfTracks` INTEGER NOT NULL, 
                        `groupByAlbumArtist` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)

                // Create song_artists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_artists` (
                        `songId` TEXT NOT NULL, 
                        `artistName` TEXT NOT NULL, 
                        `groupByAlbumArtist` INTEGER NOT NULL, 
                        PRIMARY KEY(`songId`, `artistName`, `groupByAlbumArtist`)
                    )
                """)
            }
        }

        // Migration from version 2 to 3: No schema changes, just version bump
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes needed, just ensure tables exist
            }
        }

        // Migration from version 3 to 4: Switch from destructive to proper migrations
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes, just preserve existing data
            }
        }

        // Migration from version 4 to 5: Persist multi-disc ordering metadata.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Migration from version 5 to 6: Persist song-level modified timestamp.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE songs SET dateModified = dateAdded WHERE dateModified = 0")
            }
        }

        // Migration from version 6 to 7: Add path column to songs table
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN path TEXT")
            }
        }

        // Migration from version 7 to 8: Add playlists and playlist_songs tables
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `dateCreated` INTEGER NOT NULL, 
                        `dateModified` INTEGER NOT NULL, 
                        `artworkUri` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_songs` (
                        `playlistId` TEXT NOT NULL, 
                        `songId` TEXT NOT NULL, 
                        `orderIndex` INTEGER NOT NULL, 
                        PRIMARY KEY(`playlistId`, `songId`)
                    )
                """)
            }
        }

        // Migration from version 8 to 9: Fix dateAdded and dateModified timestamps stored in seconds instead of milliseconds
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE songs SET dateAdded = dateAdded * 1000 WHERE dateAdded > 0 AND dateAdded < 100000000000")
                db.execSQL("UPDATE songs SET dateModified = dateModified * 1000 WHERE dateModified > 0 AND dateModified < 100000000000")
            }
        }

        // Migration from version 9 to 10: FTS4 search, playlist FK cascade + orphan cleanup,
        // query indexes, and bookmarks/transitions/listening-stats/offline/pending-scrobble tables.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_albumId` ON `songs` (`albumId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist` ON `songs` (`artist`)")

                db.execSQL("DELETE FROM `playlist_songs` WHERE `playlistId` NOT IN (SELECT `id` FROM `playlists`)")
                db.execSQL("DELETE FROM `song_artists` WHERE `songId` NOT IN (SELECT `id` FROM `songs`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_songs_new` (
                        `playlistId` TEXT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `songId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `playlist_songs_new` (`playlistId`, `songId`, `orderIndex`)
                    SELECT `playlistId`, `songId`, `orderIndex` FROM `playlist_songs`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `playlist_songs`")
                db.execSQL("ALTER TABLE `playlist_songs_new` RENAME TO `playlist_songs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)")

                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `songs_fts` USING FTS4(`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `albumArtist` TEXT, `genre` TEXT, content=`songs`)"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_BEFORE_UPDATE` BEFORE UPDATE ON `songs` BEGIN DELETE FROM `songs_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_BEFORE_DELETE` BEFORE DELETE ON `songs` BEGIN DELETE FROM `songs_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_AFTER_UPDATE` AFTER UPDATE ON `songs` BEGIN INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`, `albumArtist`, `genre`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`, NEW.`albumArtist`, NEW.`genre`); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_songs_fts_AFTER_INSERT` AFTER INSERT ON `songs` BEGIN INSERT INTO `songs_fts`(`docid`, `title`, `artist`, `album`, `albumArtist`, `genre`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`artist`, NEW.`album`, NEW.`albumArtist`, NEW.`genre`); END"
                )
                db.execSQL("INSERT INTO `songs_fts`(`songs_fts`) VALUES('rebuild')")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audio_bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `songTitle` TEXT NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_bookmarks_songId` ON `audio_bookmarks` (`songId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transition_rules` (
                        `id` TEXT NOT NULL,
                        `scopeType` TEXT NOT NULL,
                        `scopeId` TEXT,
                        `fromSongId` TEXT,
                        `toSongId` TEXT,
                        `mode` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `curveIn` TEXT NOT NULL,
                        `curveOut` TEXT NOT NULL,
                        `overlapPercent` REAL NOT NULL,
                        `bpmSync` INTEGER NOT NULL,
                        `harmonicMatch` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transition_rules_scopeType_scopeId` ON `transition_rules` (`scopeType`, `scopeId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `listening_spans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `songTitle` TEXT,
                        `artist` TEXT,
                        `album` TEXT,
                        `genre` TEXT,
                        `startedAtMs` INTEGER NOT NULL,
                        `endedAtMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_spans_songId` ON `listening_spans` (`songId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_spans_startedAtMs` ON `listening_spans` (`startedAtMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `song_engagements` (
                        `songId` TEXT NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `totalPlayDurationMs` INTEGER NOT NULL,
                        `lastPlayedAtMs` INTEGER NOT NULL,
                        `skipCount` INTEGER NOT NULL,
                        PRIMARY KEY(`songId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_tracks` (
                        `songId` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `serverUrl` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `artworkPath` TEXT,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `progress` REAL NOT NULL,
                        `downloadedAtMs` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`songId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_tracks_status` ON `offline_tracks` (`status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_scrobbles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `playedAtMs` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `lastAttemptAtMs` INTEGER,
                        `lastError` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_scrobbles_provider_playedAtMs` ON `pending_scrobbles` (`provider`, `playedAtMs`)")
            }
        }

        fun getInstance(context: Context): RhythmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RhythmDatabase::class.java,
                    "rhythm_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
