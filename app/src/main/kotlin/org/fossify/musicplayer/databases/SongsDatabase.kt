package org.fossify.musicplayer.databases

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.musicplayer.interfaces.*
import org.fossify.musicplayer.models.*
import org.fossify.musicplayer.objects.MyExecutor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(entities = [Track::class, Playlist::class, QueueItem::class, Artist::class, Album::class, Genre::class, PlayEvent::class, ElevenLabsApiKey::class, PlaylistTrack::class], version = 58)
@TypeConverters(UuidConverter::class)
abstract class SongsDatabase : RoomDatabase() {

    abstract fun SongsDao(): SongsDao

    abstract fun PlaylistsDao(): PlaylistsDao

    abstract fun QueueItemsDao(): QueueItemsDao

    abstract fun ArtistsDao(): ArtistsDao

    abstract fun AlbumsDao(): AlbumsDao

    abstract fun GenresDao(): GenresDao

    abstract fun PlayEventDao(): PlayEventDao

    abstract fun ElevenLabsApiKeyDao(): ElevenLabsApiKeyDao
    
    abstract fun PlaylistTracksDao(): PlaylistTracksDao

    companion object {
        const val DB_VERSION = 58
        private var db: SongsDatabase? = null

        fun getInstance(context: Context): SongsDatabase {
            if (db == null) {
                synchronized(SongsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, SongsDatabase::class.java, "songs.db")
                            .setQueryExecutor(MyExecutor.myExecutor)
                            .addCallback(DatabaseBackupCallback(context.applicationContext))
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .addMigrations(MIGRATION_11_12)
                            .addMigrations(MIGRATION_12_13)
                            .addMigrations(MIGRATION_13_14)
                            .addMigrations(MIGRATION_14_15)
                            .addMigrations(MIGRATION_15_16)
                            .addMigrations(MIGRATION_16_17)
                            .addMigrations(MIGRATION_17_18)
                            .addMigrations(MIGRATION_18_19)
                            .addMigrations(MIGRATION_19_20)
                            .addMigrations(MIGRATION_20_21)
                            .addMigrations(MIGRATION_21_22)
                            .addMigrations(MIGRATION_22_23)
                            .addMigrations(MIGRATION_23_24)
                            .addMigrations(MIGRATION_24_25)
                            .addMigrations(MIGRATION_25_26)
                            .addMigrations(MIGRATION_26_27)
                            .addMigrations(MIGRATION_27_28)
                            .addMigrations(MIGRATION_28_29)
                            .addMigrations(MIGRATION_29_30)
                            .addMigrations(MIGRATION_30_31)
                            .addMigrations(MIGRATION_31_32)
                            .addMigrations(MIGRATION_32_33)
                            .addMigrations(MIGRATION_33_34)
                            .addMigrations(MIGRATION_34_35)
                            .addMigrations(MIGRATION_35_36)
                            .addMigrations(MIGRATION_36_37)
                            .addMigrations(MIGRATION_37_38)
                            .addMigrations(MIGRATION_38_39)
                            .addMigrations(MIGRATION_39_40)
                            .addMigrations(MIGRATION_40_41)
                            .addMigrations(MIGRATION_41_42)
                            .addMigrations(MIGRATION_42_43)
                            .addMigrations(MIGRATION_43_44)
                            .addMigrations(MIGRATION_44_45)
                            .addMigrations(MIGRATION_45_46)
                            .addMigrations(MIGRATION_46_47)
                            .addMigrations(MIGRATION_47_48)
                            .addMigrations(MIGRATION_48_49)
                            .addMigrations(MIGRATION_49_50)
                            .addMigrations(MIGRATION_50_51)
                            .addMigrations(MIGRATION_51_52)
                            .addMigrations(MIGRATION_52_53)
                            .addMigrations(MIGRATION_53_54)
                            .addMigrations(MIGRATION_54_55)
                            .addMigrations(MIGRATION_55_56)
                            .addMigrations(MIGRATION_56_57)
                            .addMigrations(MIGRATION_57_58)
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }

        // removing the "type" value of Song
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                            "album TEXT NOT NULL, playlist_id INTEGER NOT NULL, PRIMARY KEY(path, playlist_id))"
                    )

                    execSQL(
                        "INSERT INTO songs_new (media_store_id, title, artist, path, duration, album, playlist_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, playlist_id FROM songs"
                    )

                    execSQL("DROP TABLE songs")
                    execSQL("ALTER TABLE songs_new RENAME TO songs")

                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_id` ON `playlists` (`id`)")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN track_id INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE songs ADD COLUMN cover_art TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE `queue_items` (`track_id` INTEGER NOT NULL PRIMARY KEY, `track_order` INTEGER NOT NULL, `is_current` INTEGER NOT NULL, `last_position` INTEGER NOT NULL)")
            }
        }

        // change the primary keys from path + playlist_id to media_store_id + playlist_id
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                            "album TEXT NOT NULL, cover_art TEXT default '' NOT NULL, playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(media_store_id, playlist_id))"
                    )

                    execSQL(
                        "INSERT OR IGNORE INTO songs_new (media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id FROM songs"
                    )

                    execSQL("DROP TABLE songs")
                    execSQL("ALTER TABLE songs_new RENAME TO tracks")
                }
            }
        }

        // adding an autoincrementing "id" field, replace primary keys with indices
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE tracks_new (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `media_store_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `path` TEXT NOT NULL, `duration` INTEGER NOT NULL, " +
                            "`album` TEXT NOT NULL, `cover_art` TEXT default '' NOT NULL, `playlist_id` INTEGER NOT NULL, `track_id` INTEGER NOT NULL DEFAULT 0)"
                    )

                    execSQL(
                        "INSERT OR IGNORE INTO tracks_new (media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id FROM tracks"
                    )

                    execSQL("DROP TABLE tracks")
                    execSQL("ALTER TABLE tracks_new RENAME TO tracks")

                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_id` ON `tracks` (`media_store_id`, `playlist_id`)")
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("CREATE TABLE `artists` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `album_cnt` INTEGER NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art_id` INTEGER NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_id` ON `artists` (`id`)")

                    execSQL("CREATE TABLE `albums` (`id` INTEGER NOT NULL PRIMARY KEY, `artist` TEXT NOT NULL, `title` TEXT NOT NULL, `cover_art` TEXT NOT NULL, `year` INTEGER NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_id` ON `albums` (`id`)")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN folder_name TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN track_cnt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN artist_id INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tracks ADD COLUMN album_id INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN order_in_playlist INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE tracks ADD COLUMN artist_id INTEGER NOT NULL DEFAULT 0")
                    execSQL("ALTER TABLE tracks ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                    execSQL("ALTER TABLE tracks ADD COLUMN flags INTEGER NOT NULL DEFAULT 0")

                    execSQL("CREATE TABLE `artists_new` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `album_cnt` INTEGER NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art` TEXT NOT NULL)")
                    execSQL("INSERT OR IGNORE INTO artists_new (id, title, album_cnt, track_cnt) SELECT id, title, album_cnt, track_cnt FROM artists")
                    execSQL("DROP TABLE artists")
                    execSQL("ALTER TABLE artists_new RENAME TO artists")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_id` ON `artists` (`id`)")

                    database.execSQL("ALTER TABLE tracks ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE albums ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE tracks ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                    execSQL("ALTER TABLE tracks ADD COLUMN genre_id INTEGER NOT NULL DEFAULT 0")

                    execSQL("CREATE TABLE `genres` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art` TEXT NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_genres_id` ON `genres` (`id`)")
                }
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE tracks ADD COLUMN disc_number INTEGER DEFAULT NULL")
                }
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE tracks_new (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `media_store_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `path` TEXT NOT NULL, `duration` INTEGER NOT NULL, " +
                            "`album` TEXT NOT NULL, genre TEXT NOT NULL DEFAULT '', `cover_art` TEXT default '' NOT NULL, `playlist_id` INTEGER NOT NULL, `track_id` INTEGER DEFAULT NULL, disc_number INTEGER DEFAULT NULL, " +
                            "folder_name TEXT default '' NOT NULL, album_id INTEGER NOT NULL DEFAULT 0, artist_id INTEGER NOT NULL DEFAULT 0, genre_id INTEGER NOT NULL DEFAULT 0, year INTEGER NOT NULL DEFAULT 0, date_added INTEGER NOT NULL DEFAULT 0, " +
                            "order_in_playlist INTEGER NOT NULL DEFAULT 0, flags INTEGER NOT NULL DEFAULT 0)"
                    )
                    execSQL(
                        "INSERT INTO tracks_new(id,media_store_id,title,artist,path,duration,album,genre,cover_art,playlist_id,track_id,disc_number,folder_name,album_id,artist_id,genre_id,year,date_added,order_in_playlist,flags) " +
                            "SELECT id,media_store_id,title,artist,path,duration,album,genre,cover_art,playlist_id,track_id,disc_number,folder_name,album_id,artist_id,genre_id,year,date_added,order_in_playlist,flags " +
                            "FROM tracks"
                    )
                    execSQL("DROP TABLE tracks")
                    execSQL("ALTER TABLE tracks_new RENAME to tracks")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_id` ON `tracks` (`media_store_id`, `playlist_id`)")
                }
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Create play_events table for event store (append-only log)
                    execSQL(
                        "CREATE TABLE IF NOT EXISTS `play_events` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`trackGuid` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL" +
                            ")"
                    )
                }
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add playbackSpeed column to play_events table (nullable for backward compatibility)
                    execSQL("ALTER TABLE `play_events` ADD COLUMN `playbackSpeed` REAL")
                }
            }
        }

        // Recreate tracks table to fix compatibility issues
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Drop existing tracks table if it exists
                    execSQL("DROP TABLE IF EXISTS tracks")
                    
                    // Create tracks table with correct structure matching Track model
                    execSQL(
                        "CREATE TABLE `tracks` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`media_store_id` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`artist` TEXT NOT NULL, " +
                            "`path` TEXT NOT NULL, " +
                            "`duration` INTEGER NOT NULL, " +
                            "`album` TEXT NOT NULL, " +
                            "`genre` TEXT NOT NULL DEFAULT '', " +
                            "`cover_art` TEXT NOT NULL DEFAULT '', " +
                            "`playlist_id` INTEGER NOT NULL, " +
                            "`track_id` INTEGER, " +
                            "`disc_number` INTEGER, " +
                            "`folder_name` TEXT NOT NULL DEFAULT '', " +
                            "`album_id` INTEGER NOT NULL DEFAULT 0, " +
                            "`artist_id` INTEGER NOT NULL DEFAULT 0, " +
                            "`genre_id` INTEGER NOT NULL DEFAULT 0, " +
                            "`year` INTEGER NOT NULL DEFAULT 0, " +
                            "`date_added` INTEGER NOT NULL DEFAULT 0, " +
                            "`order_in_playlist` INTEGER NOT NULL DEFAULT 0, " +
                            "`flags` INTEGER NOT NULL DEFAULT 0" +
                            ")"
                    )
                    
                    // Create unique index on (media_store_id, playlist_id)
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_id` ON `tracks` (`media_store_id`, `playlist_id`)")
                }
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Empty migration - no schema changes (compatibility with previous test versions)
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No changes - this migration exists only for version compatibility
            }
        }

        // Remove total_duration_seconds column added accidentally in test versions
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Recreate playlists table without total_duration_seconds column
                    execSQL("CREATE TABLE playlists_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)")
                    execSQL("INSERT INTO playlists_new (id, title) SELECT id, title FROM playlists")
                    execSQL("DROP TABLE playlists")
                    execSQL("ALTER TABLE playlists_new RENAME TO playlists")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_id ON playlists (id)")
                }
            }
        }

        // Add ID3 TXXX tags support from MP3 files (transcription, guid, dates, checksum)
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add columns for ID3 TXXX tags from MP3
                    execSQL("ALTER TABLE tracks ADD COLUMN transcription TEXT DEFAULT NULL")
                    execSQL("ALTER TABLE tracks ADD COLUMN guid TEXT DEFAULT NULL")
                    execSQL("ALTER TABLE tracks ADD COLUMN created_on_timestamp INTEGER DEFAULT NULL")
                    execSQL("ALTER TABLE tracks ADD COLUMN checksum_audio TEXT DEFAULT NULL")

                    // Create UNIQUE index on guid (UUID v4 from MP3 tags)
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_guid` ON `tracks` (`guid`)")

                    // Create UNIQUE index on checksum_audio for duplicate audio detection
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_checksum_audio` ON `tracks` (`checksum_audio`)")
                }
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Clear custom tag values for the hardcoded test file
                    // This allows it to be re-scanned with the new "Scan All" functionality
                    execSQL("""
                        UPDATE tracks 
                        SET transcription = NULL, 
                            guid = NULL, 
                            created_on_timestamp = NULL, 
                            checksum_audio = NULL
                        WHERE path LIKE '%ElevenLabs_2026-01-30T20_50_33_Antoni_pre_sp100_s50_sb75_se0_b_m2.mp3%'
                    """)
                }
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add index on transcription column for efficient full-text search
                    execSQL("CREATE INDEX IF NOT EXISTS index_tracks_transcription ON tracks(transcription)")
                }
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add transcription_normalized column for Polish character normalization
                    execSQL("ALTER TABLE tracks ADD COLUMN transcription_normalized TEXT DEFAULT NULL")
                    // Add index on normalized column for fast searching
                    execSQL("CREATE INDEX IF NOT EXISTS index_tracks_transcription_normalized ON tracks(transcription_normalized)")
                }
            }
        }

        // Add ElevenLabs API Keys table
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE IF NOT EXISTS `elevenlabs_api_keys` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`email` TEXT NOT NULL, " +
                            "`api_key` TEXT NOT NULL, " +
                            "`is_active` INTEGER NOT NULL DEFAULT 0)"
                    )
                }
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add created_at_utc column with current timestamp for existing rows
                    execSQL("ALTER TABLE `elevenlabs_api_keys` ADD COLUMN `created_at_utc` INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                    
                    // Add last_used_at_utc column (nullable)
                    execSQL("ALTER TABLE `elevenlabs_api_keys` ADD COLUMN `last_used_at_utc` INTEGER")
                }
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add character limit tracking columns (all nullable, will be filled on first API call)
                    execSQL("ALTER TABLE `elevenlabs_api_keys` ADD COLUMN `character_limit` INTEGER")
                    execSQL("ALTER TABLE `elevenlabs_api_keys` ADD COLUMN `character_count` INTEGER")
                    execSQL("ALTER TABLE `elevenlabs_api_keys` ADD COLUMN `character_limit_remaining` INTEGER")
                }
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Add next reset timestamp column
                    execSQL("ALTER TABLE `elevenlabs_api_keys` ADD COLUMN `next_character_count_reset_unix` INTEGER")
                    
                    // Update limits for existing key (from subscription API test)
                    execSQL("""
                        UPDATE `elevenlabs_api_keys` 
                        SET 
                            `character_limit` = 10000,
                            `character_count` = 6638,
                            `character_limit_remaining` = 3362,
                            `next_character_count_reset_unix` = 1772699696
                        WHERE `email` = 'at'
                    """)
                }
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Insert or update API key with user_read permission for history access
                    // First delete if exists, then insert
                    execSQL("DELETE FROM `elevenlabs_api_keys` WHERE `email` = 'ww'")
                    execSQL("""
                        INSERT INTO `elevenlabs_api_keys` 
                        (`email`, `api_key`, `is_active`, `created_at_utc`, `last_used_at_utc`, 
                         `character_limit`, `character_count`, `character_limit_remaining`, 
                         `next_character_count_reset_unix`)
                        VALUES 
                        ('ema', 
                         'va', 
                         1, 
                         ${System.currentTimeMillis()}, 
                         NULL, 
                         10000, 
                         8051, 
                         1949, 
                         NULL)
                    """)
                }
            }
        }

        // Rename columns to clarify they store Unix timestamps (UTC, not timezone-converted)
        // Also fix the bug where timestamps were incorrectly "converted" to Warsaw timezone
        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Check if old columns exist before renaming (makes migration idempotent/safe)
                    val tracksCursor = query("PRAGMA table_info(tracks)")
                    val tracksColumns = mutableSetOf<String>()
                    while (tracksCursor.moveToNext()) {
                        tracksColumns.add(tracksCursor.getString(1)) // column name is at index 1
                    }
                    tracksCursor.close()
                    
                    // Rename columns in tracks table only if old names exist
                    if ("date_added" in tracksColumns) {
                        execSQL("ALTER TABLE `tracks` RENAME COLUMN `date_added` TO `added_at_timestamp_unix`")
                    }
                    if ("created_on_timestamp" in tracksColumns) {
                        execSQL("ALTER TABLE `tracks` RENAME COLUMN `created_on_timestamp` TO `tag_txxx_created_at_unix`")
                    }
                    
                    // Check albums table
                    val albumsCursor = query("PRAGMA table_info(albums)")
                    val albumsColumns = mutableSetOf<String>()
                    while (albumsCursor.moveToNext()) {
                        albumsColumns.add(albumsCursor.getString(1))
                    }
                    albumsCursor.close()
                    
                    // Rename column in albums table only if old name exists
                    if ("date_added" in albumsColumns) {
                        execSQL("ALTER TABLE `albums` RENAME COLUMN `date_added` TO `added_at_timestamp_unix`")
                    }
                    
                    // NOTE: After this migration, run "Scan ID Tags" to fix incorrectly converted timestamps
                    // Previous code added 3600s offset (Warsaw timezone) which created invalid Unix timestamps
                }
            }
        }

        // Delete duplicate entries for ElevenLabs files without tags
        // Keeping the entry with the LOWEST ID for each media_store_id (same physical file)
        // Then add UNIQUE index on media_store_id to prevent future duplicates
        // NOTE: This migration was corrected - originally created composite index (media_store_id, playlist_id)
        // which still allowed duplicates. Migration 44->45 fixes this.
        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Step 1: DYNAMICALLY delete duplicate entries
                    // For each media_store_id that appears more than once:
                    // - Keep the entry with the LOWEST id (usually playlist_id = 0 or first occurrence)
                    // - Delete all other entries with the same media_store_id
                    execSQL("""
                        DELETE FROM tracks 
                        WHERE id NOT IN (
                            SELECT MIN(id) 
                            FROM tracks 
                            GROUP BY media_store_id
                        )
                        AND media_store_id IN (
                            SELECT media_store_id 
                            FROM tracks 
                            GROUP BY media_store_id 
                            HAVING COUNT(*) > 1
                        )
                    """.trimIndent())
                    
                    // Step 2: Drop old indexes (if exists) that allowed duplicates
                    execSQL("DROP INDEX IF EXISTS `index_tracks_id`")
                    execSQL("DROP INDEX IF EXISTS `index_tracks_media_store_id_playlist_id`")
                    
                    // Step 3: Create UNIQUE index ONLY on media_store_id to prevent future duplicates
                    // This ensures each physical file can only have ONE entry in the database
                    // (not one per playlist, but ONE total)
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_media_store_id` ON `tracks` (`media_store_id`)")
                }
            }
        }
        
        // MIGRATION 37 -> 38: Update play_events.trackGuid from path to real GUID
        // For tracks that have a GUID in ID3 tags, update the trackGuid column
        // This makes play history survive file moves/renames
        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Update trackGuid from path to real GUID where possible
                    // Only updates if:
                    // 1. The path in trackGuid matches a track in the tracks table
                    // 2. That track has a non-null GUID
                    // This makes history survive file moves/renames for tracks with GUID
                    execSQL("""
                        UPDATE play_events
                        SET trackGuid = (
                            SELECT guid 
                            FROM tracks 
                            WHERE tracks.path = play_events.trackGuid 
                              AND tracks.guid IS NOT NULL
                            LIMIT 1
                        )
                        WHERE EXISTS (
                            SELECT 1 
                            FROM tracks 
                            WHERE tracks.path = play_events.trackGuid 
                              AND tracks.guid IS NOT NULL
                        )
                    """.trimIndent())
                    
                    // Note: Events for tracks without GUID or deleted tracks keep their path
                    // New events will automatically use GUID (fallback to path) via PlayEventLogger
                }
            }
        }
        
        // MIGRATION 38 -> 39: Remove all non-MP3 tracks from database
        // From this version onwards, only MP3 files are scanned (audio/mpeg MIME type)
        // This migration cleans up existing WAV, FLAC, OGG, M4A, etc. files
        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Delete all tracks that are not MP3 files
                    // MP3 files end with .mp3 (case-insensitive)
                    execSQL("""
                        DELETE FROM tracks 
                        WHERE LOWER(path) NOT LIKE '%.mp3'
                    """.trimIndent())
                    
                    // Note: This will also clean up orphaned entries in related tables
                    // via foreign key constraints (if configured) or manual cleanup in next scan
                }
            }
        }
        
        // MIGRATION 39 -> 40: Import play events from old backup (if exists)
        // Looks for /sdcard/Download/songs_backup.db and imports events
        // Only imports events for MP3 tracks with GUID, converts PATH->GUID
        // NOTE: Room executes entire migrate() in a transaction (all-or-nothing)
        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    // Path to old backup (manually placed)
                    val backupPath = "/storage/emulated/0/Download/songs_backup.db"
                    
                    try {
                        // Check if backup file exists using a query
                        // SQLite will fail if file doesn't exist when we ATTACH
                        execSQL("ATTACH DATABASE '$backupPath' AS old_backup")
                        
                        Log.i("SongsDatabase", "Found old backup, importing events...")
                        
                        // TRANSACTIONAL: All 3 steps below execute atomically (all-or-nothing)
                        // If any step fails, entire migration rolls back
                        
                        // Step 1: Insert events with PATH (only MP3 with GUID)
                        // ORDER BY id ASC to preserve chronological order
                        execSQL("""
                            INSERT INTO play_events (trackGuid, timestamp, playbackSpeed)
                            SELECT pe.trackGuid, pe.timestamp, pe.playbackSpeed
                            FROM old_backup.play_events pe
                            WHERE pe.trackGuid IN (
                                SELECT path FROM tracks 
                                WHERE LOWER(path) LIKE '%.mp3'
                                AND guid IS NOT NULL
                                AND guid != ''
                            )
                            ORDER BY pe.id ASC
                        """.trimIndent())
                        
                        // Step 2: Update PATH -> GUID (same as migration 37->38)
                        execSQL("""
                            UPDATE play_events
                            SET trackGuid = (
                                SELECT guid 
                                FROM tracks 
                                WHERE tracks.path = play_events.trackGuid 
                                  AND tracks.guid IS NOT NULL
                                  AND tracks.guid != ''
                                LIMIT 1
                            )
                            WHERE EXISTS (
                                SELECT 1 
                                FROM tracks 
                                WHERE tracks.path = play_events.trackGuid 
                                  AND tracks.guid IS NOT NULL
                                  AND tracks.guid != ''
                            )
                        """.trimIndent())
                        
                        // Step 3: Delete events that couldn't be mapped to GUID (still have PATH)
                        // These are events for tracks without GUID
                        execSQL("""
                            DELETE FROM play_events
                            WHERE trackGuid LIKE '/storage/%'
                        """.trimIndent())
                        
                        // Detach old database
                        execSQL("DETACH DATABASE old_backup")
                        
                        Log.i("SongsDatabase", "Events imported successfully from old backup")
                        
                    } catch (e: Exception) {
                        Log.w("SongsDatabase", "Old backup not found or import failed (this is OK): ${e.message}")
                        // Migration continues - this is not a critical error
                        // The app will work fine without old events
                    }
                }
            }
        }
        
        // MIGRATION 40 -> 41: Retry event import from old backup (fixes ATTACH issue)
        // Uses separate SQLiteDatabase connection instead of ATTACH DATABASE
        // This avoids WAL transaction conflict that prevented 39->40 from working
        private val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    val backupPath = "/storage/emulated/0/Download/songs_backup.db"
                    
                    try {
                        // Check if file exists
                        val backupFile = java.io.File(backupPath)
                        if (!backupFile.exists()) {
                            Log.w("SongsDatabase", "Old backup not found at $backupPath - skipping import")
                            return
                        }
                        
                        Log.i("SongsDatabase", "Opening old backup for event import...")
                        
                        // Open old database as separate read-only connection
                        // This avoids ATTACH DATABASE WAL transaction conflict
                        val oldDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                            backupPath,
                            null,
                            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                        )
                        
                        // Get mapping of PATH -> GUID from current database
                        val pathToGuid = mutableMapOf<String, String>()
                        query("SELECT path, guid FROM tracks WHERE LOWER(path) LIKE '%.mp3' AND guid IS NOT NULL AND guid != ''").use { cursor ->
                            while (cursor.moveToNext()) {
                                val path = cursor.getString(0)
                                val guid = cursor.getString(1)
                                pathToGuid[path] = guid
                            }
                        }
                        
                        Log.i("SongsDatabase", "Found ${pathToGuid.size} valid MP3 tracks with GUID")
                        
                        // SELECT events from old database (ordered by id for chronological order)
                        val cursor = oldDb.rawQuery("""
                            SELECT trackGuid, timestamp, playbackSpeed, id
                            FROM play_events
                            ORDER BY id ASC
                        """, null)
                        
                        var totalEvents = 0
                        var importedEvents = 0
                        
                        // Prepare INSERT statement for batch insert
                        val insertStmt = compileStatement(
                            "INSERT INTO play_events (trackGuid, timestamp, playbackSpeed) VALUES (?, ?, ?)"
                        )
                        
                        while (cursor.moveToNext()) {
                            totalEvents++
                            val trackPath = cursor.getString(0)  // This is PATH in old database
                            val timestamp = cursor.getLong(1)
                            val playbackSpeed = if (cursor.isNull(2)) null else cursor.getFloat(2)
                            
                            // Get GUID for this path (if exists)
                            val guid = pathToGuid[trackPath]
                            
                            // Only import if we found GUID for this path
                            if (guid != null) {
                                insertStmt.bindString(1, guid)  // Insert GUID directly!
                                insertStmt.bindLong(2, timestamp)
                                if (playbackSpeed != null) {
                                    insertStmt.bindDouble(3, playbackSpeed.toDouble())
                                } else {
                                    insertStmt.bindNull(3)
                                }
                                insertStmt.executeInsert()
                                importedEvents++
                            }
                        }
                        
                        cursor.close()
                        oldDb.close()
                        
                        Log.i("SongsDatabase", "Event import completed successfully!")
                        Log.i("SongsDatabase", "Imported $importedEvents/$totalEvents events (all with GUID)")
                        
                    } catch (e: Exception) {
                        Log.e("SongsDatabase", "Failed to import events from old backup", e)
                        // Migration continues - this is not a critical error
                    }
                }
            }
        }
        
        // MIGRATION 41 -> 42: Optimized event import (fixes timeout issue)
        // Inserts GUID directly instead of PATH + UPDATE
        // Same as 40->41 but optimized for large datasets
        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration - v41 already has the optimized import code
                // This just bumps version to v42
                // Note: Some devices may have duplicate events if migration ran twice
                Log.i("SongsDatabase", "Migration 41->42: Version bump (import already done)")
            }
        }
        
        // MIGRATION 42 -> 43: Remove duplicate events
        // If migration 40->41 ran twice, we have duplicates (same trackGuid + timestamp)
        // Keep only the event with lowest ID (first inserted)
        private val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Checking for duplicate events...")
                    
                    // Check if there are duplicates
                    query("SELECT COUNT(*) FROM (SELECT trackGuid, timestamp FROM play_events GROUP BY trackGuid, timestamp HAVING COUNT(*) > 1)").use { cursor ->
                        cursor.moveToFirst()
                        val dupes = cursor.getInt(0)
                        
                        if (dupes > 0) {
                            Log.i("SongsDatabase", "Found $dupes duplicate groups, removing...")
                            
                            // Delete duplicates, keeping the one with lowest ID (first inserted)
                            execSQL("""
                                DELETE FROM play_events
                                WHERE id NOT IN (
                                    SELECT MIN(id) 
                                    FROM play_events 
                                    GROUP BY trackGuid, timestamp
                                )
                            """.trimIndent())
                            
                            Log.i("SongsDatabase", "Duplicate events removed successfully")
                        } else {
                            Log.i("SongsDatabase", "No duplicates found, migration 42->43 complete")
                        }
                    }
                }
            }
        }

        // MIGRATION 43 -> 44: Remove non-MP3 tracks (legacy data from before MP3 filter was added to scanner)
        private val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Cleaning non-MP3 tracks...")
                    
                    // Count non-MP3 tracks before deletion
                    query("SELECT COUNT(*) FROM tracks WHERE LOWER(path) NOT LIKE '%.mp3'").use { cursor ->
                        cursor.moveToFirst()
                        val nonMp3Count = cursor.getInt(0)
                        
                        if (nonMp3Count > 0) {
                            Log.i("SongsDatabase", "Found $nonMp3Count non-MP3 tracks to remove")
                            
                            // Delete non-MP3 tracks (WAV, FLAC, OGG, M4A, etc.)
                            execSQL("DELETE FROM tracks WHERE LOWER(path) NOT LIKE '%.mp3'")
                            
                            Log.i("SongsDatabase", "Non-MP3 tracks removed successfully")
                        } else {
                            Log.i("SongsDatabase", "No non-MP3 tracks found, migration 43->44 complete")
                        }
                    }
                }
            }
        }

        // MIGRATION 44 -> 45: Fix duplicate tracks issue
        // The composite index (media_store_id, playlist_id) created in migration 36->37 
        // allowed the same file to exist in multiple playlists, causing duplicates.
        // This migration removes duplicates and creates proper UNIQUE index on media_store_id only.
        private val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Fixing duplicate tracks with composite index...")
                    
                    // Count duplicates before deletion
                    query("""
                        SELECT COUNT(*) FROM (
                            SELECT media_store_id FROM tracks 
                            GROUP BY media_store_id 
                            HAVING COUNT(*) > 1
                        )
                    """.trimIndent()).use { cursor ->
                        cursor.moveToFirst()
                        val dupeGroups = cursor.getInt(0)
                        
                        if (dupeGroups > 0) {
                            Log.i("SongsDatabase", "Found $dupeGroups duplicate media_store_id groups")
                            
                            // For each media_store_id that has duplicates:
                            // Keep the entry with HIGHEST playlist_id (main playlists, not junk playlist_id=0)
                            // and HIGHEST id (most recent entry)
                            execSQL("""
                                DELETE FROM tracks 
                                WHERE id NOT IN (
                                    SELECT MAX(id)
                                    FROM tracks 
                                    GROUP BY media_store_id
                                )
                                AND media_store_id IN (
                                    SELECT media_store_id 
                                    FROM tracks 
                                    GROUP BY media_store_id 
                                    HAVING COUNT(*) > 1
                                )
                            """.trimIndent())
                            
                            Log.i("SongsDatabase", "Duplicates removed")
                        } else {
                            Log.i("SongsDatabase", "No duplicates found")
                        }
                    }
                    
                    // Drop the problematic composite index
                    Log.i("SongsDatabase", "Dropping composite index...")
                    execSQL("DROP INDEX IF EXISTS `index_tracks_media_store_id_playlist_id`")
                    
                    // Create proper UNIQUE index on media_store_id only
                    // This ensures one physical file = one database entry (regardless of playlists)
                    Log.i("SongsDatabase", "Creating UNIQUE index on media_store_id...")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_media_store_id` ON `tracks` (`media_store_id`)")
                    
                    Log.i("SongsDatabase", "Migration 44->45 complete")
                }
            }
        }

        // MIGRATION 45 -> 46: Remove specific "dead" track entries (files that don't exist)
        // These files returned "No such file or directory" when attempting to delete via ADB
        private val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Removing dead track entries...")
                    
                    // Delete specific tracks that were physically deleted from device
                    // These files existed but were removed due to lack of GUID
                    val deadPaths = listOf(
                        "/storage/emulated/0/Download/Med/_morn/_energia i radość/ElevenLabs_2024-12-28T17_43_01_Antoni_pre_s50_sb75_se0_b_m2 (1).mp3",
                        "/storage/emulated/0/Download/Med/_morn/Nie boję się że inni zobaczą moją porażkę/ElevenLabs_2025-07-08T11_32_07_Antoni_pre_sp100_s50_sb75_se0_b_m2 (1).mp3",
                        "/storage/emulated/0/Download/Med/_morn/Nie boję się że inni zobaczą moją porażkę/ElevenLabs_2025-07-08T11_33_36_Antoni_pre_sp100_s50_sb75_se0_b_m2 (2).mp3",
                        "/storage/emulated/0/Download/Med/_morn/Nie boję się że inni zobaczą moją porażkę/ElevenLabs_2025-07-08T11_36_18_Antoni_pre_sp100_s50_sb75_se0_b_m2 (1).mp3"
                    )
                    
                    var deletedCount = 0
                    deadPaths.forEach { path ->
                        query("SELECT COUNT(*) FROM tracks WHERE path = ?", arrayOf(path)).use { cursor ->
                            cursor.moveToFirst()
                            val count = cursor.getInt(0)
                            if (count > 0) {
                                execSQL("DELETE FROM tracks WHERE path = ?", arrayOf(path))
                                deletedCount++
                                Log.i("SongsDatabase", "Deleted dead track: ${path.split("/").last()}")
                            }
                        }
                    }
                    
                    Log.i("SongsDatabase", "Removed $deletedCount dead track entries")
                    Log.i("SongsDatabase", "Migration 45->46 complete")
                }
            }
        }
        
        // MIGRATION 46 -> 47: Remove all non-MP3 files from database
        // Application only scans MP3 files (audio/mpeg) since MIGRATION_38_39
        // This migration removes remaining .ogg, .wav, and other non-MP3 files
        private val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Removing non-MP3 files...")
                    
                    // Count files to be deleted
                    query("""
                        SELECT COUNT(*) 
                        FROM tracks 
                        WHERE LOWER(path) NOT LIKE '%.mp3'
                    """.trimIndent()).use { cursor ->
                        cursor.moveToFirst()
                        val count = cursor.getInt(0)
                        Log.i("SongsDatabase", "Found $count non-MP3 files to delete")
                        
                        if (count > 0) {
                            // Delete all tracks that are not MP3 files
                            execSQL("""
                                DELETE FROM tracks 
                                WHERE LOWER(path) NOT LIKE '%.mp3'
                            """.trimIndent())
                            Log.i("SongsDatabase", "Deleted $count non-MP3 files (.ogg, .wav, etc.)")
                        }
                    }
                    
                    Log.i("SongsDatabase", "Migration 46->47 complete")
                }
            }
        }
        
        // MIGRATION 47 -> 48: Remove non-MP3 files again (after fixing manual scan filter)
        // MIGRATION_46_47 removed .ogg/.wav files, but scanFilesManually() added them back
        // because it used isAudioFast() without MP3 filter. This has been fixed now.
        // This migration cleans up any .ogg/.wav files that were re-added by manual scan.
        private val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Removing non-MP3 files (cleanup after manual scan)...")
                    
                    // Count files to be deleted
                    query("""
                        SELECT COUNT(*) 
                        FROM tracks 
                        WHERE LOWER(path) NOT LIKE '%.mp3'
                    """.trimIndent()).use { cursor ->
                        cursor.moveToFirst()
                        val count = cursor.getInt(0)
                        Log.i("SongsDatabase", "Found $count non-MP3 files to delete")
                        
                        if (count > 0) {
                            // Delete all tracks that are not MP3 files
                            execSQL("""
                                DELETE FROM tracks 
                                WHERE LOWER(path) NOT LIKE '%.mp3'
                            """.trimIndent())
                            Log.i("SongsDatabase", "Deleted $count non-MP3 files")
                        }
                    }
                    
                    Log.i("SongsDatabase", "Migration 47->48 complete")
                }
            }
        }
        
        // MIGRATION 48 -> 49: Create junction table for future many-to-many playlist support
        // Creates playlist_tracks table to allow tracks on multiple playlists (GUID-based)
        // Legacy columns (playlist_id, order_in_playlist) remain in tracks table for now
        // Future migration will remove them and fully migrate to junction table
        private val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    Log.i("SongsDatabase", "Creating playlist_tracks junction table...")
                    
                    // 1. Create junction table for many-to-many relationship
                    execSQL("""
                        CREATE TABLE IF NOT EXISTS playlist_tracks (
                            playlist_id INTEGER NOT NULL,
                            track_guid TEXT NOT NULL,
                            position INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY (playlist_id, track_guid),
                            FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                            FOREIGN KEY (track_guid) REFERENCES tracks(guid) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    
                    // 2. Create indexes for efficient queries
                    execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlist_id ON playlist_tracks(playlist_id)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_track_guid ON playlist_tracks(track_guid)")
                    
                    // 3. Migrate existing data from tracks table (ONLY tracks with GUID)
                    query("SELECT COUNT(*) FROM tracks WHERE playlist_id != 0 AND guid IS NOT NULL").use { cursor ->
                        cursor.moveToFirst()
                        val countToMigrate = cursor.getInt(0)
                        Log.i("SongsDatabase", "Migrating $countToMigrate tracks with GUID to junction table")
                    }
                    
                    query("SELECT COUNT(*) FROM tracks WHERE playlist_id != 0 AND guid IS NULL").use { cursor ->
                        cursor.moveToFirst()
                        val countWithoutGuid = cursor.getInt(0)
                        if (countWithoutGuid > 0) {
                            Log.w("SongsDatabase", "Note: $countWithoutGuid tracks in playlists have no GUID (will remain in legacy columns)")
                        }
                    }
                    
                    // Copy data: playlist_id -> playlist_id, guid -> track_guid, order_in_playlist -> position
                    execSQL("""
                        INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_guid, position)
                        SELECT playlist_id, guid, order_in_playlist
                        FROM tracks
                        WHERE playlist_id != 0 AND guid IS NOT NULL
                    """.trimIndent())
                    
                    // 4. Verify migration
                    query("SELECT COUNT(*) FROM playlist_tracks").use { cursor ->
                        cursor.moveToFirst()
                        val totalEntries = cursor.getInt(0)
                        Log.i("SongsDatabase", "Junction table created with $totalEntries entries")
                    }
                    
                    Log.i("SongsDatabase", "Migration 48->49 complete. Legacy columns remain in tracks table.")
                    Log.i("SongsDatabase", "Future migration will remove playlist_id and order_in_playlist from tracks.")
                }
            }
        }
        
        // MIGRATION 49 -> 50: Register PlaylistTrack entity with Room
        // No schema changes - table already exists from MIGRATION_48_49
        // This migration exists only to update Room's schema hash after adding PlaylistTrack entity
        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No-op migration - PlaylistTrack entity added to Room @Database annotation
                // The playlist_tracks table was already created and populated in MIGRATION_48_49
                // This just tells Room about the entity so schema hash matches
                Log.i("SongsDatabase", "Migration 49->50 complete. PlaylistTrack entity registered with Room.")
            }
        }

        // MIGRATION 50 -> 51: Add voice_id column to elevenlabs_api_keys table
        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 50->51: Adding voice_id to elevenlabs_api_keys")
                
                // Add voice_id column with default value
                database.execSQL(
                    "ALTER TABLE elevenlabs_api_keys ADD COLUMN voice_id TEXT NOT NULL DEFAULT 'ErXwobaYiN019PkySvjV'"
                )
                
                Log.i("SongsDatabase", "Migration 50->51 complete. voice_id column added to elevenlabs_api_keys")
            }
        }

        // MIGRATION 51 -> 52: Sync added_at_timestamp_unix with tag_txxx_created_at_unix for existing tracks
        private val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 51->52: Syncing added_at with tag creation timestamps")
                
                // Update added_at_timestamp_unix to match tag_txxx_created_at_unix for all tracks that have a valid tag timestamp
                database.execSQL(
                    """
                    UPDATE tracks 
                    SET added_at_timestamp_unix = tag_txxx_created_at_unix 
                    WHERE tag_txxx_created_at_unix IS NOT NULL 
                      AND tag_txxx_created_at_unix > 0
                    """.trimIndent()
                )
                
                // Get count of updated tracks
                database.query("SELECT COUNT(*) FROM tracks WHERE tag_txxx_created_at_unix IS NOT NULL AND tag_txxx_created_at_unix > 0").use { cursor ->
                    cursor.moveToFirst()
                    val updatedCount = cursor.getInt(0)
                    Log.i("SongsDatabase", "Migration 51->52 complete. Updated $updatedCount tracks to sync added_at with tag creation time")
                }
            }
        }

        // MIGRATION 52 -> 53: Fix track titles to match renamed filenames
        private val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 52->53: Fixing track titles after file rename")
                
                // Get all tracks with old Antoni_GUID pattern in title but new ElevenLabs path
                database.query("""
                    SELECT id, path
                    FROM tracks 
                    WHERE title LIKE 'Antoni_%'
                      AND title NOT LIKE 'Antoni_pre_%'
                      AND path LIKE '%ElevenLabs_%'
                """).use { cursor ->
                    var updatedCount = 0
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val path = cursor.getString(1)
                        
                        // Extract filename from path (without extension)
                        val filename = path.substringAfterLast('/').removeSuffix(".mp3")
                        
                        // Update title to match filename
                        database.execSQL(
                            "UPDATE tracks SET title = ? WHERE id = ?",
                            arrayOf(filename, id)
                        )
                        updatedCount++
                    }
                    
                    Log.i("SongsDatabase", "Migration 52->53 complete. Fixed $updatedCount track titles to match renamed files")
                }
            }
        }

        // MIGRATION 53 -> 54: Delete all non-MP3 files from database
        private val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 53->54: Deleting non-MP3 tracks")
                
                // Count tracks to delete
                database.query("SELECT COUNT(*) FROM tracks WHERE path NOT LIKE '%.mp3'").use { cursor ->
                    cursor.moveToFirst()
                    val countToDelete = cursor.getInt(0)
                    Log.i("SongsDatabase", "Found $countToDelete non-MP3 tracks to delete")
                }
                
                // Delete all tracks that don't end with .mp3
                database.execSQL("DELETE FROM tracks WHERE path NOT LIKE '%.mp3'")
                
                // Count remaining tracks
                database.query("SELECT COUNT(*) FROM tracks").use { cursor ->
                    cursor.moveToFirst()
                    val remainingCount = cursor.getInt(0)
                    Log.i("SongsDatabase", "Migration 53->54 complete. Remaining tracks: $remainingCount")
                }
            }
        }

        // MIGRATION 54 -> 55: Delete specific missing files from database
        private val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 54->55: Deleting specific missing tracks")
                
                // List of paths for files that don't exist on disk
                val pathsToDelete = listOf(
                    "/storage/emulated/0/Music/org.fossify.musicplayer.debug/Music/ElevenLabs_2026-02-12T11_33_11_Antoni_pre_s50_sb75_se0_b_m2.mp3",
                    "/storage/emulated/0/Music/org.fossify.musicplayer.debug/Music/ElevenLabs_2026-02-12T11_33_30_Antoni_pre_s50_sb75_se0_b_m2.mp3",
                    "/storage/emulated/0/Music/org.fossify.musicplayer.debug/Music/ElevenLabs_2026-02-12T11_33_54_Antoni_pre_s50_sb75_se0_b_m2.mp3"
                )
                
                var deletedCount = 0
                pathsToDelete.forEach { path ->
                    val rowsDeleted = database.delete("tracks", "path = ?", arrayOf(path))
                    if (rowsDeleted > 0) {
                        Log.i("SongsDatabase", "Deleted missing track: $path")
                        deletedCount += rowsDeleted
                    }
                }
                
                Log.i("SongsDatabase", "Migration 54->55 complete. Deleted $deletedCount missing tracks")
            }
        }

        // MIGRATION 55 -> 56: Remove legacy playlist_id and order_in_playlist columns from tracks table
        // Junction table playlist_tracks is now fully handling playlist relationships
        private val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 55->56: Removing legacy playlist columns from tracks")
                
                database.apply {
                    // SQLite doesn't support DROP COLUMN, so we need to recreate the table
                    // 1. Create new table without playlist_id and order_in_playlist columns
                    execSQL("""
                        CREATE TABLE tracks_new (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            media_store_id INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            artist TEXT NOT NULL,
                            path TEXT NOT NULL,
                            duration INTEGER NOT NULL,
                            album TEXT NOT NULL,
                            genre TEXT NOT NULL DEFAULT '',
                            cover_art TEXT NOT NULL DEFAULT '',
                            track_id INTEGER,
                            disc_number INTEGER,
                            folder_name TEXT NOT NULL DEFAULT '',
                            album_id INTEGER NOT NULL DEFAULT 0,
                            artist_id INTEGER NOT NULL DEFAULT 0,
                            genre_id INTEGER NOT NULL DEFAULT 0,
                            year INTEGER NOT NULL DEFAULT 0,
                            added_at_timestamp_unix INTEGER NOT NULL DEFAULT 0,
                            flags INTEGER NOT NULL DEFAULT 0,
                            transcription TEXT,
                            transcription_normalized TEXT,
                            guid TEXT,
                            tag_txxx_created_at_unix INTEGER,
                            checksum_audio TEXT
                        )
                    """.trimIndent())
                    
                    // 2. Copy data from old table (excluding playlist_id and order_in_playlist)
                    execSQL("""
                        INSERT INTO tracks_new (
                            id, media_store_id, title, artist, path, duration, album, genre, cover_art,
                            track_id, disc_number, folder_name, album_id, artist_id, genre_id, year,
                            added_at_timestamp_unix, flags, transcription, transcription_normalized,
                            guid, tag_txxx_created_at_unix, checksum_audio
                        )
                        SELECT 
                            id, media_store_id, title, artist, path, duration, album, genre, cover_art,
                            track_id, disc_number, folder_name, album_id, artist_id, genre_id, year,
                            added_at_timestamp_unix, flags, transcription, transcription_normalized,
                            guid, tag_txxx_created_at_unix, checksum_audio
                        FROM tracks
                    """.trimIndent())
                    
                    // 3. Drop old table
                    execSQL("DROP TABLE tracks")
                    
                    // 4. Rename new table
                    execSQL("ALTER TABLE tracks_new RENAME TO tracks")
                    
                    // 5. Recreate indexes
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_media_store_id ON tracks(media_store_id)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_guid ON tracks(guid)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_checksum_audio ON tracks(checksum_audio)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_tracks_transcription ON tracks(transcription)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_tracks_transcription_normalized ON tracks(transcription_normalized)")
                    
                    Log.i("SongsDatabase", "Migration 55->56 complete. Legacy playlist columns removed from tracks table")
                }
            }
        }
        
        // MIGRATION 56 -> 57: Delete tracks whose filename doesn't start with 'ElevenLabs_'
        private val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 56->57: Deleting tracks not starting with 'ElevenLabs_'")
                
                // Delete tracks where the filename (basename of path) doesn't start with 'ElevenLabs_'
                // Use LIKE pattern matching: path should contain '/ElevenLabs_%' to match /path/to/ElevenLabs_*.mp3
                val deletedCount = database.delete(
                    "tracks",
                    "path NOT LIKE '%/ElevenLabs_%'",
                    emptyArray()
                )
                
                Log.i("SongsDatabase", "Migration 56->57 complete. Deleted $deletedCount tracks not starting with 'ElevenLabs_'")
            }
        }
        
        // MIGRATION 57 -> 58: Add CHECK constraint for guid format validation
        private val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i("SongsDatabase", "Starting migration 57->58: Adding CHECK constraint for guid format")
                
                database.apply {
                    // SQLite doesn't support adding CHECK constraints to existing columns
                    // We need to recreate the table with the CHECK constraint
                    
                    // 1. Create new table with CHECK constraint for guid
                    // CHECK: guid must be NULL, empty string, or valid UUID format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
                    execSQL("""
                        CREATE TABLE tracks_new (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            media_store_id INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            artist TEXT NOT NULL,
                            path TEXT NOT NULL,
                            duration INTEGER NOT NULL,
                            album TEXT NOT NULL,
                            genre TEXT NOT NULL DEFAULT '',
                            cover_art TEXT NOT NULL DEFAULT '',
                            track_id INTEGER,
                            disc_number INTEGER,
                            folder_name TEXT NOT NULL DEFAULT '',
                            album_id INTEGER NOT NULL DEFAULT 0,
                            artist_id INTEGER NOT NULL DEFAULT 0,
                            genre_id INTEGER NOT NULL DEFAULT 0,
                            year INTEGER NOT NULL DEFAULT 0,
                            added_at_timestamp_unix INTEGER NOT NULL DEFAULT 0,
                            flags INTEGER NOT NULL DEFAULT 0,
                            transcription TEXT,
                            transcription_normalized TEXT,
                            guid TEXT CHECK (
                                guid IS NULL OR 
                                guid = '' OR 
                                (LENGTH(guid) = 36 AND guid GLOB '????????-????-????-????-????????????')
                            ),
                            tag_txxx_created_at_unix INTEGER,
                            checksum_audio TEXT
                        )
                    """.trimIndent())
                    
                    // 2. Copy data from old table (only rows with valid guid format)
                    execSQL("""
                        INSERT INTO tracks_new (
                            id, media_store_id, title, artist, path, duration, album, genre, cover_art,
                            track_id, disc_number, folder_name, album_id, artist_id, genre_id, year,
                            added_at_timestamp_unix, flags, transcription, transcription_normalized,
                            guid, tag_txxx_created_at_unix, checksum_audio
                        )
                        SELECT 
                            id, media_store_id, title, artist, path, duration, album, genre, cover_art,
                            track_id, disc_number, folder_name, album_id, artist_id, genre_id, year,
                            added_at_timestamp_unix, flags, transcription, transcription_normalized,
                            guid, tag_txxx_created_at_unix, checksum_audio
                        FROM tracks
                        WHERE guid IS NULL 
                           OR guid = '' 
                           OR (LENGTH(guid) = 36 AND guid GLOB '????????-????-????-????-????????????')
                    """.trimIndent())
                    
                    // Count and log tracks with invalid guid format (will be excluded)
                    val cursor = query("""
                        SELECT COUNT(*) FROM tracks 
                        WHERE guid IS NOT NULL 
                          AND guid != '' 
                          AND NOT (LENGTH(guid) = 36 AND guid GLOB '????????-????-????-????-????????????')
                    """.trimIndent())
                    val invalidCount = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                    cursor.close()
                    
                    if (invalidCount > 0) {
                        Log.w("SongsDatabase", "Migration 57->58: Excluded $invalidCount tracks with invalid guid format")
                    }
                    
                    // 3. Drop old table
                    execSQL("DROP TABLE tracks")
                    
                    // 4. Rename new table
                    execSQL("ALTER TABLE tracks_new RENAME TO tracks")
                    
                    // 5. Recreate indexes
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_media_store_id ON tracks(media_store_id)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_guid ON tracks(guid)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_checksum_audio ON tracks(checksum_audio)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_tracks_transcription ON tracks(transcription)")
                    execSQL("CREATE INDEX IF NOT EXISTS index_tracks_transcription_normalized ON tracks(transcription_normalized)")
                    
                    Log.i("SongsDatabase", "Migration 57->58 complete. CHECK constraint added for guid format validation")
                }
            }
        }
        
        /**
         * Callback that creates automatic database backups before migrations
         * Backups are saved to: /sdcard/MusicPlayer/Backups/before-migration/
         */
        private class DatabaseBackupCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                
                // Get current database version
                val currentVersion = db.version
                
                // Check if database file exists and has content
                val dbFile = context.getDatabasePath("songs.db")
                if (!dbFile.exists() || dbFile.length() == 0L) {
                    Log.d("SongsDatabase", "Database file doesn't exist or is empty - skipping backup")
                    return
                }
                
                // Create backup only if database has data
                try {
                    val backupFile = createDatabaseBackup(context, currentVersion)
                    Log.i("SongsDatabase", "Database backup created: ${backupFile?.name}")
                } catch (e: Exception) {
                    Log.e("SongsDatabase", "Failed to create database backup", e)
                }
                
                // Clean old backups (keep only last 10)
                cleanOldBackups(context, maxBackups = 10)
            }
        }
        
        /**
         * Get backup directory path for migration backups
         * Location: /sdcard/MusicPlayer/Backups/before-migration/
         */
        private fun getBackupDirectory(): File {
            val backupDir = File(
                Environment.getExternalStorageDirectory(),
                "MusicPlayer/Backups/before-migration"
            )
            if (!backupDir.exists()) {
                val created = backupDir.mkdirs()
                Log.d("SongsDatabase", "Backup directory created: $created at ${backupDir.absolutePath}")
            }
            return backupDir
        }
        
        /**
         * Create database backup
         * Format: songs_v{version}_{timestamp}.db
         */
        private fun createDatabaseBackup(context: Context, currentVersion: Int): File? {
            try {
                val dbFile = context.getDatabasePath("songs.db")
                if (!dbFile.exists() || dbFile.length() == 0L) {
                    Log.w("SongsDatabase", "Database file doesn't exist or is empty")
                    return null
                }
                
                val backupDir = getBackupDirectory()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val backupFileName = "songs_v${currentVersion}_${timestamp}.db"
                val backupFile = File(backupDir, backupFileName)
                
                // Copy database file
                dbFile.copyTo(backupFile, overwrite = false)
                
                Log.i("SongsDatabase", "Backup created: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
                return backupFile
                
            } catch (e: Exception) {
                Log.e("SongsDatabase", "Failed to create backup", e)
                return null
            }
        }
        
        /**
         * Clean old backups, keeping only the most recent ones
         * @param maxBackups Maximum number of backups to keep (default: 10)
         */
        private fun cleanOldBackups(context: Context, maxBackups: Int = 10) {
            try {
                val backupDir = getBackupDirectory()
                val backupFiles = backupDir.listFiles { file ->
                    file.isFile && file.name.startsWith("songs_v") && file.name.endsWith(".db")
                }
                
                if (backupFiles != null && backupFiles.size > maxBackups) {
                    // Sort by last modified (oldest first)
                    val sortedFiles = backupFiles.sortedBy { it.lastModified() }
                    
                    // Delete oldest files (keep only maxBackups most recent)
                    val filesToDelete = sortedFiles.dropLast(maxBackups)
                    filesToDelete.forEach { file ->
                        val deleted = file.delete()
                        Log.d("SongsDatabase", "Deleted old backup: ${file.name} (success: $deleted)")
                    }
                    
                    Log.i("SongsDatabase", "Cleaned ${filesToDelete.size} old backups, kept ${maxBackups} most recent")
                }
            } catch (e: Exception) {
                Log.e("SongsDatabase", "Failed to clean old backups", e)
            }
        }
    }
}
