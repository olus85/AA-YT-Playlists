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
    override fun onCreateScreen(intent: Intent): Screen {
        Log.d(TAG, "onCreateScreen")
        return PlaylistGridScreen(carContext, repository)
    }
}

class PlaylistGridScreen(
    carContext: CarContext,
    private val repository: PlaylistRepository
) : Screen(carContext) {

    private var playlists: List<Playlist> = emptyList()
    private val playlistIcons = LruCache<Int, CarIcon>(50)
    private val imageLoader = ImageLoader(carContext)
    private var started = false

    override fun onGetTemplate(): Template {
        Log.d(TAG, "onGetTemplate: ${playlists.size} items, started=$started")

        // Start observing on first template request (lifecycle is guaranteed active here)
        if (!started) {
            started = true
            startObservingPlaylists()
        }

        return try {
            buildGridTemplate()
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
    }

    private fun loadIcons() {
        playlists.forEach { playlist ->
            if (playlist.imageUrl.isNotEmpty() && playlistIcons.get(playlist.id) == null) {
                val request = ImageRequest.Builder(carContext)
                    .data(playlist.imageUrl)
                    .size(400, 400)
                    .precision(Precision.INEXACT)
                    .scale(Scale.FIT)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .target { result ->
                        try {
                            val bitmap = (result as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                playlistIcons.put(playlist.id, CarIcon.Builder(
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
    private fun buildGridTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        if (repository == null) {
            itemListBuilder.setNoItemsMessage("Fehler beim Laden der Datenbank. Bitte App neu starten.")
        } else if (playlists.isEmpty()) {
            itemListBuilder.setNoItemsMessage("Keine Playlisten. Füge welche am Handy hinzu.")
        } else {
            playlists.forEach { playlist ->
                val carIcon = playlistIcons.get(playlist.id) ?: getDefaultCarIcon()

                val subtitle = listOfNotNull(playlist.trackCount, playlist.duration)
                    .joinToString(" • ")

                val gridItemBuilder = GridItem.Builder()
                    .setTitle(playlist.title.ifEmpty { "Playlist" })
                    .setImage(carIcon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        Log.d(TAG, "Clicked: ${playlist.title}")
                        openPlaylistUrl(playlist.url)
                    }

                if (subtitle.isNotEmpty()) {
                    gridItemBuilder.setText(subtitle)
                }

                itemListBuilder.addItem(gridItemBuilder.build())
            }
        }

        return GridTemplate.Builder()
            .setTitle("YT Playlists")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(itemListBuilder.build())
            .build()
    }

    @Suppress("DEPRECATION")
    private fun buildErrorTemplate(): Template {
        return GridTemplate.Builder()
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
        if (url.isBlank() || !url.startsWith("http")) {
            CarToast.makeText(carContext, "Ungültige URL.", CarToast.LENGTH_LONG).show()
            return
        }

        try {
            val intent = Intent("app.olus.ytmusic.autolauncher.ACTION_OPEN_PLAYLIST").apply {
                `package` = carContext.packageName
                putExtra("playlist_url", url)
            }
            carContext.sendBroadcast(intent)
            CarToast.makeText(carContext, "Wird gestartet...", CarToast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send broadcast", e)
            CarToast.makeText(carContext, "Fehler: ${e.message}", CarToast.LENGTH_LONG).show()
        }
    }
}
