package app.olus.ytmusic.autolauncher.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.local.dao.TrackDao
import app.olus.ytmusic.autolauncher.data.local.dao.LyricsDao
import app.olus.ytmusic.autolauncher.data.local.entity.LyricsEntity
import app.olus.ytmusic.autolauncher.data.local.entity.PlaylistEntity
import app.olus.ytmusic.autolauncher.data.local.entity.TrackEntity

@Database(
    entities = [PlaylistEntity::class, TrackEntity::class, LyricsEntity::class],
    version = 6,
    exportSchema = false
)
abstract class PlaylistDatabase : RoomDatabase() {
    
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun lyricsDao(): LyricsDao
    
    companion object {
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null

        /**
         * Migration 3 → 4:
         * - Adds 'tracks' table for track caching (Issue 4)
         * - Adds 'source' and 'externalId' columns to 'playlists' for Jellyfin support (Issue 5)
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Issue 4: Create tracks table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Issue 5: Add Jellyfin columns to playlists
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'YOUTUBE'")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `externalId` TEXT")
            }
        }
        
        /**
         * Migration 4 → 5:
         * - Adds 'lyrics_cache' table for fast lyrics retrieval
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `trackName` TEXT NOT NULL,
                        `artistName` TEXT NOT NULL,
                        `lyricsContent` TEXT NOT NULL,
                        `isSynced` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        fun getDatabase(context: Context): PlaylistDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlist_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
