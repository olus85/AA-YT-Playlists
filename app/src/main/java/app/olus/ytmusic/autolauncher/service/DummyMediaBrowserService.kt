package app.olus.ytmusic.autolauncher.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.util.AALogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FallbackMediaBrowser"
private const val ROOT_ID = "root_playlists"

/**
 * Fallback MediaBrowserService für echte Headunits, die den CarAppService
 * (Templates) umgehen oder blockieren und nur native Media-Apps erlauben.
 * Lädt Playlisten aus der Datenbank und stellt sie als abspielbare Liste bereit.
 */
@AndroidEntryPoint
class DummyMediaBrowserService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var repository: PlaylistRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        AALogger.forceLog(TAG, "onCreate: Initializing Fallback MediaBrowserService")

        mediaSession = MediaSessionCompat(this, TAG).apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                    .build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    AALogger.forceLog(TAG, "onPlayFromMediaId: $mediaId")
                    if (mediaId?.startsWith("playlist_") == true) {
                        val idStr = mediaId.removePrefix("playlist_")
                        val id = idStr.toIntOrNull()
                        if (id != null) {
                            scope.launch {
                                val playlist = repository.getPlaylistById(id)
                                if (playlist != null) {
                                    AALogger.forceLog(TAG, "Starting playback for fallback playlist: ${playlist.title}")
                                    val intent = Intent("app.olus.ytmusic.autolauncher.ACTION_OPEN_PLAYLIST").apply {
                                        `package` = packageName
                                        putExtra("playlist_url", playlist.url)
                                    }
                                    sendBroadcast(intent)
                                } else {
                                    AALogger.logError(TAG, "Playlist not found in DB for id: $id")
                                }
                            }
                        }
                    }
                }
            })
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
    }

    override fun onDestroy() {
        super.onDestroy()
        AALogger.forceLog(TAG, "onDestroy")
        job.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        AALogger.forceLog(TAG, "onGetRoot called by client: $clientPackageName (uid=$clientUid)")
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        AALogger.forceLog(TAG, "onLoadChildren called for parentId: $parentId")
        
        if (parentId == ROOT_ID) {
            result.detach()
            scope.launch {
                try {
                    val playlists = repository.getAllPlaylistsOnce()
                    AALogger.forceLog(TAG, "Loaded ${playlists.size} playlists for fallback UI")
                    
                    val items = playlists.map { playlist ->
                        val descBuilder = MediaDescriptionCompat.Builder()
                            .setMediaId("playlist_${playlist.id}")
                            .setTitle(playlist.title)
                            .setSubtitle(playlist.trackCount ?: "")
                            
                        if (!playlist.imageUrl.isNullOrEmpty()) {
                            descBuilder.setIconUri(Uri.parse(playlist.imageUrl))
                        }
                        
                        MediaBrowserCompat.MediaItem(
                            descBuilder.build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    }.toMutableList()
                    
                    result.sendResult(items)
                } catch (e: Exception) {
                    AALogger.logError(TAG, "Error loading playlists for fallback", e)
                    result.sendResult(mutableListOf())
                }
            }
        } else {
            result.sendResult(mutableListOf())
        }
    }
}
