package app.olus.ytmusic.autolauncher.data.repository

import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.local.entity.PlaylistEntity
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    
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
        playlistDao.deletePlaylist(playlist.toEntity())
    }
    
    suspend fun deletePlaylistById(id: Int) {
        playlistDao.deletePlaylistById(id)
    }
    
    private fun PlaylistEntity.toDomain(): Playlist {
        return Playlist(
            id = id,
            url = url,
            title = title,
            imageUrl = imageUrl,
            position = position,
            trackCount = trackCount,
            duration = duration
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
            duration = duration
        )
    }
}
