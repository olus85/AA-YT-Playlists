package app.olus.ytmusic.autolauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.olus.ytmusic.autolauncher.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    
    @Query("SELECT * FROM playlists ORDER BY position ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists ORDER BY position ASC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Int): PlaylistEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long
    
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylists(playlists: List<PlaylistEntity>)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Int)
    
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlists")
    suspend fun getNextOrderIndex(): Int
}
