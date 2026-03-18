package app.olus.ytmusic.autolauncher

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YTMusicAutoLauncherApp : Application() {
    
    var sharedUrlToProcess: String? = null

}
