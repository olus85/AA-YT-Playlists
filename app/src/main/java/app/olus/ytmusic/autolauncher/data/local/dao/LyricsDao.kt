package app.olus.ytmusic.autolauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.olus.ytmusic.autolauncher.data.local.entity.LyricsEntity

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE id = :id LIMIT 1")
    suspend fun getLyrics(id: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clearCache()
}
