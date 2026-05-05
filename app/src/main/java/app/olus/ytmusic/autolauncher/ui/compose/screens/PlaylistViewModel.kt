package app.olus.ytmusic.autolauncher.ui.compose.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.olus.ytmusic.autolauncher.data.repository.BackupManager
import app.olus.ytmusic.autolauncher.data.repository.JellyfinItem
import app.olus.ytmusic.autolauncher.data.repository.JellyfinRepository
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import app.olus.ytmusic.autolauncher.service.MediaSyncManager
import app.olus.ytmusic.autolauncher.data.repository.LyricsFetcher
import app.olus.ytmusic.autolauncher.data.repository.LyricsState
import android.media.MediaMetadata
import android.media.session.PlaybackState

data class AddPlaylistState(
    val url: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val trackCount: String? = null,
    val duration: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val metadataFetcher: MetadataFetcher,
    val mediaSyncManager: MediaSyncManager,
    private val lyricsFetcher: LyricsFetcher,
    val jellyfinRepository: JellyfinRepository,
    val backupManager: BackupManager
) : ViewModel() {

    val currentMetadata: StateFlow<MediaMetadata?> = mediaSyncManager.currentMetadata
    val currentPlaybackState: StateFlow<PlaybackState?> = mediaSyncManager.currentPlaybackState

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Empty)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Refresh metadata for playlists missing track count on first load only
    init {
        viewModelScope.launch {
            val playlistList = playlists.value
            if (playlistList.isNotEmpty()) {
                playlistList.filter { it.trackCount == null && it.source == "YOUTUBE" }.forEach { playlist ->
                    refreshPlaylistMetadata(playlist)
                }
            }
        }

        viewModelScope.launch {
            currentMetadata.collect { metadata ->
                if (metadata != null) {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    
                    if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                        _lyricsState.value = LyricsState.Loading
                        _lyricsState.value = lyricsFetcher.fetchLyrics(title, artist, duration)
                    } else {
                        _lyricsState.value = LyricsState.Empty
                    }
                } else {
                    _lyricsState.value = LyricsState.Empty
                }
            }
        }
    }

    private val _addPlaylistState = MutableStateFlow(AddPlaylistState())
    val addPlaylistState: StateFlow<AddPlaylistState> = _addPlaylistState

    fun updateUrl(url: String) {
        _addPlaylistState.value = _addPlaylistState.value.copy(url = url)
    }

    fun handleSharedUrl(url: String) {
        addPlaylistAndFetch(url)
    }

    fun addPlaylistAndFetch(url: String) {
        if (url.isBlank()) return

        viewModelScope.launch {
            val skeleton = Playlist(
                url = url,
                title = "Lade Metadaten...",
                imageUrl = "",
                trackCount = null,
                duration = null,
                source = "YOUTUBE"
            )
            val insertedId = repository.addPlaylist(skeleton).toInt()
            resetAddPlaylistState()

            val result = metadataFetcher.fetchMetadata(url)
            result.fold(
                onSuccess = { metadata ->
                    val updated = skeleton.copy(
                        id = insertedId,
                        title = metadata.title.ifEmpty { "Unbekannte Playlist" },
                        imageUrl = metadata.imageUrl,
                        trackCount = metadata.trackCount,
                        duration = metadata.duration
                    )
                    repository.updatePlaylist(updated)
                },
                onFailure = { error ->
                    val updated = skeleton.copy(
                        id = insertedId,
                        title = "Fehler beim Laden"
                    )
                    repository.updatePlaylist(updated)
                }
            )
        }
    }

    /**
     * Imports a Jellyfin album/playlist as a new local playlist.
     */
    fun importJellyfinItem(item: JellyfinItem) {
        viewModelScope.launch {
            val imageUrl = jellyfinRepository.getImageUrl(item.id) ?: ""
            val playlist = Playlist(
                url = "${jellyfinRepository.serverUrl}/web/index.html#!/details?id=${item.id}",
                title = item.name,
                imageUrl = imageUrl,
                trackCount = null,
                duration = if (item.artist.isNotEmpty()) item.artist else null,
                source = "JELLYFIN",
                externalId = item.id
            )
            repository.addPlaylist(playlist)
        }
    }

    fun updatePlaylistDetails(playlist: Playlist, newTitle: String, newImageUrl: String) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(title = newTitle, imageUrl = newImageUrl))
        }
    }

    fun savePlaylistOrder(orderedList: List<Playlist>) {
        viewModelScope.launch {
            val updatedList = orderedList.mapIndexed { index, playlist ->
                playlist.copy(position = index)
            }
            repository.updatePlaylists(updatedList)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun refreshPlaylistMetadata(playlist: Playlist) {
        if (playlist.source != "YOUTUBE") return
        viewModelScope.launch {
            val result = metadataFetcher.fetchMetadata(playlist.url)
            result.fold(
                onSuccess = { metadata ->
                    repository.updatePlaylist(playlist.copy(
                        trackCount = metadata.trackCount ?: playlist.trackCount,
                        duration = metadata.duration ?: playlist.duration,
                        imageUrl = if (metadata.imageUrl.isNotEmpty()) metadata.imageUrl else playlist.imageUrl
                    ))
                },
                onFailure = { /* Keep existing data */ }
            )
        }
    }

    fun forceRefreshPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            if (playlist.source == "YOUTUBE") {
                metadataFetcher.clearTrackCache(playlist.url)
                repository.deleteTracksForPlaylist(playlist.id)

                val tracksResult = metadataFetcher.fetchTracks(playlist.url)
                tracksResult.getOrNull()?.let { tracks ->
                    repository.saveTracks(playlist.id, tracks)
                }

                val result = metadataFetcher.fetchMetadata(playlist.url)
                result.fold(
                    onSuccess = { metadata ->
                        repository.updatePlaylist(playlist.copy(
                            trackCount = metadata.trackCount ?: playlist.trackCount,
                            duration = metadata.duration ?: playlist.duration
                        ))
                    },
                    onFailure = { /* keep existing data */ }
                )
            }
        }
    }

    fun resetAddPlaylistState() {
        _addPlaylistState.value = AddPlaylistState()
    }
}
