package app.olus.ytmusic.autolauncher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val title: String,
    val imageUrl: String,
    val position: Int = 0,
    val trackCount: String? = null,
    val duration: String? = null
)
