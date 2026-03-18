package app.olus.ytmusic.autolauncher.service

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class LauncherMediaSession(context: Context) {
    private val mediaSession = MediaSessionCompat(context, "YTMusicAutoLauncher")

    init {
        // Set the session's playback state to indicate we are handling media.
        // Even if we are just a launcher, an active playing/paused state helps 
        // Android Auto recognize this app for the dashboard split-screen media widget.
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
            
        mediaSession.setPlaybackState(stateBuilder.build())
        mediaSession.isActive = true
    }

    fun setPlaying(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, 0, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }
}
