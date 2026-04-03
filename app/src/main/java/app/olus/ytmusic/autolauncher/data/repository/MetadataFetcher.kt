package app.olus.ytmusic.autolauncher.data.repository

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.SocketTimeoutException
import java.net.URLEncoder

private const val TAG = "MetadataFetcher"

data class MetadataResult(
    val title: String,
    val imageUrl: String,
    val trackCount: String? = null,
    val duration: String? = null
)

data class Track(
    val title: String,
    val author: String,
    val videoId: String
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

    // In-memory cache for tracks
    private val trackCache = android.util.LruCache<String, List<Track>>(20)

    // Retry configuration
    companion object {
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 500L
        private const val CONNECT_TIMEOUT_MS = 10000
    }

    fun clearTrackCache(url: String) {
        val playlistId = extractPlaylistId(url)
        if (playlistId != null) {
            trackCache.remove(playlistId)
            Log.d(TAG, "Cleared track cache for playlist: $playlistId")
        }
    }

    suspend fun prefetchInstances() {
        getActiveInstances()
    }

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

    /**
     * Determines if an error is transient (worth retrying) vs permanent.
     */
    private fun isTransientError(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is org.jsoup.HttpStatusException -> e.statusCode in 500..599
            else -> e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true
        }
    }

    /**
     * Executes an HTTP request with exponential backoff retry on transient errors.
     * Returns null if all retries are exhausted.
     */
    private suspend fun <T> executeWithRetry(
        instance: String,
        operation: () -> T
    ): T? {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    val backoffMs = INITIAL_BACKOFF_MS * (1L shl (attempt - 1)) // 500ms, 1000ms
                    Log.d(TAG, "Retry $attempt for $instance after ${backoffMs}ms backoff")
                    delay(backoffMs)
                }
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (!isTransientError(e)) {
                    Log.w(TAG, "Permanent error for $instance: ${e.message}")
                    break // Don't retry on permanent errors (4xx etc.)
                }
                Log.w(TAG, "Transient error for $instance (attempt ${attempt + 1}/${MAX_RETRIES + 1}): ${e.message}")
            }
        }
        return null
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

    suspend fun fetchTracks(url: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching tracks for: $url")
            val playlistId = extractPlaylistId(url)
            if (playlistId == null) {
                return@withContext Result.failure(Exception("Ungültige URL"))
            }

            val cached = trackCache.get(playlistId)
            if (cached != null) {
                Log.d(TAG, "Returning cached tracks for: $playlistId")
                return@withContext Result.success(cached)
            }

            val instancesToTry = getActiveInstances()
            for (instance in instancesToTry) {
                val result = executeWithRetry(instance) {
                    val apiUrl = "$instance/api/v1/playlists/$playlistId"
                    val response = Jsoup.connect(apiUrl)
                        .ignoreContentType(true)
                        .userAgent("Mozilla/5.0")
                        .timeout(CONNECT_TIMEOUT_MS)
                        .execute()
                    val json = JSONObject(response.body())
                    val videosArray = json.optJSONArray("videos")
                    if (videosArray != null && videosArray.length() > 0) {
                        val tracks = mutableListOf<Track>()
                        for (i in 0 until videosArray.length()) {
                            val v = videosArray.optJSONObject(i)
                            if (v != null) {
                                val title = v.optString("title", "")
                                val author = v.optString("author", "")
                                val videoId = v.optString("videoId", "")
                                if (title.isNotEmpty() && videoId.isNotEmpty()) {
                                    tracks.add(Track(title, author, videoId))
                                }
                            }
                        }
                        tracks.ifEmpty { null }
                    } else null
                }
                if (result != null) {
                    trackCache.put(playlistId, result)
                    return@withContext Result.success(result)
                }
            }
            Result.failure(Exception("Keine Tracks gefunden."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Strategy 1: Invidious API
    // ──────────────────────────────────────────────────────────────

    private suspend fun fetchFromInvidious(playlistId: String): MetadataResult? {
        val instancesToTry = getActiveInstances()
        for (instance in instancesToTry) {
            val result = executeWithRetry(instance) {
                val apiUrl = "$instance/api/v1/playlists/$playlistId"
                Log.d(TAG, "Trying Invidious: $apiUrl")

                val response = Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .timeout(CONNECT_TIMEOUT_MS)
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

                    MetadataResult(title, bestThumb, trackCount, duration)
                } else null
            }
            if (result != null) return result
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────
    // Strategy 2: YouTube oEmbed API
    // ──────────────────────────────────────────────────────────────

    private suspend fun fetchFromOEmbed(url: String): MetadataResult? {
        return executeWithRetry("oEmbed") {
            // oEmbed only works with www.youtube.com URLs
            val normalizedUrl = url.replace("music.youtube.com", "www.youtube.com")
            val encodedUrl = URLEncoder.encode(normalizedUrl, "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

            val response = Jsoup.connect(oEmbedUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(CONNECT_TIMEOUT_MS)
                .execute()

            val json = JSONObject(response.body())
            val title = json.optString("title", "")
            val thumbnailUrl = json.optString("thumbnail_url", "")
            val author = json.optString("author_name", "")

            if (title.isNotEmpty()) {
                val duration = if (author.isNotEmpty()) author else null
                MetadataResult(title, upgradeImageResolution(thumbnailUrl), null, duration)
            } else null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Strategy 3: YouTube RSS Feed
    // ──────────────────────────────────────────────────────────────

    private suspend fun fetchFromRssFeed(playlistId: String): MetadataResult? {
        return executeWithRetry("RSS") {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId"
            Log.d(TAG, "Trying RSS: $rssUrl")

            val doc = Jsoup.connect(rssUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(CONNECT_TIMEOUT_MS)
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
                MetadataResult(title, upgradeImageResolution(thumbnailUrl), trackCount, null)
            } else null
        }
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
