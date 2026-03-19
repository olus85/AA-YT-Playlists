package app.olus.ytmusic.autolauncher.ui.auto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "YTMediaBrowserService"
private const val ROOT_ID = "root_id"

@AndroidEntryPoint
class YTMusicMediaBrowserService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var repository: PlaylistRepository

    private val fetcher = MetadataFetcher()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            // Set an initial playback state
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            setPlaybackState(stateBuilder.build())

            // Handle callbacks
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "onPlay called")
                    // Could resume last played item here if tracking it
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    Log.d(TAG, "onPlayFromMediaId: $mediaId")
                    mediaId?.let { openUrl(it) }
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "onSkipToNext")
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "onSkipToPrevious")
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }

                override fun onPause() {
                    Log.d(TAG, "onPause")
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                }
            })
        }

        // Set the session's token so that client activities can communicate with it
        sessionToken = mediaSession.sessionToken
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        mediaSession.release()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot: client=$clientPackageName")
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren: parentId=$parentId")
        
        // We must detach the result to load asynchronously
        result.detach()

        serviceScope.launch {
            try {
                if (parentId == ROOT_ID) {
                    // Load playlists from database
                    val playlists = repository.getAllPlaylists().first()
                    val items = playlists.map { playlist ->
                        createPlaylistMediaItem(playlist)
                    }.toMutableList()
                    
                    if (items.isEmpty()) {
                        // Return empty list if no playlists
                        result.sendResult(mutableListOf())
                    } else {
                        result.sendResult(items)
                    }
                } else {
                    // It's a playlist URL, fetch its tracks
                    val tracksResult = fetcher.fetchTracks(parentId)
                    tracksResult.fold(
                        onSuccess = { tracks ->
                            val items = tracks.map { track ->
                                val listId = fetcher.extractPlaylistId(parentId) ?: ""
                                val trackUrl = "https://music.youtube.com/watch?v=${track.videoId}&list=$listId"
                                createTrackMediaItem(trackUrl, track.title, track.author)
                            }.toMutableList()
                            result.sendResult(items)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to load tracks for $parentId", e)
                            result.sendResult(mutableListOf())
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onLoadChildren", e)
                result.sendResult(mutableListOf())
            }
        }
    }

    private fun createPlaylistMediaItem(playlist: Playlist): MediaBrowserCompat.MediaItem {
        val subtitle = listOfNotNull(playlist.trackCount, playlist.duration).joinToString(" • ")
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(playlist.url)
            .setTitle(playlist.title.ifEmpty { "Playlist" })
            .setSubtitle(subtitle)
            .setIconUri(Uri.parse(playlist.imageUrl))
            .build()
        // BROWSABLE so we can see tracks, PLAYABLE so we can click "Play" directly on the folder
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE or MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createTrackMediaItem(trackUrl: String, title: String, author: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(trackUrl)
            .setTitle(title)
            .setSubtitle(author)
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun openUrl(url: String) {
        try {
            // Update state to playing temporarily to give UI feedback
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            mediaSession.setPlaybackState(stateBuilder.build())

            val intent = Intent("app.olus.ytmusic.autolauncher.ACTION_OPEN_PLAYLIST").apply {
                `package` = packageName
                putExtra("playlist_url", url)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast for $url", e)
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        try {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send media key event: $keyCode", e)
        }
    }
}
