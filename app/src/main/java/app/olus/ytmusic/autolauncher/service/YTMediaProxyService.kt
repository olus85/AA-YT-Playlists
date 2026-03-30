package app.olus.ytmusic.autolauncher.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.olus.ytmusic.autolauncher.util.AALogger

private const val TAG = "YTMediaProxyService"

/**
 * Target YouTube Music package names (official + ReVanced variants).
 */
private val YT_MUSIC_PACKAGES = setOf(
    "app.rvx.android.apps.youtube.music",
    "app.revanced.android.apps.youtube.music",
    "com.google.android.apps.youtube.music"
)

/**
 * NotificationListenerService that listens for active MediaSessions on the system,
 * filters for YouTube Music, and bridges its state into [MediaSyncManager] so the
 * Android Auto service can mirror it.
 *
 * This service requires the BIND_NOTIFICATION_LISTENER_SERVICE permission,
 * which the user must grant manually in system settings.
 */
class YTMediaProxyService : NotificationListenerService() {

    private var mediaSyncManager: MediaSyncManager? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        AALogger.forceLog(TAG, "onCreate: YTMediaProxyService starting")

        // Get the MediaSyncManager singleton from the Hilt application component
        mediaSyncManager = MediaSyncManagerProvider.get(applicationContext)

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (mediaSessionManager == null) {
            AALogger.logError(TAG, "MediaSessionManager is null!")
            return
        }

        val componentName = ComponentName(this, YTMediaProxyService::class.java)

        sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            AALogger.forceLog(TAG, "Active sessions changed: ${controllers?.size ?: 0} sessions")
            controllers?.forEach { controller ->
                AALogger.log(TAG, "  Session: ${controller.packageName} tag=${controller.tag}")
            }
            findAndBindYTMusicSession(controllers)
        }

        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener!!,
                componentName
            )
            AALogger.forceLog(TAG, "Registered OnActiveSessionsChangedListener")

            // Check existing sessions immediately
            val currentSessions = mediaSessionManager?.getActiveSessions(componentName)
            AALogger.forceLog(TAG, "Current active sessions: ${currentSessions?.size ?: 0}")
            findAndBindYTMusicSession(currentSessions)
        } catch (e: SecurityException) {
            AALogger.logError(TAG, "SecurityException: Notification access not granted?", e)
        } catch (e: Exception) {
            AALogger.logError(TAG, "Error setting up session listener", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AALogger.forceLog(TAG, "onDestroy")

        sessionsChangedListener?.let { listener ->
            try {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(listener)
            } catch (e: Exception) {
                AALogger.logError(TAG, "Error removing listener", e)
            }
        }
        sessionsChangedListener = null
        mediaSyncManager?.setActiveController(null)
    }

    private fun findAndBindYTMusicSession(controllers: List<MediaController>?) {
        if (controllers == null) return

        val ytController = controllers.firstOrNull { controller ->
            controller.packageName in YT_MUSIC_PACKAGES
        }

        if (ytController != null) {
            AALogger.forceLog(TAG, "Found YT Music session: ${ytController.packageName}")
            mediaSyncManager?.setActiveController(ytController)
        } else {
            AALogger.log(TAG, "No YT Music session found among active sessions")
            // Don't clear the controller here – keep the last known state
            // until a new session appears or the session is explicitly destroyed
        }
    }

    // NotificationListenerService callbacks (required but not our primary mechanism)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // We don't need to process notifications directly;
        // the MediaSessionManager listener handles session discovery.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Check if a YT Music notification was removed → session might be gone
        if (sbn?.packageName in YT_MUSIC_PACKAGES) {
            AALogger.log(TAG, "YT Music notification removed: ${sbn?.packageName}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AALogger.forceLog(TAG, "onListenerConnected: Notification access is active")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AALogger.forceLog(TAG, "onListenerDisconnected")
    }
}

/**
 * Manual provider for MediaSyncManager, since NotificationListenerService
 * is instantiated by the system and cannot use constructor injection.
 */
object MediaSyncManagerProvider {
    @Volatile
    private var instance: MediaSyncManager? = null

    fun get(context: Context): MediaSyncManager {
        return instance ?: synchronized(this) {
            instance ?: run {
                // Try to get from Hilt's EntryPoint
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    MediaSyncManagerEntryPoint::class.java
                )
                entryPoint.mediaSyncManager().also { instance = it }
            }
        }
    }
}

/**
 * Hilt EntryPoint for accessing the MediaSyncManager singleton from
 * system services that can't use @AndroidEntryPoint.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MediaSyncManagerEntryPoint {
    fun mediaSyncManager(): MediaSyncManager
}
