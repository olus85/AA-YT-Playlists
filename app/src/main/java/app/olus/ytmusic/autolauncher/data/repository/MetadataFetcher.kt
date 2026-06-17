package app.olus.ytmusic.autolauncher.data.repository

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.ConnectException
import java.net.UnknownHostException

import app.olus.ytmusic.autolauncher.domain.model.Track

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
    private val instancesMutex = Mutex()

    // In-memory cache for tracks (thread-safe via synchronized map)
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

    private suspend fun getActiveInstances(): List<String> = instancesMutex.withLock {
        if (dynamicInstances.isNotEmpty() && System.currentTimeMillis() - lastFetchTime < 3600000) {
            return@withLock dynamicInstances
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
            is ConnectException -> true
            is UnknownHostException -> true
            is org.jsoup.HttpStatusException -> e.statusCode in 429..499 || e.statusCode in 500..599
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

            // Strategy 1.5: HTML Scraping Fallback
            val htmlResult = fetchMetadataFromHtml(playlistId)
            if (htmlResult != null) {
                Log.d(TAG, "HTML metadata success: ${htmlResult.title}, ${htmlResult.trackCount}")
                return@withContext Result.success(htmlResult)
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

            val instancesToTry = getActiveInstances().take(2)
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

            // Fallback to HTML scraping before RSS
            val htmlResult = fetchTracksFromHtml(playlistId)
            if (htmlResult != null) {
                trackCache.put(playlistId, htmlResult)
                return@withContext Result.success(htmlResult)
            }

            // Fallback to RSS feed if Invidious yields no tracks
            val rssResult = fetchTracksFromRssFeed(playlistId)
            if (rssResult != null) {
                trackCache.put(playlistId, rssResult)
                return@withContext Result.success(rssResult)
            }

            Result.failure(Exception("Keine Tracks gefunden."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchTracksFromHtml(playlistId: String): List<Track>? {
        return executeWithRetry("HTML_TRACKS") {
            val url = "https://www.youtube.com/playlist?list=$playlistId"
            Log.d(TAG, "Trying HTML scraping for tracks: $playlistId")

            val html = fetchHtmlWithConnection(url) ?: return@executeWithRetry null
            val doc = Jsoup.parse(html)
            Log.d(TAG, "HTML Page Title: '${doc.title()}'")
            var jsonStr: String? = null
            
            val scriptElements = doc.select("script")
            Log.d(TAG, "HTML script elements count: ${scriptElements.size}")
            for (script in scriptElements) {
                val data = script.html()
                if (data.contains("ytInitialData")) {
                    Log.d(TAG, "Found ytInitialData script of length ${data.length}")
                    jsonStr = extractJsonFromScript(data, "ytInitialData")
                    if (jsonStr != null) {
                        Log.d(TAG, "Successfully extracted JSON of length ${jsonStr.length}")
                        break
                    } else {
                        Log.w(TAG, "extractJsonFromScript returned null")
                    }
                }
            }

            if (jsonStr == null) {
                Log.w(TAG, "ytInitialData not found in HTML")
                return@executeWithRetry null
            }

            val json = JSONObject(jsonStr)
            val tracks = mutableListOf<Track>()

            // 1. Try walking the standard path for items
            val contents = json.optJSONObject("contents")
            val twoColumnBrowseResultsRenderer = contents?.optJSONObject("twoColumnBrowseResultsRenderer")
            val tabs = twoColumnBrowseResultsRenderer?.optJSONArray("tabs")
            val firstTab = tabs?.optJSONObject(0)
            val tabRenderer = firstTab?.optJSONObject("tabRenderer")
            val tabContent = tabRenderer?.optJSONObject("content")
            val sectionListRenderer = tabContent?.optJSONObject("sectionListRenderer")
            val sectionContents = sectionListRenderer?.optJSONArray("contents")
            val firstSectionContent = sectionContents?.optJSONObject(0)
            val itemSectionRenderer = firstSectionContent?.optJSONObject("itemSectionRenderer")
            val itemContents = itemSectionRenderer?.optJSONArray("contents")

            if (itemContents != null && itemContents.length() > 0) {
                for (i in 0 until itemContents.length()) {
                    val item = itemContents.optJSONObject(i) ?: continue
                    
                    // Try parsing new layout: lockupViewModel
                    val lockup = item.optJSONObject("lockupViewModel")
                    if (lockup != null) {
                        val videoId = lockup.optString("contentId")
                        val meta = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                        val title = meta?.optJSONObject("title")?.optString("content", "") ?: ""
                        var artist = ""
                        val rows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                        if (rows != null && rows.length() > 0) {
                            val parts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                            if (parts != null && parts.length() > 0) {
                                artist = parts.optJSONObject(0)?.optJSONObject("text")?.optString("content", "") ?: ""
                            }
                        }
                        if (videoId.isNotEmpty() && title.isNotEmpty()) {
                            tracks.add(Track(title, artist, videoId))
                        }
                        continue
                    }
                    
                    // Try parsing old layout: playlistVideoRenderer
                    val playlistVideoRenderer = item.optJSONObject("playlistVideoRenderer")
                    if (playlistVideoRenderer != null) {
                        val videoId = playlistVideoRenderer.optString("videoId")
                        var title = ""
                        val titleObj = playlistVideoRenderer.optJSONObject("title")
                        val titleRuns = titleObj?.optJSONArray("runs")
                        if (titleRuns != null && titleRuns.length() > 0) {
                            title = titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                        } else {
                            title = titleObj?.optString("simpleText", "") ?: ""
                        }
                        
                        var artist = ""
                        val bylineObj = playlistVideoRenderer.optJSONObject("shortBylineText")
                        val bylineRuns = bylineObj?.optJSONArray("runs")
                        if (bylineRuns != null && bylineRuns.length() > 0) {
                            artist = bylineRuns.optJSONObject(0)?.optString("text", "") ?: ""
                        } else {
                            artist = bylineObj?.optString("simpleText", "") ?: ""
                        }

                        if (videoId.isNotEmpty() && title.isNotEmpty()) {
                            tracks.add(Track(title, artist, videoId))
                        }
                    }
                }
            }

            // Fallback 1: Deep search for playlistVideoListRenderer
            if (tracks.isEmpty()) {
                val listRenderer = findKeyRecursively(json, "playlistVideoListRenderer") as? JSONObject
                val videoContents = listRenderer?.optJSONArray("contents")
                if (videoContents != null) {
                    for (i in 0 until videoContents.length()) {
                        val videoItem = videoContents.optJSONObject(i)
                        val playlistVideoRenderer = videoItem?.optJSONObject("playlistVideoRenderer")
                        if (playlistVideoRenderer != null) {
                            val videoId = playlistVideoRenderer.optString("videoId")
                            val titleObj = playlistVideoRenderer.optJSONObject("title")
                            val titleRuns = titleObj?.optJSONArray("runs")
                            val title = if (titleRuns != null && titleRuns.length() > 0) {
                                titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                            } else {
                                titleObj?.optString("simpleText", "") ?: ""
                            }
                            
                            val bylineObj = playlistVideoRenderer.optJSONObject("shortBylineText")
                            val bylineRuns = bylineObj?.optJSONArray("runs")
                            val artist = if (bylineRuns != null && bylineRuns.length() > 0) {
                                bylineRuns.optJSONObject(0)?.optString("text", "") ?: ""
                            } else {
                                bylineObj?.optString("simpleText", "") ?: ""
                            }

                            if (videoId.isNotEmpty() && title.isNotEmpty()) {
                                tracks.add(Track(title, artist, videoId))
                            }
                        }
                    }
                }
            }

            // Fallback 2: Deep search for all lockupViewModels
            if (tracks.isEmpty()) {
                val allLockups = findAllKeysRecursively(json, "lockupViewModel")
                for (lockupObj in allLockups) {
                    if (lockupObj is JSONObject) {
                        val videoId = lockupObj.optString("contentId")
                        val meta = lockupObj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                        val title = meta?.optJSONObject("title")?.optString("content", "") ?: ""
                        var artist = ""
                        val rows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                        if (rows != null && rows.length() > 0) {
                            val parts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                            if (parts != null && parts.length() > 0) {
                                artist = parts.optJSONObject(0)?.optJSONObject("text")?.optString("content", "") ?: ""
                            }
                        }
                        if (videoId.isNotEmpty() && title.isNotEmpty()) {
                            val t = Track(title, artist, videoId)
                            if (!tracks.any { it.videoId == videoId }) {
                                tracks.add(t)
                            }
                        }
                    }
                }
            }

            if (tracks.isNotEmpty()) tracks else null
        }
    }

    private suspend fun fetchMetadataFromHtml(playlistId: String): MetadataResult? {
        return executeWithRetry("HTML_METADATA") {
            val url = "https://www.youtube.com/playlist?list=$playlistId"
            Log.d(TAG, "Trying HTML scraping for metadata: $playlistId")

            val html = fetchHtmlWithConnection(url) ?: return@executeWithRetry null
            val doc = Jsoup.parse(html)
            var jsonStr: String? = null
            
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val data = script.html()
                if (data.contains("ytInitialData")) {
                    jsonStr = extractJsonFromScript(data, "ytInitialData")
                    if (jsonStr != null) break
                }
            }

            if (jsonStr == null) {
                Log.w(TAG, "ytInitialData script not found in HTML metadata")
                return@executeWithRetry null
            }

            val json = JSONObject(jsonStr)

            // Extract title and thumbnail from microformat
            val microformat = json.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
            val title = microformat?.optString("title", "") ?: ""
            
            val thumbnails = microformat?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            var imageUrl = ""
            if (thumbnails != null && thumbnails.length() > 0) {
                imageUrl = thumbnails.optJSONObject(0)?.optString("url", "") ?: ""
            }

            // Extract trackCount
            var trackCount: String? = null
            
            val sidebar = json.optJSONObject("sidebar")?.optJSONObject("playlistSidebarRenderer")
            val sidebarItems = sidebar?.optJSONArray("items")
            if (sidebarItems != null && sidebarItems.length() > 0) {
                val primaryInfo = sidebarItems.optJSONObject(0)?.optJSONObject("playlistSidebarPrimaryInfoRenderer")
                val stats = primaryInfo?.optJSONArray("stats")
                if (stats != null && stats.length() > 0) {
                    val countText = stats.optJSONObject(0)?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    if (countText != null) {
                        trackCount = "$countText Songs"
                    }
                }
            }
            
            if (trackCount == null) {
                val header = json.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")
                val contentVM = header?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
                val rows = contentVM?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                if (rows != null && rows.length() > 1) {
                    val parts = rows.optJSONObject(1)?.optJSONArray("metadataParts")
                    if (parts != null && parts.length() > 0) {
                        val countText = parts.optJSONObject(parts.length() - 1)?.optJSONObject("text")?.optString("content")
                        if (countText != null) {
                            trackCount = countText
                        }
                    }
                }
            }

            // Extract author
            var author: String? = null
            if (sidebarItems != null && sidebarItems.length() > 1) {
                val secondaryInfo = sidebarItems.optJSONObject(1)?.optJSONObject("playlistSidebarSecondaryInfoRenderer")
                val owner = secondaryInfo?.optJSONObject("videoOwner")?.optJSONObject("videoOwnerRenderer")
                val ownerRuns = owner?.optJSONObject("title")?.optJSONArray("runs")
                if (ownerRuns != null && ownerRuns.length() > 0) {
                    author = ownerRuns.optJSONObject(0)?.optString("text")
                }
            }

            if (author == null) {
                val header = json.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")
                val contentVM = header?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
                val rows = contentVM?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                if (rows != null && rows.length() > 0) {
                    val parts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                    if (parts != null && parts.length() > 0) {
                        author = parts.optJSONObject(0)?.optJSONObject("avatarStack")?.optJSONObject("avatarStackViewModel")?.optJSONObject("text")?.optString("content")
                        if (author != null && author.startsWith("von ")) {
                            author = author.substring(4)
                        }
                    }
                }
            }

            if (title.isNotEmpty()) {
                val bestThumb = upgradeImageResolution(imageUrl)
                MetadataResult(title, bestThumb, trackCount, author)
            } else null
        }
    }

    private fun fetchHtmlWithConnection(urlStr: String): String? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL(urlStr)
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = CONNECT_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HTML via HttpURLConnection for $urlStr", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractJsonFromScript(scriptData: String, startKey: String): String? {
        val index = scriptData.indexOf(startKey)
        if (index == -1) return null
        val braceIndex = scriptData.indexOf('{', index)
        if (braceIndex == -1) return null
        
        var openBrackets = 0
        var inString = false
        var stringChar = '"'
        var escape = false
        
        for (i in braceIndex until scriptData.length) {
            val c = scriptData[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true
                    stringChar = c
                } else if (c == stringChar) {
                    inString = false
                }
                continue
            }
            if (!inString) {
                if (c == '{') {
                    openBrackets++
                } else if (c == '}') {
                    openBrackets--
                    if (openBrackets == 0) {
                        return scriptData.substring(braceIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun findKeyRecursively(json: Any, key: String): Any? {
        if (json is JSONObject) {
            if (json.has(key)) {
                return json.get(key)
            }
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val value = json.get(k)
                val found = findKeyRecursively(value, key)
                if (found != null) return found
            }
        } else if (json is org.json.JSONArray) {
            for (i in 0 until json.length()) {
                val value = json.get(i)
                val found = findKeyRecursively(value, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findAllKeysRecursively(json: Any, key: String, results: MutableList<Any> = mutableListOf()): List<Any> {
        if (json is JSONObject) {
            if (json.has(key)) {
                results.add(json.get(key))
            }
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val value = json.get(k)
                findAllKeysRecursively(value, key, results)
            }
        } else if (json is org.json.JSONArray) {
            for (i in 0 until json.length()) {
                val value = json.get(i)
                findAllKeysRecursively(value, key, results)
            }
        }
        return results
    }

    private suspend fun fetchTracksFromRssFeed(playlistId: String): List<Track>? {
        return executeWithRetry("RSS_TRACKS") {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId"
            Log.d(TAG, "Trying RSS for tracks: $rssUrl")

            val doc = Jsoup.connect(rssUrl)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .timeout(CONNECT_TIMEOUT_MS)
                .get()

            val entries = doc.select("entry")
            if (entries.isEmpty()) return@executeWithRetry null

            val tracks = mutableListOf<Track>()
            for (entry in entries) {
                val title = entry.select("title").text()
                val author = entry.select("author > name").text()
                val videoId = entry.select("yt|videoId").text()
                if (title.isNotEmpty() && videoId.isNotEmpty()) {
                    tracks.add(Track(title, author, videoId))
                }
            }
            if (tracks.isNotEmpty()) tracks else null
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

    // ──────────────────────────────────────────────────────────────
    // Voice Search: Find a single track by query
    // ──────────────────────────────────────────────────────────────

    /**
     * Searches for a single track via Invidious search API with fuzzy matching.
     * Returns the first matching video result, or null if nothing found.
     * Used for Google Assistant "Hey Google, play X on YT Playlists" integration.
     * Handles common speech-to-text errors by trying multiple query variations.
     */
    suspend fun searchTrack(query: String): Track? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for track: $query")
            val queriesToTry = buildSearchQueries(query)
            val instancesToTry = getActiveInstances()

            for (queryVariant in queriesToTry) {
                for (instance in instancesToTry) {
                    val result = executeWithRetry(instance) {
                        val encodedQuery = URLEncoder.encode(queryVariant, "UTF-8")
                        val apiUrl = "$instance/api/v1/search?q=$encodedQuery&type=video&sort_by=relevance"
                        Log.d(TAG, "Trying search: $apiUrl")

                        val response = Jsoup.connect(apiUrl)
                            .ignoreContentType(true)
                            .userAgent("Mozilla/5.0")
                            .timeout(CONNECT_TIMEOUT_MS)
                            .execute()

                        val jsonArray = org.json.JSONArray(response.body())
                        if (jsonArray.length() > 0) {
                            val first = jsonArray.getJSONObject(0)
                            val videoId = first.optString("videoId", "")
                            val title = first.optString("title", "")
                            val author = first.optString("author", "")
                            if (videoId.isNotEmpty() && title.isNotEmpty()) {
                                Track(title, author, videoId)
                            } else null
                        } else null
                    }
                    if (result != null) return@withContext result
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            null
        }
    }

    /**
     * Searches for multiple tracks via Invidious search API.
     * Returns up to `limit` matching video results.
     */
    suspend fun searchTracks(query: String, limit: Int = 15): List<Track> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for tracks: $query")
            val queriesToTry = buildSearchQueries(query)
            val instancesToTry = getActiveInstances()

            for (queryVariant in queriesToTry) {
                for (instance in instancesToTry) {
                    val result = executeWithRetry(instance) {
                        val encodedQuery = URLEncoder.encode(queryVariant, "UTF-8")
                        val apiUrl = "$instance/api/v1/search?q=$encodedQuery&type=video&sort_by=relevance"
                        Log.d(TAG, "Trying search: $apiUrl")

                        val response = Jsoup.connect(apiUrl)
                            .ignoreContentType(true)
                            .userAgent("Mozilla/5.0")
                            .timeout(CONNECT_TIMEOUT_MS)
                            .execute()

                        val jsonArray = org.json.JSONArray(response.body())
                        val tracks = mutableListOf<Track>()
                        val count = minOf(jsonArray.length(), limit)
                        for (i in 0 until count) {
                            val obj = jsonArray.getJSONObject(i)
                            val videoId = obj.optString("videoId", "")
                            val title = obj.optString("title", "")
                            val author = obj.optString("author", "")
                            if (videoId.isNotEmpty() && title.isNotEmpty()) {
                                tracks.add(Track(title, author, videoId))
                            }
                        }
                        tracks
                    }
                    if (!result.isNullOrEmpty()) return@withContext result
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search tracks failed", e)
            emptyList()
        }
    }

    /**
     * Builds multiple query variations to handle speech-to-text inaccuracies.
     * e.g., "ich habe dich" → ["ich habe dich", "ich hab dich", "habe dich", "hab dich"]
     */
    private fun buildSearchQueries(original: String): List<String> {
        val queries = mutableListOf(original)
        val lower = original.lowercase()
        
        val commonReplacements = listOf(
            "habe" to "hab",
            " habe" to " hab",
            "hast" to "has",
            " hat" to " ",
            "nicht" to "nich",
            " dich" to " dich",
            " mir" to " ",
            " ich" to " ",
        )
        
        var variant = lower
        for ((from, to) in commonReplacements) {
            if (variant.contains(from)) {
                val newVariant = variant.replace(from, to).trim()
                if (newVariant.isNotEmpty() && !queries.contains(newVariant)) {
                    queries.add(newVariant)
                }
            }
        }
        
        val words = lower.split("\\s+".toRegex())
        if (words.size > 2) {
            val withoutArticles = words.filter { it !in listOf("der", "die", "das", "ein", "eine", "und", "oder", "auf", "zu", "mit") }
            if (withoutArticles.size < words.size) {
                val withoutArticlesQuery = withoutArticles.joinToString(" ")
                if (!queries.contains(withoutArticlesQuery)) {
                    queries.add(withoutArticlesQuery)
                }
            }
        }
        
        return queries.distinct().take(5)
    }
}
