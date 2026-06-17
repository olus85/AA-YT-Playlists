package app.olus.ytmusic.autolauncher.util

object SearchQueryCleaner {

    @JvmStatic
    fun cleanVoiceQuery(query: String?): String {
        if (query.isNullOrBlank()) return ""
        var cleaned = query.trim().lowercase()
        
        // Remove common prefixes
        val prefixes = listOf("spiele ", "play ", "wiedergabe ", "suche nach ", "suche ", "search for ", "search ")
        var prefixMatched = true
        while (prefixMatched) {
            prefixMatched = false
            for (prefix in prefixes) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.substring(prefix.length).trim()
                    prefixMatched = true
                    break
                }
            }
        }
        
        // Remove common suffixes
        val suffixes = listOf(
            " auf playlist launcher", " on playlist launcher", " playlist launcher",
            " auf playlistlauncher", " on playlistlauncher", " playlistlauncher",
            " auf yt music auto launcher", " on yt music auto launcher", " yt music auto launcher",
            " auf youtube music", " on youtube music", " youtube music",
            " launcher"
        )
        var suffixMatched = true
        while (suffixMatched) {
            suffixMatched = false
            for (suffix in suffixes) {
                if (cleaned.endsWith(suffix)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length).trim()
                    suffixMatched = true
                    break
                }
            }
        }
        
        return cleaned.trim()
    }
}
