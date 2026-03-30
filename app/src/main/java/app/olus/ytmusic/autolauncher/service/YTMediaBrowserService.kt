package app.olus.ytmusic.autolauncher.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import app.olus.ytmusic.autolauncher.R
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.util.AALogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val TAG = "YTMediaBrowserService"
private const val ROOT_ID = "root"
private const val NOTIFICATION_CHANNEL_ID = "yt_auto_proxy_channel"
private const val FOREGROUND_NOTIFICATION_ID = 42

/**
 * How long (ms) to suppress proxy state-sync after launching YT Music,
 * so Android Auto sees STATE_CONNECTING until the new track actually plays.
 * Metadata is NEVER suppressed – it must always flow through.
 */
private const val LAUNCH_SUPPRESS_DURATION_MS = 6000L

/** How long to keep foreground status after launching (ms). */
private const val FOREGROUND_KEEPALIVE_MS = 5000L

/**
 * Stable, hierarchical MediaBrowserServiceCompat for Android Auto.
 *
 * Hierarchy:
 *   root
 *   ├── playlist_1 (BROWSABLE) → expands to track list
 *   │   ├── shuffle_1 (PLAYABLE) "▶ Shuffle abspielen"
 *   │   ├── track_1_videoId1 (PLAYABLE)
 *   │   └── track_1_videoId2 (PLAYABLE)
 *   └── ...
 *
 * On track selection, fires an intent to open YouTube Music
 * and syncs the playback state from the real YT Music session via MediaSyncManager.
 */
@AndroidEntryPoint
class YTMediaBrowserService : MediaBrowserServiceCompat() {

    @Inject lateinit var repository: PlaylistRepository
    @Inject lateinit var metadataFetcher: MetadataFetcher
    @Inject lateinit var mediaSyncManager: MediaSyncManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var mediaSession: MediaSessionCompat
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Timestamp of last launch; state-sync suppressed until elapsed > LAUNCH_SUPPRESS_DURATION_MS */
    private val lastLaunchTimestamp = AtomicLong(0L)

    private var lastLoadedBitmapUri: String? = null
    private var lastLoadedBitmap: android.graphics.Bitmap? = null

    private val ytMusicPackages = listOf(
        "app.rvx.android.apps.youtube.music",
        "app.revanced.android.apps.youtube.music",
        "com.google.android.apps.youtube.music"
    )

    // ─── Lifecycle ──────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        AALogger.forceLog(TAG, "onCreate: Initializing YTMediaBrowserService")

        mediaSession = MediaSessionCompat(this, TAG).apply {
            setPlaybackState(buildIdleState())
            setCallback(mediaSessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        startProxySync()
    }

    override fun onDestroy() {
        super.onDestroy()
        AALogger.forceLog(TAG, "onDestroy")
        job.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }

    private fun buildIdleState(): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(SUPPORTED_ACTIONS)
            .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
            .build()

    // ─── MediaBrowser Hierarchy ─────────────────────────────────────────

    override fun onGetRoot(
        clientPackageName: String, clientUid: Int, rootHints: Bundle?
    ): BrowserRoot {
        AALogger.forceLog(TAG, "onGetRoot called by client: $clientPackageName (uid=$clientUid)")
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        AALogger.forceLog(TAG, "onLoadChildren called for parentId: $parentId")
        when {
            parentId == ROOT_ID -> loadPlaylists(result)
            parentId.startsWith("playlist_") -> loadTracks(parentId, result)
            else -> result.sendResult(mutableListOf())
        }
    }

    private fun loadPlaylists(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        scope.launch {
            try {
                val playlists = repository.getAllPlaylistsOnce()
                AALogger.forceLog(TAG, "Loaded ${playlists.size} playlists")

                val items = playlists.map { playlist ->
                    val desc = MediaDescriptionCompat.Builder()
                        .setMediaId("playlist_${playlist.id}")
                        .setTitle(playlist.title)
                        .setSubtitle(playlist.trackCount ?: "")
                    if (!playlist.imageUrl.isNullOrEmpty()) {
                        desc.setIconUri(Uri.parse(playlist.imageUrl))
                    }
                    // ONLY FLAG_BROWSABLE → clicking opens track list, never triggers playback
                    MediaBrowserCompat.MediaItem(desc.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
                }.toMutableList()

                result.sendResult(items)
            } catch (e: Exception) {
                AALogger.logError(TAG, "Error loading playlists", e)
                result.sendResult(mutableListOf())
            }
        }
    }

    private fun loadTracks(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val id = parentId.removePrefix("playlist_").toIntOrNull()
        if (id == null) { result.sendResult(mutableListOf()); return }

        result.detach()
        scope.launch {
            try {
                val playlist = repository.getPlaylistById(id)
                if (playlist == null) {
                    AALogger.logError(TAG, "Playlist not found for id: $id")
                    result.sendResult(mutableListOf()); return@launch
                }

                AALogger.forceLog(TAG, "Fetching tracks for '${playlist.title}' (timeout: 10s)")
                val fetchResult = withTimeoutOrNull(10_000L) {
                    metadataFetcher.fetchTracks(playlist.url)
                }

                if (fetchResult == null) {
                    AALogger.logError(TAG, "TIMEOUT fetching tracks for '${playlist.title}'")
                    result.sendResult(mutableListOf(
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("error_timeout")
                                .setTitle("Zeitüberschreitung")
                                .setSubtitle("Tippe erneut um es nochmal zu versuchen")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    ))
                    return@launch
                }

                fetchResult.fold(
                    onSuccess = { tracks ->
                        AALogger.forceLog(TAG, "Loaded ${tracks.size} tracks for '${playlist.title}'")
                        val items = mutableListOf<MediaBrowserCompat.MediaItem>()

                        // First item: Shuffle play action
                        items.add(MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("shuffle_${playlist.id}")
                                .setTitle("▶ Shuffle abspielen")
                                .setSubtitle("${tracks.size} Songs zufällig abspielen")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        ))

                        // Track items
                        tracks.forEach { track ->
                            items.add(MediaBrowserCompat.MediaItem(
                                MediaDescriptionCompat.Builder()
                                    .setMediaId("track_${playlist.id}_${track.videoId}")
                                    .setTitle(track.title)
                                    .setSubtitle(track.author)
                                    .setIconUri(Uri.parse("https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg"))
                                    .build(),
                                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                            ))
                        }
                        result.sendResult(items)
                    },
                    onFailure = { e ->
                        AALogger.logError(TAG, "Failed to fetch tracks", e)
                        result.sendResult(mutableListOf())
                    }
                )
            } catch (e: Exception) {
                AALogger.logError(TAG, "Exception loading tracks", e)
                result.sendResult(mutableListOf())
            }
        }
    }

    // ─── MediaSession Callback ──────────────────────────────────────────

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            AALogger.forceLog(TAG, "onPlayFromMediaId: $mediaId")
            if (mediaId == null) return
            when {
                mediaId.startsWith("shuffle_") -> handleShuffleClick(mediaId)
                mediaId.startsWith("track_") -> handleTrackClick(mediaId)
                else -> AALogger.log(TAG, "Unknown mediaId format: $mediaId")
            }
        }

        override fun onPlay() {
            AALogger.log(TAG, "onPlay → forwarding")
            if (mediaSyncManager.hasActiveSession()) {
                mediaSyncManager.play()
            }
        }

        override fun onPause() {
            AALogger.log(TAG, "onPause → forwarding")
            if (mediaSyncManager.hasActiveSession()) {
                mediaSyncManager.pause()
            }
        }

        override fun onSkipToNext() {
            AALogger.log(TAG, "onSkipToNext → forwarding")
            mediaSyncManager.skipToNext()
        }

        override fun onSkipToPrevious() {
            AALogger.log(TAG, "onSkipToPrevious → forwarding")
            mediaSyncManager.skipToPrevious()
        }

        override fun onStop() {
            AALogger.log(TAG, "onStop → forwarding")
            mediaSyncManager.stop()
        }

        override fun onSeekTo(pos: Long) {
            AALogger.log(TAG, "onSeekTo($pos) → forwarding")
            mediaSyncManager.seekTo(pos)
        }
    }

    // ─── Playback Handlers ──────────────────────────────────────────────

    private fun handleShuffleClick(mediaId: String) {
        val id = mediaId.removePrefix("shuffle_").toIntOrNull() ?: return
        beginLaunch()

        scope.launch {
            val playlist = repository.getPlaylistById(id)
            if (playlist != null) {
                val listId = Uri.parse(playlist.url).getQueryParameter("list")
                if (listId != null) {
                    val url = "https://music.youtube.com/watch?list=$listId&shuffle=1"
                    AALogger.forceLog(TAG, "Launching shuffle: $url")
                    launchYouTubeMusic(url)
                } else {
                    AALogger.logError(TAG, "No list ID in URL: ${playlist.url}")
                }
            }
        }
    }

    private fun handleTrackClick(mediaId: String) {
        val idStr = mediaId.removePrefix("track_")
        val sep = idStr.indexOf('_')
        if (sep == -1) return
        val playlistId = idStr.substring(0, sep).toIntOrNull() ?: return
        val videoId = idStr.substring(sep + 1)

        beginLaunch()

        scope.launch {
            val playlist = repository.getPlaylistById(playlistId)
            val listId = playlist?.url?.let { Uri.parse(it).getQueryParameter("list") }
            val url = if (listId != null) {
                "https://music.youtube.com/watch?v=$videoId&list=$listId"
            } else {
                "https://music.youtube.com/watch?v=$videoId"
            }
            AALogger.forceLog(TAG, "Launching track: $url")
            launchYouTubeMusic(url)
        }
    }

    // ─── Launch Mechanics ───────────────────────────────────────────────

    /**
     * Sets STATE_CONNECTING with dummy metadata and starts the suppression timer.
     */
    private fun beginLaunch() {
        lastLaunchTimestamp.set(System.currentTimeMillis())
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f)
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Lade...")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "YouTube Music")
                .build()
        )
        AALogger.forceLog(TAG, "beginLaunch: STATE_CONNECTING set, suppression ON (${LAUNCH_SUPPRESS_DURATION_MS}ms)")
    }

    private fun isStateSuppressed(): Boolean {
        return System.currentTimeMillis() - lastLaunchTimestamp.get() < LAUNCH_SUPPRESS_DURATION_MS
    }

    private fun clearSuppression() {
        lastLaunchTimestamp.set(0)
    }

    private fun launchYouTubeMusic(url: String) {
        AALogger.forceLog(TAG, "launchYouTubeMusic: $url")
        mainHandler.post {
            // Promote to foreground service BEFORE startActivity()
            // This grants the background activity start exemption on Android 10+
            promoteToForeground()

            var started = false
            for (pkg in ytMusicPackages) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        `package` = pkg
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    AALogger.forceLog(TAG, "Successfully launched via: $pkg")
                    started = true
                    break
                } catch (e: Exception) {
                    AALogger.log(TAG, "Package $pkg not available: ${e.message}")
                }
            }
            if (!started) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    AALogger.forceLog(TAG, "Launched via generic fallback")
                } catch (e: Exception) {
                    AALogger.logError(TAG, "All launch attempts failed", e)
                }
            }

            // Demote back to background after keepalive period
            mainHandler.postDelayed({
                demoteFromForeground()
            }, FOREGROUND_KEEPALIVE_MS)
        }
    }

    // ─── Foreground Service Management ──────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "YT Auto Proxy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Benötigt um YouTube Music aus dem Hintergrund zu starten"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Temporarily promotes this service to foreground so startActivity() works
     * even when the app is in the background (Android 10+ restriction).
     */
    private fun promoteToForeground() {
        try {
            ensureNotificationChannel()
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("YT Playlists")
                .setContentText("Starte YouTube Music...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
            AALogger.forceLog(TAG, "Promoted to foreground service")
        } catch (e: Exception) {
            AALogger.logError(TAG, "Failed to promote to foreground", e)
        }
    }

    private fun demoteFromForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            AALogger.log(TAG, "Demoted from foreground service")
        } catch (e: Exception) {
            AALogger.logError(TAG, "Failed to demote from foreground", e)
        }
    }

    // ─── Proxy Synchronisation ──────────────────────────────────────────

    private fun startProxySync() {
        // Metadata: ALWAYS synced (never suppressed). The car should always
        // show the real song title, even during launch transitions.
        scope.launch(Dispatchers.Main) {
            mediaSyncManager.currentMetadata.collect { metadata ->
                if (metadata == null) return@collect
                try {
                    val builder = MediaMetadataCompat.Builder()
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.let {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it)
                    }
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.let {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it)
                    }
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.let {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it)
                    }
                    metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).let {
                        if (it > 0) builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it)
                    }
                    metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)?.let {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, it)
                    }
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)?.let {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it)
                    }
                    val artBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    val albumArtBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    
                    val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) 
                                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

                    if (artBmp != null) {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBmp)
                    } else if (lastLoadedBitmapUri == artUri && lastLoadedBitmap != null) {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, lastLoadedBitmap)
                    }
                    
                    if (albumArtBmp != null) {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBmp)
                    } else if (lastLoadedBitmapUri == artUri && lastLoadedBitmap != null) {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, lastLoadedBitmap)
                    }

                    mediaSession.setMetadata(builder.build())
                    AALogger.log(TAG, "Proxy: Synced metadata → ${metadata.getString(MediaMetadata.METADATA_KEY_TITLE)}")

                    if (artBmp == null && albumArtBmp == null && !artUri.isNullOrEmpty() && lastLoadedBitmapUri != artUri) {
                        lastLoadedBitmapUri = artUri // verhinder mehrmaliges Ausführen
                        scope.launch(Dispatchers.IO) {
                            try {
                                val uri = android.net.Uri.parse(artUri)
                                val inputStream = if (artUri.startsWith("http")) {
                                    java.net.URL(artUri).readBytes().inputStream()
                                } else {
                                    contentResolver.openInputStream(uri)
                                }
                                
                                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                inputStream?.close()

                                if (bitmap != null) {
                                    lastLoadedBitmap = bitmap
                                    withContext(Dispatchers.Main) {
                                        val currentMeta = mediaSession.controller.metadata
                                        if (currentMeta != null) {
                                            val currentBuilder = MediaMetadataCompat.Builder(currentMeta)
                                            currentBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                                            currentBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                                            mediaSession.setMetadata(currentBuilder.build())
                                            AALogger.log(TAG, "Proxy: Applied lazy artwork")
                                        }
                                    }
                                } else {
                                    lastLoadedBitmapUri = null // reset falls fehlgeschlagen
                                }
                            } catch (e: Exception) {
                                lastLoadedBitmapUri = null
                                AALogger.log(TAG, "Proxy: Failed to lazily load artwork: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AALogger.logError(TAG, "Proxy: Error syncing metadata", e)
                }
            }
        }

        // PlaybackState: suppressed during launch window.
        // Only STATE_PLAYING lifts suppression early (= new track started).
        scope.launch(Dispatchers.Main) {
            mediaSyncManager.currentPlaybackState.collect { playbackState ->
                if (playbackState == null) return@collect
                val stateInt = playbackState.state

                if (isStateSuppressed()) {
                    if (stateInt == android.media.session.PlaybackState.STATE_PLAYING) {
                        AALogger.forceLog(TAG, "Proxy: STATE_PLAYING during suppression → lifting")
                        clearSuppression()
                        // fall through to sync
                    } else {
                        AALogger.log(TAG, "Proxy: State ($stateInt) suppressed")
                        return@collect
                    }
                }

                try {
                    mediaSession.setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setActions(SUPPORTED_ACTIONS)
                            .setState(
                                mapPlaybackState(stateInt),
                                playbackState.position,
                                playbackState.playbackSpeed
                            )
                            .build()
                    )
                    AALogger.log(TAG, "Proxy: Synced state → $stateInt")
                } catch (e: Exception) {
                    AALogger.logError(TAG, "Proxy: Error syncing state", e)
                }
            }
        }
    }

    private fun mapPlaybackState(state: Int): Int = when (state) {
        android.media.session.PlaybackState.STATE_PLAYING -> PlaybackStateCompat.STATE_PLAYING
        android.media.session.PlaybackState.STATE_PAUSED -> PlaybackStateCompat.STATE_PAUSED
        android.media.session.PlaybackState.STATE_STOPPED -> PlaybackStateCompat.STATE_STOPPED
        android.media.session.PlaybackState.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
        android.media.session.PlaybackState.STATE_CONNECTING -> PlaybackStateCompat.STATE_CONNECTING
        android.media.session.PlaybackState.STATE_SKIPPING_TO_NEXT -> PlaybackStateCompat.STATE_SKIPPING_TO_NEXT
        android.media.session.PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS
        android.media.session.PlaybackState.STATE_FAST_FORWARDING -> PlaybackStateCompat.STATE_FAST_FORWARDING
        android.media.session.PlaybackState.STATE_REWINDING -> PlaybackStateCompat.STATE_REWINDING
        else -> PlaybackStateCompat.STATE_NONE
    }

    companion object {
        private const val SUPPORTED_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
    }
}
