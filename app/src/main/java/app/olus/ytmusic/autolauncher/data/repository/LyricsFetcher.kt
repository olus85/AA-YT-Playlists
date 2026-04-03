package app.olus.ytmusic.autolauncher.data.repository

import app.olus.ytmusic.autolauncher.util.AALogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import app.olus.ytmusic.autolauncher.data.local.entity.LyricsEntity

data class LyricLine(val timestampMs: Long, val text: String)

sealed class LyricsState {
    object Loading : LyricsState()
    object Empty : LyricsState()
    data class Success(val lyrics: List<LyricLine>, val isSynced: Boolean = true) : LyricsState()
    data class Error(val message: String) : LyricsState()
}



@Singleton
class LyricsFetcher @Inject constructor(
    private val lyricsDao: app.olus.ytmusic.autolauncher.data.local.dao.LyricsDao
) {

    private val baseApiUrl = "https://lrclib.net/api/get"
    private val searchApiUrl = "https://lrclib.net/api/search"
    private val TAG = "LyricsFetcher"

    companion object {
        // Genius API credentials
        const val GENIUS_ACCESS_TOKEN = "e2IjjnIVsdoFj5k3wb5A8US_r6sPdgM9QVbW9P5Rz2I85Rp7ic_FE4yCiERkcCgm"
    }

    /**
     * Main entry point. Fallback chain:
     * Cache -> NetEase -> lrclib/get → lrclib/search → Megalobiz → Genius (plain-text)
     */
    suspend fun fetchLyrics(trackName: String, artistName: String, durationMs: Long?): LyricsState = withContext(Dispatchers.IO) {
        try {
            val cleanedTrackName = cleanTrackTitle(trackName)
            val cleanedArtistName = cleanArtistName(artistName)
            val cacheId = "$cleanedArtistName-$cleanedTrackName"

            AALogger.log(TAG, "Fetching lyrics for '$cleanedTrackName' by '$cleanedArtistName' (${durationMs?.div(1000)}s)")

            // ── Step 0: Check Local Cache ──
            try {
                val cached = lyricsDao.getLyrics(cacheId)
                if (cached != null) {
                    AALogger.forceLog(TAG, "Cache hit for '$cacheId'!")
                    val parsed = parseLrc(cached.lyricsContent)
                    if (parsed.isNotEmpty()) {
                        return@withContext LyricsState.Success(parsed, isSynced = cached.isSynced)
                    }
                }
            } catch (e: Exception) {
                AALogger.logError(TAG, "Cache read failed", e)
            }

            // ── Step 1: NetEase Cloud Music ──
            AALogger.log(TAG, "Trying NetEase...")
            var finalResult: LyricsState.Success? = null
            
            val netEaseResult = fetchFromNetEase(cleanedTrackName, cleanedArtistName)
            if (netEaseResult is LyricsState.Success) {
                finalResult = netEaseResult
            } else {
                // ── Step 2: lrclib exact GET ──
                AALogger.log(TAG, "NetEase miss. Trying lrclib/get...")
                val lrclibGetResult = fetchFromLrclibGet(cleanedTrackName, cleanedArtistName, durationMs)
                if (lrclibGetResult is LyricsState.Success) {
                    finalResult = lrclibGetResult
                } else {
                    // ── Step 3: lrclib search (fuzzy) ──
                    AALogger.log(TAG, "lrclib/get miss. Trying lrclib/search...")
                    val lrclibSearchResult = fetchFromLrclibSearch(cleanedTrackName, cleanedArtistName)
                    if (lrclibSearchResult is LyricsState.Success) {
                        finalResult = lrclibSearchResult
                    } else {
                        // ── Step 4: Megalobiz ──
                        AALogger.log(TAG, "lrclib/search miss. Trying Megalobiz...")
                        val megalobizResult = fetchFromMegalobiz(cleanedTrackName, cleanedArtistName)
                        if (megalobizResult is LyricsState.Success) {
                            finalResult = megalobizResult
                        } else {
                            // ── Step 5: Genius (Plain-text fallback) ──
                            AALogger.log(TAG, "Megalobiz miss. Fallback to Genius...")
                            val geniusResult = fetchFromGenius(cleanedTrackName, cleanedArtistName)
                            if (geniusResult is LyricsState.Success) {
                                finalResult = geniusResult
                            } else {
                                return@withContext LyricsState.Empty
                            }
                        }
                    }
                }
            }
            
            // Reconstruct LRC string from parsed LyricLines and save to cache if it's synced
            val result = finalResult
            if (result != null && result.isSynced) {
                val lrcString = result.lyrics.joinToString("\n") { line ->
                    val ms = line.timestampMs
                    val m = ms / 60000
                    val s = (ms % 60000) / 1000
                    val msPart = (ms % 1000) / 10
                    String.format("[%02d:%02d.%02d]%s", m, s, msPart, line.text)
                }
                
                try {
                    lyricsDao.insertLyrics(LyricsEntity(
                        id = cacheId,
                        trackName = cleanedTrackName,
                        artistName = cleanedArtistName,
                        lyricsContent = lrcString,
                        isSynced = true
                    ))
                    AALogger.log(TAG, "Saved lyrics to cache for '$cacheId'")
                } catch (e: Exception) {
                    AALogger.logError(TAG, "Failed to save lyrics to cache", e)
                }
            }
            
            return@withContext finalResult ?: LyricsState.Empty

        } catch (e: Exception) {
            AALogger.logError(TAG, "Exception in top-level fetchLyrics", e)
            LyricsState.Empty
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Provider 1: lrclib exact GET
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromLrclibGet(trackName: String, artistName: String, durationMs: Long?): LyricsState? {
        return try {
            val queryBuilder = StringBuilder()
            queryBuilder.append("?track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
            queryBuilder.append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
            if (durationMs != null && durationMs > 0) {
                queryBuilder.append("&duration=").append(durationMs / 1000)
            }

            val targetUrl = URL(baseApiUrl + queryBuilder.toString())
            val connection = targetUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AA-YT-Playlists-App")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseText = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(responseText)
            if (json.has("syncedLyrics") && !json.isNull("syncedLyrics")) {
                val syncedLrc = json.getString("syncedLyrics")
                val parsed = parseLrc(syncedLrc)
                if (parsed.isNotEmpty()) {
                    AALogger.log(TAG, "Successfully fetched synced lyrics from lrclib/get")
                    return LyricsState.Success(parsed, isSynced = true)
                }
            }
            null // No synced lyrics found
        } catch (e: Exception) {
            AALogger.log(TAG, "lrclib/get error: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Provider 2: lrclib search (fuzzy)
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromLrclibSearch(trackName: String, artistName: String): LyricsState? {
        return try {
            val query = URLEncoder.encode("$artistName $trackName", "UTF-8")
            val targetUrl = URL("$searchApiUrl?q=$query")
            val connection = targetUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AA-YT-Playlists-App")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseText = reader.readText()
            reader.close()
            connection.disconnect()

            val jsonArray = JSONArray(responseText)
            if (jsonArray.length() == 0) return null

            // Find the best match that has synced lyrics
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val syncedLyrics = item.optString("syncedLyrics", "")
                if (syncedLyrics.isNotEmpty()) {
                    val parsed = parseLrc(syncedLyrics)
                    if (parsed.isNotEmpty()) {
                        val matchTitle = item.optString("trackName", "")
                        val matchArtist = item.optString("artistName", "")
                        AALogger.log(TAG, "lrclib/search match: '$matchTitle' by '$matchArtist'")
                        return LyricsState.Success(parsed, isSynced = true)
                    }
                }
            }
            null
        } catch (e: Exception) {
            AALogger.log(TAG, "lrclib/search error: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Provider 3: Megalobiz scraper
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromMegalobiz(trackName: String, artistName: String): LyricsState? {
        return try {
            val query = URLEncoder.encode("$artistName $trackName", "UTF-8")
            val searchUrl = "https://www.megalobiz.com/search/all?qry=$query"

            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(8000)
                .get()

            // Find LRC result links
            val resultLinks = doc.select("a.entity_name[href*=/lrc/maker/]")
            if (resultLinks.isEmpty()) {
                AALogger.log(TAG, "Megalobiz: No results found")
                return null
            }

            // Try the first few results
            for (i in 0 until minOf(3, resultLinks.size)) {
                val link = resultLinks[i]
                val href = link.attr("abs:href")
                if (href.isEmpty()) continue

                try {
                    val detailDoc = Jsoup.connect(href)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(8000)
                        .get()

                    // The LRC content is inside a textarea or span with class "lyrics"
                    val lrcContent = detailDoc.select("span.lyrics").text()
                        .ifEmpty { detailDoc.select("textarea#lrc_content").text() }
                        .ifEmpty { detailDoc.select("div.lyrics_details span").text() }

                    if (lrcContent.isNotEmpty() && lrcContent.contains("[")) {
                        val parsed = parseLrc(lrcContent)
                        if (parsed.isNotEmpty()) {
                            AALogger.log(TAG, "Successfully fetched ${parsed.size} synced lines from Megalobiz")
                            return LyricsState.Success(parsed, isSynced = true)
                        }
                    }
                } catch (e: Exception) {
                    AALogger.log(TAG, "Megalobiz detail page error: ${e.message}")
                }
            }
            null
        } catch (e: Exception) {
            AALogger.log(TAG, "Megalobiz scraper error: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Provider 4: NetEase Cloud Music (reverse-engineered)
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromNetEase(trackName: String, artistName: String): LyricsState? {
        return try {
            // Step 1: Search for the song
            val query = URLEncoder.encode("$artistName $trackName", "UTF-8")
            val searchUrl = URL("https://music.163.com/api/search/get/?s=$query&type=1&limit=5")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.requestMethod = "POST"
            searchConn.setRequestProperty("User-Agent", "Mozilla/5.0")
            searchConn.setRequestProperty("Referer", "https://music.163.com/")
            searchConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            searchConn.connectTimeout = 8000
            searchConn.readTimeout = 8000
            searchConn.doOutput = true
            // Send empty body for POST
            searchConn.outputStream.write(ByteArray(0))
            searchConn.outputStream.flush()

            if (searchConn.responseCode != HttpURLConnection.HTTP_OK) {
                AALogger.log(TAG, "NetEase search HTTP ${searchConn.responseCode}")
                searchConn.disconnect()
                return null
            }

            val searchReader = BufferedReader(InputStreamReader(searchConn.inputStream))
            val searchResponse = searchReader.readText()
            searchReader.close()
            searchConn.disconnect()

            val searchJson = JSONObject(searchResponse)
            val songs = searchJson.optJSONObject("result")?.optJSONArray("songs")
            if (songs == null || songs.length() == 0) {
                AALogger.log(TAG, "NetEase: No search results")
                return null
            }

            // Try each song result for synced lyrics
            for (i in 0 until minOf(3, songs.length())) {
                val song = songs.optJSONObject(i) ?: continue
                val songId = song.optLong("id", 0)
                if (songId == 0L) continue

                try {
                    // Step 2: Fetch lyrics for this song
                    val lyricsUrl = URL("https://music.163.com/api/song/lyric?id=$songId&lv=1&tv=-1")
                    val lyricsConn = lyricsUrl.openConnection() as HttpURLConnection
                    lyricsConn.requestMethod = "GET"
                    lyricsConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    lyricsConn.setRequestProperty("Referer", "https://music.163.com/")
                    lyricsConn.connectTimeout = 5000
                    lyricsConn.readTimeout = 5000

                    if (lyricsConn.responseCode != HttpURLConnection.HTTP_OK) {
                        lyricsConn.disconnect()
                        continue
                    }

                    val lyricsReader = BufferedReader(InputStreamReader(lyricsConn.inputStream))
                    val lyricsResponse = lyricsReader.readText()
                    lyricsReader.close()
                    lyricsConn.disconnect()

                    val lyricsJson = JSONObject(lyricsResponse)
                    val lrcText = lyricsJson.optJSONObject("lrc")?.optString("lyric", "")

                    if (!lrcText.isNullOrEmpty() && lrcText.contains("[")) {
                        val parsed = parseLrc(lrcText)
                        if (parsed.isNotEmpty()) {
                            val songName = song.optString("name", "")
                            AALogger.log(TAG, "NetEase: Found synced lyrics for '$songName' (${parsed.size} lines)")
                            return LyricsState.Success(parsed, isSynced = true)
                        }
                    }
                } catch (e: Exception) {
                    AALogger.log(TAG, "NetEase lyrics fetch error for song $songId: ${e.message}")
                }
            }
            null
        } catch (e: Exception) {
            AALogger.log(TAG, "NetEase provider error: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Provider 5: Genius API (plain-text fallback)
    // ──────────────────────────────────────────────────────────────

    private fun fetchFromGenius(trackName: String, artistName: String): LyricsState {
        return try {
            AALogger.log(TAG, "Trying Genius API for '$trackName' by '$artistName'")

            // Step 1: Search for the song on Genius
            val searchQuery = URLEncoder.encode("$artistName $trackName", "UTF-8")
            val searchUrl = URL("https://api.genius.com/search?q=$searchQuery")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.requestMethod = "GET"
            searchConn.setRequestProperty("Authorization", "Bearer $GENIUS_ACCESS_TOKEN")
            searchConn.setRequestProperty("User-Agent", "AA-YT-Playlists-App")
            searchConn.connectTimeout = 8000
            searchConn.readTimeout = 8000

            if (searchConn.responseCode != HttpURLConnection.HTTP_OK) {
                AALogger.log(TAG, "Genius search failed with HTTP ${searchConn.responseCode}")
                searchConn.disconnect()
                return LyricsState.Empty
            }

            val searchReader = BufferedReader(InputStreamReader(searchConn.inputStream))
            val searchResponse = searchReader.readText()
            searchReader.close()
            searchConn.disconnect()

            val searchJson = JSONObject(searchResponse)
            val hits = searchJson.optJSONObject("response")?.optJSONArray("hits")
            if (hits == null || hits.length() == 0) {
                AALogger.log(TAG, "Genius: No results found")
                return LyricsState.Empty
            }

            // Get the URL of the top result
            val topResult = hits.optJSONObject(0)?.optJSONObject("result")
            val geniusUrl = topResult?.optString("url")
            if (geniusUrl.isNullOrEmpty()) {
                AALogger.log(TAG, "Genius: No URL in top result")
                return LyricsState.Empty
            }

            AALogger.log(TAG, "Genius: Scraping lyrics from $geniusUrl")

            // Step 2: Scrape the lyrics page with Jsoup
            val doc = Jsoup.connect(geniusUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()

            // Genius wraps lyrics in div[data-lyrics-container="true"]
            val lyricsContainers = doc.select("div[data-lyrics-container=true]")
            if (lyricsContainers.isEmpty()) {
                AALogger.log(TAG, "Genius: No lyrics container found on page")
                return LyricsState.Empty
            }

            val lyricsBuilder = StringBuilder()
            for (container in lyricsContainers) {
                // Replace <br> with newlines before extracting text
                container.select("br").forEach { it.before("\n") }
                val text = container.wholeText()
                lyricsBuilder.append(text).append("\n")
            }

            val plainText = lyricsBuilder.toString().trim()
            if (plainText.isEmpty()) {
                AALogger.log(TAG, "Genius: Lyrics text is empty after extraction")
                return LyricsState.Empty
            }

            // Convert plain text to LyricLine objects without timestamps (plain-text mode)
            val lines = plainText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("[") } // Remove section headers like [Verse 1]
                .map { LyricLine(timestampMs = 0L, text = it) }

            if (lines.isEmpty()) {
                AALogger.log(TAG, "Genius: No usable lyrics lines")
                return LyricsState.Empty
            }

            AALogger.log(TAG, "Successfully fetched ${lines.size} lines from Genius (plain-text)")
            LyricsState.Success(lines, isSynced = false)

        } catch (e: Exception) {
            AALogger.log(TAG, "Genius fallback failed: ${e.message}")
            LyricsState.Empty
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────

    /**
     * Cleans common YouTube Music artifacts from the title to improve likelihood of matches.
     */
    private fun cleanTrackTitle(title: String): String {
        var clean = title
        // Remove text within brackets and parentheses like (Official Video), [Audio]
        clean = clean.replace(Regex("""\s*\[[^\]]*\]\s*"""), " ")
        clean = clean.replace(Regex("""\s*\([^\)]*(?i:video|audio|lyric|remaster|live|official|version)[^\)]*\)\s*"""), " ")
        clean = clean.replace(Regex("""\s*-.+?(?i:video|audio|lyric|remaster|live|official|version).*"""), "")
        return clean.trim()
    }

    private fun cleanArtistName(artist: String): String {
        // YT Music often formats single artists as "Artist Name - Topic". Let's remove " - Topic"
        return artist.replace(Regex("""\s*-\s*Topic$"""), "").trim()
    }

    /**
     * Parses a standard LRC string into lines with timestamps.
     * Example: [00:15.22] Text...
     */
    private fun parseLrc(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        // Regex to match generic LRC time formats like [mm:ss.xx] or [mm:ss:xx]
        val pattern = Pattern.compile("""\[(\d{2,}):(\d{2})[.:](\d{2,3})\](.*)""")
        
        lrcText.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                try {
                    val min = matcher.group(1)?.toLong() ?: 0L
                    val sec = matcher.group(2)?.toLong() ?: 0L
                    val millisPart = matcher.group(3) ?: "0"
                    
                    // Handle different fractional second digits (usually 2 or 3)
                    val millis = if (millisPart.length == 2) millisPart.toLong() * 10 else millisPart.toLong()
                    
                    val timestamp = (min * 60 * 1000) + (sec * 1000) + millis
                    val text = matcher.group(4)?.trim() ?: ""
                    
                    lines.add(LyricLine(timestamp, text))
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }
        
        // Sort by timestamp just in case
        return lines.sortedBy { it.timestampMs }
    }
}
