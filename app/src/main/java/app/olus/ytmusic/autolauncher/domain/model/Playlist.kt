package app.olus.ytmusic.autolauncher.domain.model

data class Playlist(
    val id: Int = 0,
    val url: String,
    val title: String,
    val imageUrl: String? = null,
    val position: Int = 0,
    val trackCount: String? = null,
    val duration: String? = null,
    val source: String = "YOUTUBE",
    val externalId: String? = null
) {
    init {
        require(url.isNotBlank()) { "Playlist URL must not be blank" }
        require(title.isNotBlank()) { "Playlist title must not be blank" }
    }
}
