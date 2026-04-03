package app.olus.ytmusic.autolauncher.data.repository

import app.olus.ytmusic.autolauncher.util.AALogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class LyricLine(val timestampMs: Long, val text: String)

sealed class LyricsState {
    object Loading : LyricsState()
    object Empty : LyricsState()
    data class Success(val lyrics: List<LyricLine>, val isSynced: Boolean = true) : LyricsState()
    data class Error(val message: String) : LyricsState()
}

@Singleton
class LyricsFetcher @Inject constructor() {

    private val baseApiUrl = "https://lrclib.net/api/get"
    private val TAG = "LyricsFetcher"

    companion object {
        // Genius API credentials
        const val GENIUS_CLIENT_ID = "2o3DcfgPEO3esYG5Zvywv5o7TNh1xhE0nqK0TLOXChHrkC1SEUAeID6VBL0kT3VP"
        const val GENIUS_CLIENT_SECRET = "eymPtpHXby0yVLTpED9pDmTPmXU8e7tRrSvg6EOupOs1fckPTsc6oV0SBX5qlJlSKpeSIPGiRNBietllC0vOBg"
        const val GENIUS_ACCESS_TOKEN = "e2IjjnIVsdoFj5k3wb5A8US_r6sPdgM9QVbW9P5Rz2I85Rp7ic_FE4yCiERkcCgm"
    }

    suspend fun fetchLyrics(trackName: String, artistName: String, durationMs: Long?): LyricsState = withContext(Dispatchers.IO) {
        try {
            val cleanedTrackName = cleanTrackTitle(trackName)
            val cleanedArtistName = cleanArtistName(artistName)

            AALogger.log(TAG, "Fetching lyrics for '$cleanedTrackName' by '$cleanedArtistName' (${durationMs?.div(1000)}s)")

            val queryBuilder = StringBuilder()
            queryBuilder.append("?track_name=").append(URLEncoder.encode(cleanedTrackName, "UTF-8"))
            queryBuilder.append("&artist_name=").append(URLEncoder.encode(cleanedArtistName, "UTF-8"))
            if (durationMs != null && durationMs > 0) {
                queryBuilder.append("&duration=").append(durationMs / 1000)
            }

            val targetUrl = URL(baseApiUrl + queryBuilder.toString())
            val connection = targetUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AA-YT-Playlists-App") // Good practice for lrclib
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                AALogger.log(TAG, "No lyrics found for track (404) on lrclib. Falling back to Musixmatch.")
                return@withContext fetchFromMusixmatch(cleanedTrackName, cleanedArtistName)
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                AALogger.log(TAG, "Failed to fetch lyrics, HTTP $responseCode")
                return@withContext fetchFromMusixmatch(cleanedTrackName, cleanedArtistName)
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
                    return@withContext LyricsState.Success(parsed, isSynced = true)
                }
            }

            // Fallback to Musixmatch if no synced lyrics found
            AALogger.log(TAG, "lrclib missing synced lyrics. Falling back to Musixmatch.")
            fetchFromMusixmatch(cleanedTrackName, cleanedArtistName)

        } catch (e: Exception) {
            AALogger.log(TAG, "Exception fetching lyrics: ${e.message}. Falling back.")
            fetchFromMusixmatch(cleanTrackTitle(trackName), cleanArtistName(artistName))
        }
    }

    private var mxmToken: String? = null

    private suspend fun fetchFromMusixmatch(trackName: String, artistName: String): LyricsState {
        return try {
            if (mxmToken == null) {
                val tokenUrl = URL("https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0")
                val tokenConn = tokenUrl.openConnection() as HttpURLConnection
                tokenConn.requestMethod = "GET"
                tokenConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val tokenReader = BufferedReader(InputStreamReader(tokenConn.inputStream))
                val tokenResponse = tokenReader.readText()
                tokenReader.close()
                tokenConn.disconnect()
                
                val tokenJson = JSONObject(tokenResponse)
                val body = tokenJson.optJSONObject("message")?.optJSONObject("body")
                mxmToken = body?.optString("user_token")
            }

            if (mxmToken.isNullOrEmpty()) {
                AALogger.log(TAG, "Musixmatch token empty. Falling back to Genius.")
                return fetchFromGenius(trackName, artistName)
            }

            val queryBuilder = StringBuilder()
            queryBuilder.append("https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json")
            queryBuilder.append("&q_track=").append(URLEncoder.encode(trackName, "UTF-8"))
            queryBuilder.append("&q_artist=").append(URLEncoder.encode(artistName, "UTF-8"))
            queryBuilder.append("&user_token=").append(mxmToken)
            queryBuilder.append("&app_id=web-desktop-app-v1.0")

            val targetUrl = URL(queryBuilder.toString())
            val connection = targetUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseText = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(responseText)
            val msgBody = json.optJSONObject("message")?.optJSONObject("body")
            val macroCalls = msgBody?.optJSONObject("macro_calls")
            val subsGet = macroCalls?.optJSONObject("track.subtitles.get")
            val subsBody = subsGet?.optJSONObject("message")?.optJSONObject("body")
            val subtitleList = subsBody?.optJSONArray("subtitle_list")
            
            if (subtitleList != null && subtitleList.length() > 0) {
                val subtitleObj = subtitleList.optJSONObject(0)?.optJSONObject("subtitle")
                val lrcText = subtitleObj?.optString("subtitle_body")
                if (!lrcText.isNullOrEmpty()) {
                    val parsed = parseLrc(lrcText)
                    if (parsed.isNotEmpty()) {
                        AALogger.log(TAG, "Successfully fetched from Musixmatch")
                        return LyricsState.Success(parsed, isSynced = true)
                    }
                }
            }
            AALogger.log(TAG, "Musixmatch returned no lyrics. Falling back to Genius.")
            fetchFromGenius(trackName, artistName)
        } catch (e: Exception) {
            AALogger.log(TAG, "Musixmatch fallback failed: ${e.message}. Trying Genius.")
            fetchFromGenius(trackName, artistName)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Genius API Fallback
    // ──────────────────────────────────────────────────────────────

    private suspend fun fetchFromGenius(trackName: String, artistName: String): LyricsState {
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
