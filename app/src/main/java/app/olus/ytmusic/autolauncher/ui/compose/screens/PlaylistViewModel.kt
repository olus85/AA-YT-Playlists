package app.olus.ytmusic.autolauncher.ui.compose.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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
    private val metadataFetcher: MetadataFetcher
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Refresh metadata for playlists missing track count on first load only
    init {
        viewModelScope.launch {
            // Wait for first emission, then refresh incomplete entries (once)
            val list = playlists.first { it.isNotEmpty() || true }
            list.filter { it.trackCount == null }.forEach { playlist ->
                refreshPlaylistMetadata(playlist)
            }
        }
    }

    private val _addPlaylistState = MutableStateFlow(AddPlaylistState())
    val addPlaylistState: StateFlow<AddPlaylistState> = _addPlaylistState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun refreshAll() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val currentList = playlists.value
            currentList.map { playlist ->
                async {
                    val result = metadataFetcher.fetchMetadata(playlist.url)
                    result.onSuccess { metadata ->
                        repository.updatePlaylist(playlist.copy(
                            trackCount = metadata.trackCount ?: playlist.trackCount,
                            duration = metadata.duration ?: playlist.duration,
                            imageUrl = if (metadata.imageUrl.isNotEmpty()) metadata.imageUrl else playlist.imageUrl
                        ))
                    }
                }
            }.awaitAll()
            _isRefreshing.value = false
        }
    }

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
                duration = null
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

    fun resetAddPlaylistState() {
        _addPlaylistState.value = AddPlaylistState()
    }
}
