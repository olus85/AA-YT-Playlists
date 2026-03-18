package app.olus.ytmusic.autolauncher.data.repository

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

private const val TAG = "MetadataFetcher"

data class MetadataResult(
    val title: String,
    val imageUrl: String,
    val trackCount: String? = null,
    val duration: String? = null
)

class MetadataFetcher {

    // Invidious API instances for metadata fetching (no consent walls, no API key needed)
    private val fallbackInstances = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://yt.artemislena.eu",
        "https://vid.puffyan.us"
    )

    private var dynamicInstances: List<String> = emptyList()
    private var lastFetchTime = 0L

    private suspend fun getActiveInstances(): List<String> {
        if (dynamicInstances.isNotEmpty() && System.currentTimeMillis() - lastFetchTime < 3600000) {
            return dynamicInstances
        }
        try {
            Log.d(TAG, "Fetching active Invidious instances...")
            val response = Jsoup.connect("https://api.invidious.io/instances.json")
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(5000)
                .execute()

            val jsonArray = org.json.JSONArray(response.body())
            val instances = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONArray(i)
                if (item != null && item.length() > 1) {
                    val details = item.optJSONObject(1)
                    if (details != null && details.optBoolean("api", false)) {
                        val uri = details.optString("uri")
                        if (uri.isNotEmpty()) {
                            instances.add(uri)
                        }
                    }
                }
            }

            if (instances.isNotEmpty()) {
                // Shuffle and limit to 10 instances to avoid lengthy timeouts if many are down
                dynamicInstances = instances.shuffled().take(10)
                lastFetchTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch instances from api.invidious.io", e)
        }

        if (dynamicInstances.isEmpty()) {
            Log.d(TAG, "Using fallback instances")
            dynamicInstances = fallbackInstances
        }
        return dynamicInstances
    }

    suspend fun fetchMetadata(url: String): Result<MetadataResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching metadata for: $url")

            val playlistId = extractPlaylistId(url)
            if (playlistId == null) {
                Log.w(TAG, "Could not extract playlist ID from URL: $url")
                return@withContext Result.failure(Exception("Ungültige URL"))
            }
            Log.d(TAG, "Playlist ID: $playlistId")

            // Strategy 1: Invidious API (most reliable, no consent walls)
            val invResult = fetchFromInvidious(playlistId)
            if (invResult != null) {
                Log.d(TAG, "Invidious success: ${invResult.title}, ${invResult.trackCount}")
                return@withContext Result.success(invResult)
            }

            // Strategy 2: oEmbed API (works for videos, sometimes for playlists)
            val oEmbedResult = fetchFromOEmbed(url)
            if (oEmbedResult != null) {
                Log.d(TAG, "oEmbed success: ${oEmbedResult.title}")
                return@withContext Result.success(oEmbedResult)
            }

            // Strategy 3: YouTube RSS feed (limited but consent-free)
            val rssResult = fetchFromRssFeed(playlistId)
            if (rssResult != null) {
                Log.d(TAG, "RSS success: ${rssResult.title}")
                return@withContext Result.success(rssResult)
            }

            Result.failure(Exception("Keine Metadaten gefunden."))
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchMetadata", e)
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Strategy 1: Invidious API
    // ──────────────────────────────────────────────────────────────

    private suspend fun fetchFromInvidious(playlistId: String): MetadataResult? {
        val instancesToTry = getActiveInstances()
        for (instance in instancesToTry) {
            try {
                val apiUrl = "$instance/api/v1/playlists/$playlistId"
                Log.d(TAG, "Trying Invidious: $apiUrl")

                val response = Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .execute()

                val json = JSONObject(response.body())

                val title = json.optString("title", "")
                val videoCount = json.optInt("videoCount", 0)
                val author = json.optString("author", "")
                val thumbnailUrl = json.optString("playlistThumbnail", "")

                if (title.isNotEmpty()) {
                    val trackCount = if (videoCount > 0) "$videoCount Songs" else null
                    val duration = if (author.isNotEmpty() && author != "YouTube") author else null

                    // Try to get highest resolution thumbnail
                    val bestThumb = upgradeImageResolution(thumbnailUrl)

                    return MetadataResult(title, bestThumb, trackCount, duration)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious instance $instance failed: ${e.message}")
            }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────
    // Strategy 2: YouTube oEmbed API
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromOEmbed(url: String): MetadataResult? {
        try {
            // oEmbed only works with www.youtube.com URLs
            val normalizedUrl = url.replace("music.youtube.com", "www.youtube.com")
            val encodedUrl = URLEncoder.encode(normalizedUrl, "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

            val response = Jsoup.connect(oEmbedUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .execute()

            val json = JSONObject(response.body())
            val title = json.optString("title", "")
            val thumbnailUrl = json.optString("thumbnail_url", "")
            val author = json.optString("author_name", "")

            if (title.isNotEmpty()) {
                val duration = if (author.isNotEmpty()) author else null
                return MetadataResult(title, upgradeImageResolution(thumbnailUrl), null, duration)
            }
        } catch (e: Exception) {
            Log.w(TAG, "oEmbed failed: ${e.message}")
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────
    // Strategy 3: YouTube RSS Feed
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromRssFeed(playlistId: String): MetadataResult? {
        try {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId"
            Log.d(TAG, "Trying RSS: $rssUrl")

            val doc = Jsoup.connect(rssUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get()

            val title = doc.select("feed > title").text()
            val entries = doc.select("entry")
            val trackCount = if (entries.size > 0) "${entries.size} Songs" else null

            // Get thumbnail from first video
            var thumbnailUrl = ""
            val firstEntry = entries.firstOrNull()
            if (firstEntry != null) {
                val mediaGroup = firstEntry.select("media|group media|thumbnail")
                if (mediaGroup.isNotEmpty()) {
                    thumbnailUrl = mediaGroup.attr("url")
                }
                // Fallback: construct thumbnail URL from video ID
                if (thumbnailUrl.isEmpty()) {
                    val videoId = firstEntry.select("yt|videoId").text()
                    if (videoId.isNotEmpty()) {
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    }
                }
            }

            if (title.isNotEmpty()) {
                return MetadataResult(title, upgradeImageResolution(thumbnailUrl), trackCount, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSS failed: ${e.message}")
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────

    /**
     * Extract playlist ID from various YouTube URL formats.
     */
    internal fun extractPlaylistId(inputUrl: String): String? {
        val url = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
            "https://$inputUrl"
        } else {
            inputUrl
        }

        return try {
            val uri = java.net.URI(url)
            val query = uri.query ?: return null
            val pairs = query.split("&")
            pairs.find { it.startsWith("list=") }?.substringAfter("list=")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Upgrade YouTube thumbnail to highest resolution.
     */
    private fun upgradeImageResolution(imageUrl: String): String {
        if (imageUrl.isEmpty()) return imageUrl

        // Clean up URL parameters first
        val cleanUrl = imageUrl.split("?").first()

        return cleanUrl
            .replace("hqdefault.jpg", "maxresdefault.jpg")
            .replace("mqdefault.jpg", "maxresdefault.jpg")
            .replace("/default.jpg", "/maxresdefault.jpg")
    }
}
