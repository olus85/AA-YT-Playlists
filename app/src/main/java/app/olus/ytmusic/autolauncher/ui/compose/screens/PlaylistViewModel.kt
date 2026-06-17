package app.olus.ytmusic.autolauncher.ui.compose.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.olus.ytmusic.autolauncher.data.repository.BackupManager
import app.olus.ytmusic.autolauncher.data.repository.JellyfinItem
import app.olus.ytmusic.autolauncher.data.repository.JellyfinRepository
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import app.olus.ytmusic.autolauncher.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import app.olus.ytmusic.autolauncher.service.MediaSyncManager
import app.olus.ytmusic.autolauncher.data.repository.LyricsFetcher
import app.olus.ytmusic.autolauncher.data.repository.LyricsState
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.olus.ytmusic.autolauncher.data.local.entity.TrackEntity
import app.olus.ytmusic.autolauncher.util.AALogger

data class PlaylistTrackSearchResult(
    val title: String,
    val author: String,
    val videoId: String,
    val playlistId: Int,
    val playlistTitle: String,
    val playlistSource: String
)

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
            try {
                kotlinx.coroutines.withTimeout(3000L) {
                    val playlistList = playlists.first { it.isNotEmpty() }
                    playlistList.filter { it.trackCount == null && it.source == "YOUTUBE" }.forEach { playlist ->
                        refreshPlaylistMetadata(playlist)
                    }
                }
            } catch (e: Exception) {
                // Timeout or empty database
            }
        }

        viewModelScope.launch {
            currentMetadata.collectLatest { metadata ->
                if (metadata != null) {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

                    if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                        _lyricsState.value = LyricsState.Loading
                        val fetched = lyricsFetcher.fetchLyrics(title, artist, duration)
                        _lyricsState.value = fetched
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

    // ─── Voice Search State ───────────────────────────────────────────
    private val _showSearchDialog = MutableStateFlow(false)
    val showSearchDialog: StateFlow<Boolean> = _showSearchDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchingLocal = MutableStateFlow(false)
    val isSearchingLocal: StateFlow<Boolean> = _isSearchingLocal.asStateFlow()

    private val _localSearchResults = MutableStateFlow<List<PlaylistTrackSearchResult>>(emptyList())
    val localSearchResults: StateFlow<List<PlaylistTrackSearchResult>> = _localSearchResults.asStateFlow()

    private val _isSearchingYT = MutableStateFlow(false)
    val isSearchingYT: StateFlow<Boolean> = _isSearchingYT.asStateFlow()

    private val _ytSearchResults = MutableStateFlow<List<Track>>(emptyList())
    val ytSearchResults: StateFlow<List<Track>> = _ytSearchResults.asStateFlow()

    fun openSearchDialog(initialQuery: String) {
        _searchQuery.value = initialQuery
        _ytSearchResults.value = emptyList()
        _showSearchDialog.value = true
        performLocalSearch(initialQuery)
    }

    fun closeSearchDialog() {
        _showSearchDialog.value = false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        performLocalSearch(query)
    }

    fun performLocalSearch(query: String) {
        if (query.isBlank()) {
            _localSearchResults.value = emptyList()
            return
        }
        _isSearchingLocal.value = true
        viewModelScope.launch {
            try {
                val allTrackEntities = repository.getAllTrackEntities()
                val playlistsList = repository.getAllPlaylistsOnce()
                val playlistMap = playlistsList.associateBy { it.id }

                val queryTokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

                val results = if (queryTokens.isNotEmpty()) {
                    allTrackEntities.filter { track ->
                        val titleLower = track.title.lowercase()
                        val authorLower = track.author.lowercase()
                        queryTokens.all { token ->
                            titleLower.contains(token) || authorLower.contains(token)
                        }
                    }.mapNotNull { track ->
                        val playlist = playlistMap[track.playlistId] ?: return@mapNotNull null
                        PlaylistTrackSearchResult(
                            title = track.title,
                            author = track.author,
                            videoId = track.videoId,
                            playlistId = track.playlistId,
                            playlistTitle = playlist.title,
                            playlistSource = playlist.source
                        )
                    }
                } else {
                    emptyList()
                }
                _localSearchResults.value = results
            } catch (e: Exception) {
                AALogger.logError("PlaylistViewModel", "Local search failed", e)
            } finally {
                _isSearchingLocal.value = false
            }
        }
    }

    fun performYTSearch() {
        val query = _searchQuery.value
        if (query.isBlank()) return
        
        _isSearchingYT.value = true
        viewModelScope.launch {
            try {
                val results = metadataFetcher.searchTracks(query)
                _ytSearchResults.value = results
            } catch (e: Exception) {
                AALogger.logError("PlaylistViewModel", "YouTube search failed", e)
            } finally {
                _isSearchingYT.value = false
            }
        }
    }

    fun playTrack(context: Context, track: PlaylistTrackSearchResult) {
        if (track.playlistSource == "JELLYFIN") {
            mediaSyncManager.onPlayJellyfinTrack?.invoke(track.playlistId, track.videoId)
        } else {
            playYouTubeTrack(context, track.videoId, track.playlistId)
        }
    }

    fun playYTTrack(context: Context, track: Track) {
        playYouTubeTrack(context, track.videoId, null)
    }

    private fun playYouTubeTrack(context: Context, videoId: String, playlistId: Int?) {
        viewModelScope.launch {
            val listId = playlistId?.let { repository.getPlaylistById(it) }
                ?.url?.let { Uri.parse(it).getQueryParameter("list") }
            val url = if (listId != null) {
                "https://music.youtube.com/watch?v=$videoId&list=$listId"
            } else {
                "https://music.youtube.com/watch?v=$videoId"
            }
            
            AALogger.forceLog("PlaylistViewModel", "playYouTubeTrack: activeController = ${mediaSyncManager.activeController.value?.packageName}")
            val controller = mediaSyncManager.activeController.value
            val ytMusicPackages = listOf(
                "app.rvx.android.apps.youtube.music",
                "app.revanced.android.apps.youtube.music",
                "com.google.android.apps.youtube.music"
            )
            if (controller != null && ytMusicPackages.contains(controller.packageName)) {
                try {
                    AALogger.forceLog("PlaylistViewModel", "Trying transportControls.playFromUri for $url")
                    controller.transportControls.playFromUri(Uri.parse(url), null)
                    return@launch
                } catch (e: Exception) {
                    AALogger.logError("PlaylistViewModel", "playFromUri failed", e)
                }
            }
            
            // Fallback: Launch Intent
            AALogger.forceLog("PlaylistViewModel", "Falling back to Intent launch for $url")
            var started = false
            for (pkg in ytMusicPackages) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        `package` = pkg
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                    started = true
                    break
                } catch (e: Exception) {
                    // Ignore
                }
            }
            if (!started) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    AALogger.logError("PlaylistViewModel", "All launch attempts failed", e)
                }
            }
        }
    }
}
