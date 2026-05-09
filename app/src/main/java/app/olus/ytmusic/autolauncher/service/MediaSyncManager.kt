package app.olus.ytmusic.autolauncher.service

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import app.olus.ytmusic.autolauncher.util.AALogger
import app.olus.ytmusic.autolauncher.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaSyncManager"

/**
 * Singleton manager that bridges between the YTMediaProxyService (NotificationListenerService)
 * and the YTMediaBrowserService (Android Auto UI).
 *
 * It holds the active YouTube Music MediaController and exposes its state as Flows,
 * so the Auto service can mirror metadata and playback state onto the car display.
 */
@Singleton
class MediaSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _activeController = MutableStateFlow<MediaController?>(null)
    val activeController: StateFlow<MediaController?> = _activeController.asStateFlow()

    private val _currentMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentMetadata: StateFlow<MediaMetadata?> = _currentMetadata.asStateFlow()

    private val _currentPlaybackState = MutableStateFlow<PlaybackState?>(null)
    val currentPlaybackState: StateFlow<PlaybackState?> = _currentPlaybackState.asStateFlow()

    private var controllerCallback: MediaController.Callback? = null

    // ─── Source Mode Handling ───────────────────────────────────────────
    enum class SourceMode { YOUTUBE, JELLYFIN }

    private val _currentSourceMode = MutableStateFlow(SourceMode.YOUTUBE)
    val currentSourceMode: StateFlow<SourceMode> = _currentSourceMode.asStateFlow()

    fun switchSourceMode(mode: SourceMode) {
        if (_currentSourceMode.value != mode) {
            _currentSourceMode.value = mode
            AALogger.forceLog(TAG, "Switched source mode to $mode")
            // Re-emit known state for the active mode if switching back to YouTube
            if (mode == SourceMode.YOUTUBE) {
                _activeController.value?.let { controller ->
                    _currentMetadata.value = controller.metadata
                    _currentPlaybackState.value = controller.playbackState
                }
            }
        }
    }

    // Callbacks for Native Jellyfin Player (ExoPlayer)
    var onJellyfinPlay: (() -> Unit)? = null
    var onJellyfinPause: (() -> Unit)? = null
    var onJellyfinSkipToNext: (() -> Unit)? = null
    var onJellyfinSkipToPrevious: (() -> Unit)? = null
    var onJellyfinStop: (() -> Unit)? = null
    var onJellyfinSeekTo: ((Long) -> Unit)? = null

    private fun processMetadataUpdate(newMetadata: MediaMetadata?) {
        val oldTitle = _currentMetadata.value?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val newTitle = newMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

        if (newTitle != null && newTitle != oldTitle) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val autoLyrics = prefs.getBoolean("auto_lyrics", false)
            if (!autoLyrics) {
                AALogger.log(TAG, "Auto-Lyrics deaktiviert, überspringe Foreground-Start")
                _currentMetadata.value = newMetadata
                return
            }
            // Track hat sich geändert -> App in den Vordergrund + Lyrics
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = "app.olus.ytmusic.autolauncher.ACTION_SHOW_LYRICS"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                AALogger.logError(TAG, "Fehler beim Foreground-Start für Lyrics", e)
            }
        }
        
        _currentMetadata.value = newMetadata
    }

    fun updateFromJellyfin(metadata: MediaMetadata?, state: PlaybackState?) {
        if (_currentSourceMode.value == SourceMode.JELLYFIN) {
            processMetadataUpdate(metadata)
            _currentPlaybackState.value = state
        }
    }

    /**
     * Called by YTMediaProxyService when the target YT Music session is found/changed.
     */
    fun setActiveController(controller: MediaController?) {
        AALogger.forceLog(TAG, "setActiveController: ${controller?.packageName ?: "null"}")

        val oldController = _activeController.value
        _activeController.value = controller

        oldController?.let { old ->
            controllerCallback?.let { cb -> old.unregisterCallback(cb) }
        }

        if (controller != null) {
            if (_currentSourceMode.value == SourceMode.YOUTUBE) {
                _currentMetadata.value = controller.metadata
                _currentPlaybackState.value = controller.playbackState
            }

            controllerCallback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (_currentSourceMode.value == SourceMode.YOUTUBE) {
                        processMetadataUpdate(metadata)
                    }
                }
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    // Auto-switching back to YouTube if user starts playing on phone directly
                    if (state?.state == PlaybackState.STATE_PLAYING && _currentSourceMode.value == SourceMode.JELLYFIN) {
                        switchSourceMode(SourceMode.YOUTUBE)
                    }
                    if (_currentSourceMode.value == SourceMode.YOUTUBE) {
                        _currentPlaybackState.value = state
                    }
                }
                override fun onSessionDestroyed() {
                    _activeController.value = null
                    if (_currentSourceMode.value == SourceMode.YOUTUBE) {
                        _currentMetadata.value = null
                        _currentPlaybackState.value = null
                    }
                }
            }
            controller.registerCallback(controllerCallback!!)
        } else {
            if (_currentSourceMode.value == SourceMode.YOUTUBE) {
                _currentMetadata.value = null
                _currentPlaybackState.value = null
            }
            controllerCallback = null
        }
    }

    // ─── Transport Control Forwarding ───────────────────────────────────

    fun play() {
        AALogger.log(TAG, "Forwarding: play() in mode ${_currentSourceMode.value}")
        if (_currentSourceMode.value == SourceMode.JELLYFIN) onJellyfinPlay?.invoke()
        else _activeController.value?.transportControls?.play()
    }

    fun pause() {
        AALogger.log(TAG, "Forwarding: pause() in mode ${_currentSourceMode.value}")
        if (_currentSourceMode.value == SourceMode.JELLYFIN) onJellyfinPause?.invoke()
        else _activeController.value?.transportControls?.pause()
    }

    fun skipToNext() {
        AALogger.log(TAG, "Forwarding: skipToNext() in mode ${_currentSourceMode.value}")
        if (_currentSourceMode.value == SourceMode.JELLYFIN) onJellyfinSkipToNext?.invoke()
        else _activeController.value?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        AALogger.log(TAG, "Forwarding: skipToPrevious() in mode ${_currentSourceMode.value}")
        if (_currentSourceMode.value == SourceMode.JELLYFIN) onJellyfinSkipToPrevious?.invoke()
        else _activeController.value?.transportControls?.skipToPrevious()
    }

    fun stop() {
        AALogger.log(TAG, "Forwarding: stop() in mode ${_currentSourceMode.value}")
        if (_currentSourceMode.value == SourceMode.JELLYFIN) onJellyfinStop?.invoke()
        else _activeController.value?.transportControls?.stop()
    }

    fun seekTo(pos: Long) {
        AALogger.log(TAG, "Forwarding: seekTo($pos) in mode ${_currentSourceMode.value}")
        if (_currentSourceMode.value == SourceMode.JELLYFIN) onJellyfinSeekTo?.invoke(pos)
        else _activeController.value?.transportControls?.seekTo(pos)
    }

    fun hasActiveSession(): Boolean = _activeController.value != null || _currentSourceMode.value == SourceMode.JELLYFIN
}
