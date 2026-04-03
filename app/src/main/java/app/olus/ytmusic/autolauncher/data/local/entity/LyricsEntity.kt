package app.olus.ytmusic.autolauncher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricsEntity(
    @PrimaryKey
    val id: String, // "$artist-$track"
    val trackName: String,
    val artistName: String,
    val lyricsContent: String,
    val isSynced: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
