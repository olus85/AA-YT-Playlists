package app.olus.ytmusic.autolauncher.data.repository

import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.local.dao.TrackDao
import app.olus.ytmusic.autolauncher.data.local.entity.PlaylistEntity
import app.olus.ytmusic.autolauncher.data.local.entity.TrackEntity
import app.olus.ytmusic.autolauncher.data.local.PlaylistDatabase
import androidx.room.withTransaction
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import app.olus.ytmusic.autolauncher.domain.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val database: PlaylistDatabase
) {
    
    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun getAllPlaylistsOnce(): List<Playlist> {
        return playlistDao.getAllPlaylistsOnce().map { it.toDomain() }
    }
    
    suspend fun getPlaylistById(id: Int): Playlist? {
        return playlistDao.getPlaylistById(id)?.toDomain()
    }
    
    suspend fun addPlaylist(playlist: Playlist): Long {
        val position = playlistDao.getNextOrderIndex()
        val entity = playlist.toEntity().copy(position = position)
        return playlistDao.insertPlaylist(entity)
    }
    
    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(playlist.toEntity())
    }

    suspend fun updatePlaylists(playlists: List<Playlist>) {
        playlistDao.updatePlaylists(playlists.map { it.toEntity() })
    }
    
    suspend fun deletePlaylist(playlist: Playlist) {
        // Also delete cached tracks for this playlist
        trackDao.deleteTracksForPlaylist(playlist.id)
        playlistDao.deletePlaylist(playlist.toEntity())
    }
    
    suspend fun deletePlaylistById(id: Int) {
        trackDao.deleteTracksForPlaylist(id)
        playlistDao.deletePlaylistById(id)
    }

    // ─── Track Caching (Issue 4) ────────────────────────────────────

    suspend fun getCachedTracks(playlistId: Int): List<Track> {
        return trackDao.getTracksForPlaylist(playlistId).map { entity ->
            Track(
                title = entity.title,
                author = entity.author,
                videoId = entity.videoId
            )
        }
    }

    suspend fun getAllCachedTracks(): List<Track> {
        return trackDao.getAllTracks().map { entity ->
            Track(
                title = entity.title,
                author = entity.author,
                videoId = entity.videoId
            )
        }
    }

    suspend fun getAllTrackEntities(): List<TrackEntity> {
        return trackDao.getAllTracks()
    }

    suspend fun saveTracks(playlistId: Int, tracks: List<Track>) {
        val entities = tracks.mapIndexed { index, track ->
            TrackEntity(
                playlistId = playlistId,
                title = track.title,
                author = track.author,
                videoId = track.videoId,
                position = index
            )
        }
        database.withTransaction {
            trackDao.deleteTracksForPlaylist(playlistId)
            trackDao.insertTracks(entities)
        }
    }

    suspend fun deleteTracksForPlaylist(playlistId: Int) {
        trackDao.deleteTracksForPlaylist(playlistId)
    }

    // ─── Mapping ────────────────────────────────────────────────────
    
    private fun PlaylistEntity.toDomain(): Playlist {
        return Playlist(
            id = id,
            url = url,
            title = title,
            imageUrl = imageUrl,
            position = position,
            trackCount = trackCount,
            duration = duration,
            source = source,
            externalId = externalId
        )
    }
    
    private fun Playlist.toEntity(): PlaylistEntity {
        return PlaylistEntity(
            id = id,
            url = url,
            title = title,
            imageUrl = imageUrl,
            position = position,
            trackCount = trackCount,
            duration = duration,
            source = source,
            externalId = externalId
        )
    }
}
