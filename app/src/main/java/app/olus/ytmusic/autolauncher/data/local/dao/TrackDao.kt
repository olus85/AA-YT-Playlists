package app.olus.ytmusic.autolauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.olus.ytmusic.autolauncher.data.local.entity.TrackEntity

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracksForPlaylist(playlistId: Int): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: Int)

    @Query("DELETE FROM tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteTrackByVideoId(playlistId: Int, videoId: String)

    @Transaction
    suspend fun replaceTracksForPlaylist(playlistId: Int, tracks: List<TrackEntity>) {
        deleteTracksForPlaylist(playlistId)
        insertTracks(tracks)
    }
}
