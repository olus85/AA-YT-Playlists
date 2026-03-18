package app.olus.ytmusic.autolauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "PlaylistReceiver"

class PlaylistReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("playlist_url")
        if (url != null) {
            Log.d(TAG, "Received broadcast for: $url")
            val playbackUrl = convertToPlaybackUrl(url)
            Log.d(TAG, "Converted to playback URL: $playbackUrl")
            openUrl(context, playbackUrl)
        }
    }

    /**
     * Converts a YouTube Music playlist URL to a playback URL.
     * 
     * The key insight: `/playlist?list=X` just OPENS the playlist page.
     * But `/watch?list=X` actually STARTS PLAYBACK of the first track.
     * 
     * Examples:
     * - music.youtube.com/playlist?list=PLxxx → music.youtube.com/watch?list=PLxxx
     * - youtube.com/playlist?list=PLxxx → music.youtube.com/watch?list=PLxxx
     */
    private fun convertToPlaybackUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            val listId = uri.getQueryParameter("list")
            val videoId = uri.getQueryParameter("v")
            
            if (videoId != null && listId != null) {
                return "https://music.youtube.com/watch?v=$videoId&list=$listId&shuffle=0"
            } else if (listId != null) {
                // Build a proper playback URL with the list ID
                return "https://music.youtube.com/watch?list=$listId&shuffle=0"
            }
            
            // If it's already a /watch URL or doesn't have a list param, 
            // just ensure it uses music.youtube.com
            val converted = url
                .replace("www.youtube.com", "music.youtube.com")
                .replace("youtube.com/playlist", "music.youtube.com/watch")
            
            return converted
        } catch (e: Exception) {
            Log.w(TAG, "URL conversion failed, using original: $url", e)
            return url
        }
    }

    private fun openUrl(context: Context, url: String) {
        Log.d(TAG, "Attempting to open URL: $url")
        
        // Try known YouTube Music packages in priority order
        val packages = listOf(
            "app.rvx.android.apps.youtube.music",
            "app.revanced.android.apps.youtube.music",
            "com.google.android.apps.youtube.music"
        )

        var started = false
        for (pkg in packages) {
            try {
                Log.d(TAG, "Trying package: $pkg")
                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    `package` = pkg
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
                Log.d(TAG, "Successfully started: $pkg")
                started = true
                break
            } catch (e: Exception) {
                Log.d(TAG, "Package $pkg not available: ${e.message}")
            }
        }

        // Generic fallback
        if (!started) {
            try {
                Log.d(TAG, "Using generic ACTION_VIEW fallback")
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
            } catch (e: Exception) {
                Log.e(TAG, "All launch attempts failed", e)
            }
        }
    }
}
