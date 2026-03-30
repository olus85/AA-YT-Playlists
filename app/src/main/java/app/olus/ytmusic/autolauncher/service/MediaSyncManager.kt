package app.olus.ytmusic.autolauncher.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import app.olus.ytmusic.autolauncher.util.AALogger
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
class MediaSyncManager @Inject constructor() {

    private val _activeController = MutableStateFlow<MediaController?>(null)
    val activeController: StateFlow<MediaController?> = _activeController.asStateFlow()

    private val _currentMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentMetadata: StateFlow<MediaMetadata?> = _currentMetadata.asStateFlow()

    private val _currentPlaybackState = MutableStateFlow<PlaybackState?>(null)
    val currentPlaybackState: StateFlow<PlaybackState?> = _currentPlaybackState.asStateFlow()

    private var controllerCallback: MediaController.Callback? = null

    /**
     * Called by YTMediaProxyService when the target YT Music session is found/changed.
     */
    fun setActiveController(controller: MediaController?) {
        AALogger.forceLog(TAG, "setActiveController: ${controller?.packageName ?: "null"}")

        // Remove old callback
        controllerCallback?.let { cb ->
            _activeController.value?.unregisterCallback(cb)
        }

        _activeController.value = controller

        if (controller != null) {
            // Push initial state
            _currentMetadata.value = controller.metadata
            _currentPlaybackState.value = controller.playbackState

            AALogger.forceLog(TAG, "Initial metadata: ${controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            AALogger.forceLog(TAG, "Initial state: ${controller.playbackState?.state}")

            // Register for updates
            controllerCallback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    AALogger.log(TAG, "Metadata changed: ${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
                    _currentMetadata.value = metadata
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    AALogger.log(TAG, "PlaybackState changed: ${state?.state}")
                    _currentPlaybackState.value = state
                }

                override fun onSessionDestroyed() {
                    AALogger.forceLog(TAG, "Session destroyed")
                    _activeController.value = null
                    _currentMetadata.value = null
                    _currentPlaybackState.value = null
                }
            }
            controller.registerCallback(controllerCallback!!)
        } else {
            _currentMetadata.value = null
            _currentPlaybackState.value = null
            controllerCallback = null
        }
    }

    // ─── Transport Control Forwarding ───────────────────────────────────

    fun play() {
        AALogger.log(TAG, "Forwarding: play()")
        _activeController.value?.transportControls?.play()
    }

    fun pause() {
        AALogger.log(TAG, "Forwarding: pause()")
        _activeController.value?.transportControls?.pause()
    }

    fun skipToNext() {
        AALogger.log(TAG, "Forwarding: skipToNext()")
        _activeController.value?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        AALogger.log(TAG, "Forwarding: skipToPrevious()")
        _activeController.value?.transportControls?.skipToPrevious()
    }

    fun stop() {
        AALogger.log(TAG, "Forwarding: stop()")
        _activeController.value?.transportControls?.stop()
    }

    fun seekTo(pos: Long) {
        AALogger.log(TAG, "Forwarding: seekTo($pos)")
        _activeController.value?.transportControls?.seekTo(pos)
    }

    /**
     * Checks if there's currently an active YT Music controller connected.
     */
    fun hasActiveSession(): Boolean = _activeController.value != null
}
