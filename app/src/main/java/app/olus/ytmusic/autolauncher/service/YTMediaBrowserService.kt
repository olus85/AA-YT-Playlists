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
import app.olus.ytmusic.autolauncher.data.repository.JellyfinItem
import app.olus.ytmusic.autolauncher.data.repository.JellyfinRepository
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.domain.model.Track
import app.olus.ytmusic.autolauncher.util.AALogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val TAG = "YTMediaBrowserService"
private const val ROOT_ID = "root"
private const val FOLDER_PLAYLISTS = "folder_playlists"
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
    @Inject lateinit var jellyfinRepository: JellyfinRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var mediaSession: MediaSessionCompat
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Timestamp of last launch; state-sync suppressed until elapsed > LAUNCH_SUPPRESS_DURATION_MS */
    private val lastLaunchTimestamp = AtomicLong(0L)

    private lateinit var jellyfinNativePlayer: JellyfinExoPlayerManager

    private val imageLoader: coil.ImageLoader by lazy {
        coil.ImageLoader.Builder(this).build()
    }

    private var lastLoadedBitmapUri: String? = null
    private var lastLoadedBitmap: android.graphics.Bitmap? = null
    private var pendingBitmapUri: String? = null
    private var activePlaylistId: Int?
        get() {
            return try {
                val id = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getInt("active_playlist_id", -1)
                if (id == -1) null else id
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            try {
                getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).edit().putInt("active_playlist_id", value ?: -1).apply()
            } catch (e: Exception) {
                // ignore
            }
        }

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

        jellyfinNativePlayer = JellyfinExoPlayerManager(
            context = this,
            jellyfinRepository = jellyfinRepository,
            mediaSyncManager = mediaSyncManager,
            onPlaybackStateChange = { isPlaying ->
                try {
                    if (isPlaying) {
                        ensureNotificationChannel()
                        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("Wiedergabe aktiv")
                            .setContentText("Jellyfin")
                            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                        } else {
                            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
                        }
                    } else {
                        stopForeground(false)
                    }
                } catch (e: Exception) {
                    AALogger.logError(TAG, "Error in Jellyfin playback state callback", e)
                }
            }
        ).apply { initialize() }

        startProxySync()
    }

    override fun onDestroy() {
        super.onDestroy()
        AALogger.forceLog(TAG, "onDestroy")
        job.cancel()
        jellyfinNativePlayer.release()
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
            parentId == ROOT_ID -> loadRootFolder(result)
            parentId == FOLDER_PLAYLISTS -> loadPlaylists(result)
            parentId.startsWith("playlist_") -> loadTracks(parentId, result)
            else -> result.sendResult(mutableListOf())
        }
    }

    /**
     * Returns a single virtual folder "Playlisten" as the only root item.
     * This forces Android Auto to render it as a single tab/entry, and clicking
     * it will show the actual playlists as a proper list with thumbnails.
     */
    private fun loadRootFolder(result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val folderDesc = MediaDescriptionCompat.Builder()
            .setMediaId(FOLDER_PLAYLISTS)
            .setTitle("Playlisten")
            .setSubtitle("Deine Musik")
            .build()

        val folderItem = MediaBrowserCompat.MediaItem(
            folderDesc,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
        result.sendResult(mutableListOf(folderItem))
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

                if (playlist.source == "JELLYFIN") {
                    loadJellyfinTracks(playlist, result)
                    return@launch
                }

                // ── Offline-first: Try DB cache first ──
                val cachedTracks = repository.getCachedTracks(id)
                if (cachedTracks.isNotEmpty()) {
                    AALogger.forceLog(TAG, "Serving ${cachedTracks.size} cached tracks for '${playlist.title}'")
                    result.sendResult(buildTrackItems(playlist, cachedTracks))

                    // Refresh in background (don't block the UI)
                    coroutineScope {
                        launch {
                            try {
                                val freshResult = withTimeoutOrNull(15_000L) {
                                    metadataFetcher.fetchTracks(playlist.url)
                                }
                                freshResult?.getOrNull()?.let { freshTracks ->
                                    repository.saveTracks(id, freshTracks)
                                    AALogger.log(TAG, "Background refresh: saved ${freshTracks.size} tracks for '${playlist.title}'")
                                }
                            } catch (e: Exception) {
                                AALogger.log(TAG, "Background refresh failed: ${e.message}")
                            }
                        }
                    }
                    return@launch
                }

                // ── No cache: fetch from network ──
                AALogger.forceLog(TAG, "No cache. Fetching tracks for '${playlist.title}' (timeout: 10s)")
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
                        // Save to DB for next time
                        repository.saveTracks(id, tracks)
                        result.sendResult(buildTrackItems(playlist, tracks))
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

    /**
     * Builds the MediaItem list for a set of tracks (shared by cache and network paths).
     */
    private fun buildTrackItems(playlist: app.olus.ytmusic.autolauncher.domain.model.Playlist, tracks: List<Track>): MutableList<MediaBrowserCompat.MediaItem> {
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
        return items
    }

    /**
     * Loads tracks from Jellyfin server for a Jellyfin-sourced playlist.
     */
    private suspend fun loadJellyfinTracks(
        playlist: app.olus.ytmusic.autolauncher.domain.model.Playlist,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        try {
            val itemId = playlist.externalId
            if (itemId.isNullOrEmpty()) {
                AALogger.logError(TAG, "Jellyfin playlist has no externalId")
                result.sendResult(mutableListOf())
                return
            }

            val jfTracks = jellyfinRepository.getPlaylistTracks(itemId)
            if (jfTracks.isEmpty()) {
                AALogger.log(TAG, "No Jellyfin tracks found for '${playlist.title}'")
                result.sendResult(mutableListOf())
                return
            }

            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            jfTracks.forEach { jfTrack ->
                val iconUri = jellyfinRepository.getImageUrl(jfTrack.id)
                items.add(MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("jftrack_${playlist.id}_${jfTrack.id}")
                        .setTitle(jfTrack.name)
                        .setSubtitle(jfTrack.artist)
                        .apply { if (iconUri != null) setIconUri(Uri.parse(iconUri)) }
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                ))
            }

            AALogger.forceLog(TAG, "Loaded ${jfTracks.size} Jellyfin tracks for '${playlist.title}'")
            result.sendResult(items)
        } catch (e: Exception) {
            AALogger.logError(TAG, "Error loading Jellyfin tracks", e)
            result.sendResult(mutableListOf())
        }
    }

    // ─── MediaSession Callback ──────────────────────────────────────────

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            AALogger.forceLog(TAG, "onPlayFromMediaId: $mediaId")
            if (mediaId == null) return
            when {
                mediaId.startsWith("shuffle_") -> handleShuffleClick(mediaId)
                mediaId.startsWith("jftrack_") -> handleTrackClick(mediaId)
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

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            AALogger.forceLog(TAG, "onPlayFromSearch: query='$query'")
            if (query.isNullOrBlank()) return

            beginLaunch()

            // Show "Searching..." metadata while searching
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Suche: $query")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Wird gesucht...")
                    .build()
            )

            scope.launch {
                try {
                    // 1. Try Jellyfin first (if configured)
                    val jfTrack = jellyfinRepository.searchTrack(query)
                    if (jfTrack != null) {
                        AALogger.forceLog(TAG, "Voice search: Jellyfin hit '${jfTrack.name}' by ${jfTrack.artist}")
                        mainHandler.post {
                            jellyfinNativePlayer.playTracks(listOf(jfTrack), 0, false)
                        }
                        return@launch
                    }

                    // 2. Fallback: Invidious search
                    val track = metadataFetcher.searchTrack(query)
                    if (track != null) {
                        AALogger.forceLog(TAG, "Voice search: Invidious hit '${track.title}' by ${track.author} (${track.videoId})")
                        val url = "https://music.youtube.com/watch?v=${track.videoId}"
                        launchYouTubeMusic(url)
                    } else {
                        AALogger.logError(TAG, "Voice search: no results for '$query'")
                        mediaSession.setPlaybackState(
                            PlaybackStateCompat.Builder()
                                .setActions(SUPPORTED_ACTIONS)
                                .setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
                                .setErrorMessage(
                                    PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED,
                                    "Kein Ergebnis f\u00fcr \"$query\""
                                )
                                .build()
                        )
                    }
                } catch (e: Exception) {
                    AALogger.logError(TAG, "Voice search failed", e)
                }
            }
        }
    }

    // ─── Playback Handlers ──────────────────────────────────────────────

    private fun handleShuffleClick(mediaId: String) {
        val id = mediaId.removePrefix("shuffle_").toIntOrNull() ?: return
        activePlaylistId = id
        beginLaunch()

        scope.launch {
            val playlist = repository.getPlaylistById(id)
            if (playlist != null) {
                if (playlist.source == "JELLYFIN") {
                    val externalId = playlist.externalId ?: return@launch
                    val tracks = jellyfinRepository.getPlaylistTracks(externalId)
                    if (tracks.isEmpty()) return@launch
                    mainHandler.post {
                        jellyfinNativePlayer.playTracks(tracks, 0, true)
                    }
                } else {
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
    }

    private fun handleTrackClick(mediaId: String) {
        // Check if it's a Jellyfin track
        if (mediaId.startsWith("jftrack_")) {
            handleJellyfinTrackClick(mediaId)
            return
        }

        val idStr = mediaId.removePrefix("track_")
        val sep = idStr.indexOf('_')
        if (sep == -1) return
        val playlistId = idStr.substring(0, sep).toIntOrNull() ?: return
        val videoId = idStr.substring(sep + 1)

        activePlaylistId = playlistId
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

    private fun handleJellyfinTrackClick(mediaId: String) {
        val idStr = mediaId.removePrefix("jftrack_")
        val sep = idStr.indexOf('_')
        if (sep == -1) return
        val playlistIdStr = idStr.substring(0, sep)
        val trackItemId = idStr.substring(sep + 1)
        val playlistId = playlistIdStr.toIntOrNull() ?: return

        beginLaunch()

        scope.launch {
            try {
                val playlist = repository.getPlaylistById(playlistId) ?: return@launch
                val externalId = playlist.externalId ?: return@launch

                val tracks = jellyfinRepository.getPlaylistTracks(externalId)
                if (tracks.isEmpty()) {
                    AALogger.logError(TAG, "No tracks found for Jellyfin playlist $externalId")
                    return@launch
                }

                val startIndex = tracks.indexOfFirst { it.id == trackItemId }.takeIf { it >= 0 } ?: 0

                mainHandler.post {
                    jellyfinNativePlayer.playTracks(tracks, startIndex, false)
                }
            } catch (e: Exception) {
                AALogger.logError(TAG, "Error playing Jellyfin track natively", e)
            }
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
        
        scope.launch {
            val controller = mediaSyncManager.activeController.value
            if (controller != null && ytMusicPackages.contains(controller.packageName)) {
                AALogger.log(TAG, "Trying background playFromUri...")
                val currentId = mediaSyncManager.currentMetadata.value?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                
                mainHandler.post {
                    controller.transportControls.playFromUri(Uri.parse(url), null)
                }
                
                // Wait up to 2.5 seconds to see if YT Music reacts
                val success = withTimeoutOrNull(2500L) {
                    while(true) {
                        kotlinx.coroutines.yield()
                        val state = mediaSyncManager.currentPlaybackState.value?.state
                        val metaId = mediaSyncManager.currentMetadata.value?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                        if (metaId != currentId || state == android.media.session.PlaybackState.STATE_BUFFERING || state == android.media.session.PlaybackState.STATE_PLAYING) {
                            return@withTimeoutOrNull true
                        }
                        kotlinx.coroutines.delay(100)
                    }
                }
                
                if (success == true) {
                    AALogger.forceLog(TAG, "Background playFromUri successful!")
                    return@launch
                }
                AALogger.forceLog(TAG, "Background launch timed out, trying Intent fallback.")
            }
            
            mainHandler.post {
                performIntentLaunch(url)
            }
        }
    }

    private fun performIntentLaunch(url: String) {
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
                    
                    val rawArtBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    val rawAlbumArtBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    val artBmp = scaleBitmap(rawArtBmp)
                    val albumArtBmp = scaleBitmap(rawAlbumArtBmp)
                    
                    val displayBmp = artBmp ?: albumArtBmp
                    
                    var artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) 
                                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

                    if (artUri.isNullOrEmpty()) {
                        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                        if (!title.isNullOrEmpty()) {
                            val cleanPlaying = title.lowercase().replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                            var matchingTrack: Track? = null
                            val playlistId = activePlaylistId
                            if (playlistId != null) {
                                val cachedTracks = repository.getCachedTracks(playlistId)
                                matchingTrack = cachedTracks.firstOrNull { 
                                    val cleanDb = it.title.lowercase().replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                                    cleanDb.contains(cleanPlaying) || cleanPlaying.contains(cleanDb)
                                }
                            }
                            if (matchingTrack == null) {
                                val allTracks = repository.getAllCachedTracks()
                                matchingTrack = allTracks.firstOrNull {
                                    val cleanDb = it.title.lowercase().replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                                    cleanDb.contains(cleanPlaying) || cleanPlaying.contains(cleanDb)
                                }
                            }
                            if (matchingTrack != null) {
                                artUri = "https://i.ytimg.com/vi/${matchingTrack.videoId}/hqdefault.jpg"
                                AALogger.log(TAG, "Proxy: Resolved artwork URI from database track cache: $artUri")
                            }
                        }
                    }

                    if (artUri.isNullOrEmpty()) {
                        lastLoadedBitmap = null
                        lastLoadedBitmapUri = null
                    } else {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
                        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                    }

                    if (displayBmp != null) {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, displayBmp)
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, displayBmp)
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, displayBmp)
                    } else if (lastLoadedBitmapUri == artUri && lastLoadedBitmap != null) {
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, lastLoadedBitmap)
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, lastLoadedBitmap)
                        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, lastLoadedBitmap)
                    }

                    mediaSession.setMetadata(builder.build())
                    AALogger.log(TAG, "Proxy: Synced metadata → ${metadata.getString(MediaMetadata.METADATA_KEY_TITLE)} | displayBmp=${displayBmp != null} (rawArt=${rawArtBmp != null}, rawAlbum=${rawAlbumArtBmp != null}) | artUri=$artUri")

                    if (displayBmp == null && !artUri.isNullOrEmpty()) {
                        if (lastLoadedBitmapUri != artUri) {
                            pendingBitmapUri = artUri
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val request = coil.request.ImageRequest.Builder(this@YTMediaBrowserService)
                                        .data(artUri)
                                        .size(400)
                                        .allowHardware(false)
                                        .build()
                                    val result = imageLoader.execute(request)
                                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap

                                    if (bitmap != null) {
                                        withContext(Dispatchers.Main) {
                                            if (pendingBitmapUri == artUri) {
                                                lastLoadedBitmapUri = artUri
                                                lastLoadedBitmap = bitmap
                                                pendingBitmapUri = null
                                                
                                                val currentBuilder = MediaMetadataCompat.Builder()
                                                metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.let {
                                                    currentBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it)
                                                }
                                                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.let {
                                                    currentBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it)
                                                }
                                                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.let {
                                                    currentBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it)
                                                }
                                                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).let {
                                                    if (it > 0) currentBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it)
                                                }
                                                
                                                currentBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                                                currentBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri)
                                                currentBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                                                
                                                currentBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                                                currentBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                                                currentBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                                                
                                                mediaSession.setMetadata(currentBuilder.build())
                                                AALogger.log(TAG, "Proxy: Applied lazy artwork (Coil, ${bitmap.width}x${bitmap.height})")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        if (pendingBitmapUri == artUri) {
                                            pendingBitmapUri = null
                                        }
                                    }
                                    AALogger.log(TAG, "Proxy: Failed to lazily load artwork: ${e.message}")
                                }
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

    private fun scaleBitmap(bitmap: android.graphics.Bitmap?, maxDimension: Int = 400): android.graphics.Bitmap? {
        if (bitmap == null) return null
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (ratio > 1) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return try {
            android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            AALogger.logError(TAG, "Failed to scale bitmap", e)
            bitmap
        }
    }

    companion object {
        private const val SUPPORTED_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
    }
}
