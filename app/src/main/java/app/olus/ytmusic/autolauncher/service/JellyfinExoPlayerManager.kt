package app.olus.ytmusic.autolauncher.service

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.olus.ytmusic.autolauncher.data.repository.JellyfinItem
import app.olus.ytmusic.autolauncher.data.repository.JellyfinRepository
import app.olus.ytmusic.autolauncher.util.AALogger

class JellyfinExoPlayerManager(
    private val context: Context,
    private val jellyfinRepository: JellyfinRepository,
    private val mediaSyncManager: MediaSyncManager,
    private val onPlaybackStateChange: (Boolean) -> Unit
) {
    private val TAG = "JellyfinExoPlayerManager"
    var exoPlayer: ExoPlayer? = null

    fun initialize() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(audioAttributes, true)
            
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.containsAny(
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_POSITION_DISCONTINUITY
                        )
                    ) {
                        updateMediaSyncManager()
                        onPlaybackStateChange(player.playWhenReady && player.playbackState == Player.STATE_READY)
                    }
                }
            })
        }

        mediaSyncManager.onJellyfinPlay = { exoPlayer?.play() }
        mediaSyncManager.onJellyfinPause = { exoPlayer?.pause() }
        mediaSyncManager.onJellyfinSkipToNext = { exoPlayer?.seekToNextMediaItem() }
        mediaSyncManager.onJellyfinSkipToPrevious = { exoPlayer?.seekToPreviousMediaItem() }
        mediaSyncManager.onJellyfinStop = { exoPlayer?.stop() }
        mediaSyncManager.onJellyfinSeekTo = { pos -> exoPlayer?.seekTo(pos) }
    }

    fun playTracks(tracks: List<JellyfinItem>, startIndex: Int, shuffle: Boolean) {
        val player = exoPlayer ?: return
        
        val mediaItems = tracks.map { track ->
            val streamUrl = jellyfinRepository.getAudioStreamUrl(track.id)
            val imageUrl = jellyfinRepository.getImageUrl(track.id)
            Media3Item.Builder()
                .setMediaId(track.id)
                .setUri(streamUrl)
                .setMediaMetadata(
                    Media3Metadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artist)
                        .setArtworkUri(imageUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        mediaSyncManager.switchSourceMode(MediaSyncManager.SourceMode.JELLYFIN)
    }

    private fun updateMediaSyncManager() {
        val player = exoPlayer ?: return
        
        // 1. Map to native android.media.session.PlaybackState
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
            Player.STATE_READY -> if (player.playWhenReady) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
            else -> PlaybackState.STATE_NONE
        }

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
            )
            .setState(state, player.currentPosition, 1.0f)

        // 2. Map Metadata to native android.media.MediaMetadata
        val metaBuilder = MediaMetadata.Builder()
        player.currentMediaItem?.let { item ->
            metaBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, item.mediaId)
            item.mediaMetadata.title?.let { metaBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, it.toString()) }
            item.mediaMetadata.artist?.let { metaBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, it.toString()) }
            item.mediaMetadata.artworkUri?.let { metaBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, it.toString()) }
            metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration.coerceAtLeast(0))
        }

        mediaSyncManager.updateFromJellyfin(metaBuilder.build(), stateBuilder.build())
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
