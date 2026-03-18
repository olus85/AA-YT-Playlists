package app.olus.ytmusic.autolauncher.ui.auto

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import app.olus.ytmusic.autolauncher.data.local.PlaylistDatabase
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Precision
import coil.size.Scale
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.olus.ytmusic.autolauncher.service.LauncherMediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "YTMusicCarApp"

@AndroidEntryPoint
class YTMusicCarAppService : CarAppService() {

    @Inject
    lateinit var repository: PlaylistRepository

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
    }

    override fun createHostValidator(): HostValidator {
        Log.d(TAG, "createHostValidator")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.d(TAG, "onCreateSession")
        return YTMusicCarSession(repository)
    }
}

class YTMusicCarSession(private val repository: PlaylistRepository) : Session() {
    val mediaSession by lazy { LauncherMediaSession(carContext) }
    val currentlyPlayingUrl = MutableStateFlow<String?>(null)

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "onCreateScreen")
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                mediaSession.release()
            }
        })

        return PlaylistGridScreen(carContext, repository, this)
    }
}

class PlaylistGridScreen(
    carContext: CarContext,
    private val repository: PlaylistRepository,
    private val session: YTMusicCarSession
) : Screen(carContext) {

    private var playlists: List<Playlist> = emptyList()
    private val playlistIcons = LruCache<String, CarIcon>(50)
    private val imageLoader = ImageLoader(carContext)
    private var started = false
    private var playingUrl: String? = null

    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate: ${playlists.size} items, started=$started")

        // Start observing on first template request (lifecycle is guaranteed active here)
        if (!started) {
            started = true
            startObservingPlaylists()
        }

        return try {
            buildListTemplate()
        } catch (e: Exception) {
            Log.e(TAG, "Error building template", e)
            buildErrorTemplate()
        }
    }

    private fun startObservingPlaylists() {
        lifecycleScope.launch {
            try {
                repository.getAllPlaylists().collect { updatedList ->
                    Log.d(TAG, "Playlists updated: ${updatedList.size}")
                    playlists = updatedList
                    invalidate()
                    loadIcons()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing playlists", e)
            }
        }
        lifecycleScope.launch {
            session.currentlyPlayingUrl.collect { url ->
                playingUrl = url
                invalidate()
            }
        }
    }

    private fun loadIcons() {
        playlists.forEach { playlist ->
            if (playlist.imageUrl.isNotEmpty() && playlistIcons.get(playlist.imageUrl) == null) {
                val request = ImageRequest.Builder(carContext)
                    .data(playlist.imageUrl)
                    .size(400, 400)
                    .precision(Precision.INEXACT)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .target { result ->
                        try {
                            val bitmap = (result as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                playlistIcons.put(playlist.imageUrl, CarIcon.Builder(
                                    IconCompat.createWithBitmap(bitmap)
                                ).build())
                                invalidate()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing icon for ${playlist.id}", e)
                        }
                    }
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildListTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        if (playlists.isEmpty()) {
            itemListBuilder.setNoItemsMessage("Keine Playlisten. Füge welche am Handy hinzu.")
        } else {
            playlists.forEach { playlist ->
                val carIcon = playlistIcons.get(playlist.imageUrl) ?: getDefaultCarIcon()

                val subtitle = listOfNotNull(playlist.trackCount, playlist.duration)
                    .joinToString(" • ")

                val rowBuilder = androidx.car.app.model.Row.Builder()
                    .setTitle(playlist.title.ifEmpty { "Playlist" })
                    .setImage(carIcon, androidx.car.app.model.Row.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        Log.d(TAG, "Clicked: ${playlist.title}")
                        screenManager.push(PlaylistDetailScreen(carContext, playlist, session))
                    }
                    
                val isPlaying = playingUrl == playlist.url
                val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                val actionIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build()
                rowBuilder.addAction(
                    Action.Builder()
                        .setIcon(actionIcon)
                        .setOnClickListener {
                            if (isPlaying) {
                                session.currentlyPlayingUrl.value = null
                                session.mediaSession.setPlaying(false)
                                pauseMusic()
                            } else {
                                session.currentlyPlayingUrl.value = playlist.url
                                session.mediaSession.setPlaying(true)
                                openPlaylistUrl(playlist.url)
                            }
                        }
                        .build()
                )

                if (subtitle.isNotEmpty()) {
                    rowBuilder.addText(subtitle)
                }

                itemListBuilder.addItem(rowBuilder.build())
            }
        }

        return androidx.car.app.model.ListTemplate.Builder()
            .setTitle("YT Playlists")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(itemListBuilder.build())
            .build()
    }

    @Suppress("DEPRECATION")
    private fun buildErrorTemplate(): Template {
        return androidx.car.app.model.ListTemplate.Builder()
            .setTitle("YT Playlists")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(
                ItemList.Builder()
                    .setNoItemsMessage("Ein Fehler ist aufgetreten.")
                    .build()
            )
            .build()
    }

    private fun getDefaultCarIcon(): CarIcon {
        return CarIcon.Builder(
            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_gallery)
        ).build()
    }

    private fun openPlaylistUrl(url: String) {
        try {
            val intent = Intent("app.olus.ytmusic.autolauncher.ACTION_OPEN_PLAYLIST").apply {
                `package` = carContext.packageName
                putExtra("playlist_url", url)
            }
            carContext.sendBroadcast(intent)
            CarToast.makeText(carContext, "Wird gestartet...", CarToast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            CarToast.makeText(carContext, "Fehler: ${e.message}", CarToast.LENGTH_LONG).show()
        }
    }

    private fun pauseMusic() {
        try {
            val audioManager = carContext.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause music", e)
        }
    }
}

class PlaylistDetailScreen(
    carContext: CarContext,
    private val playlist: Playlist,
    private val session: YTMusicCarSession
) : Screen(carContext) {

    private val fetcher = app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher()
    private var tracks: List<app.olus.ytmusic.autolauncher.data.repository.Track>? = null
    private var isError = false
    private var isLoading = true

    override fun onGetTemplate(): Template {
        if (isLoading) {
            lifecycleScope.launch {
                val result = fetcher.fetchTracks(playlist.url)
                result.fold(
                    onSuccess = { 
                        tracks = it
                        isLoading = false
                        invalidate()
                    },
                    onFailure = {
                        isError = true
                        isLoading = false
                        invalidate()
                    }
                )
            }
            return androidx.car.app.model.ListTemplate.Builder()
                .setTitle(playlist.title)
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build()
        }

        val itemListBuilder = ItemList.Builder()

        if (isError || tracks.isNullOrEmpty()) {
            itemListBuilder.setNoItemsMessage("Fehler beim Laden der Tracks.")
        } else {
            tracks!!.forEach { track ->
                itemListBuilder.addItem(
                    androidx.car.app.model.Row.Builder()
                        .setTitle(track.title)
                        .addText(track.author)
                        .setOnClickListener {
                            val listId = fetcher.extractPlaylistId(playlist.url) ?: ""
                            val trackUrl = "https://music.youtube.com/watch?v=${track.videoId}&list=$listId"
                            openTrackUrl(trackUrl)
                        }
                        .build()
                )
            }
        }

        val actionStrip = androidx.car.app.model.ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, app.olus.ytmusic.autolauncher.R.drawable.ic_shuffle)).build())
                    .setOnClickListener {
                        session.currentlyPlayingUrl.value = playlist.url
                        session.mediaSession.setPlaying(true)
                        tracks?.let { list ->
                            if (list.isNotEmpty()) {
                                val track = list.random()
                                val listId = fetcher.extractPlaylistId(playlist.url) ?: ""
                                val trackUrl = "https://music.youtube.com/watch?v=${track.videoId}&list=$listId"
                                openTrackUrl(trackUrl)
                            } else {
                                openTrackUrl(playlist.url)
                            }
                        } ?: run {
                            openTrackUrl(playlist.url)
                        }
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_media_next)).build())
                    .setOnClickListener {
                        session.currentlyPlayingUrl.value = playlist.url
                        session.mediaSession.setPlaying(true)
                        tracks?.drop(1)?.firstOrNull()?.let { track ->
                            val listId = fetcher.extractPlaylistId(playlist.url) ?: ""
                            val trackUrl = "https://music.youtube.com/watch?v=${track.videoId}&list=$listId"
                            openTrackUrl(trackUrl)
                        } ?: run {
                            openTrackUrl(playlist.url)
                        }
                    }
                    .build()
            )
            .build()
            
        return androidx.car.app.model.ListTemplate.Builder()
            .setTitle(playlist.title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setSingleList(itemListBuilder.build())
            .build()
    }

    private fun openTrackUrl(url: String) {
        try {
            val intent = Intent("app.olus.ytmusic.autolauncher.ACTION_OPEN_PLAYLIST").apply {
                `package` = carContext.packageName
                putExtra("playlist_url", url)
            }
            carContext.sendBroadcast(intent)
            CarToast.makeText(carContext, "Wird gestartet...", CarToast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            CarToast.makeText(carContext, "Fehler: ${e.message}", CarToast.LENGTH_LONG).show()
        }
    }
}
