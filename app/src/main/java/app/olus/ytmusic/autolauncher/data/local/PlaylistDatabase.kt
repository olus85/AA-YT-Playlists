package app.olus.ytmusic.autolauncher.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.local.entity.PlaylistEntity

@Database(
    entities = [PlaylistEntity::class],
    version = 3,
    exportSchema = false
)
abstract class PlaylistDatabase : RoomDatabase() {
    
    abstract fun playlistDao(): PlaylistDao
    
    companion object {
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null
        
        fun getDatabase(context: Context): PlaylistDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlist_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
