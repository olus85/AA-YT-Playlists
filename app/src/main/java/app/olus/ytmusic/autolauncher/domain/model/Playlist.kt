package app.olus.ytmusic.autolauncher.domain.model

data class Playlist(
    val id: Int = 0,
    val url: String,
    val title: String,
    val imageUrl: String,
    val position: Int = 0,
    val trackCount: String? = null,
    val duration: String? = null
)
