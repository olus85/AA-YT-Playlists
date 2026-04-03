package app.olus.ytmusic.autolauncher

import android.app.Application
import app.olus.ytmusic.autolauncher.util.AALogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YTMusicAutoLauncherApp : Application() {
    
    var sharedUrlToProcess: String? = null
    
    val triggerLyricsDialog = kotlinx.coroutines.flow.MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        AALogger.init(this)
    }
}
