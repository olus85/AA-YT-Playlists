package app.olus.ytmusic.autolauncher.data.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

private const val TAG = "JellyfinRepository"
private const val PREFS_NAME = "jellyfin_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_USER_ID = "user_id"
private const val KEY_USERNAME = "username"

/**
 * Represents a Jellyfin media item (album, playlist, or track).
 */
data class JellyfinItem(
    val id: String,
    val name: String,
    val type: String, // "MusicAlbum", "Playlist", "Audio"
    val artist: String = "",
    val imageTag: String? = null
)

/**
 * Repository for Jellyfin server interactions.
 * Handles authentication, browsing albums/playlists, and track retrieval.
 */
class JellyfinRepository(private val context: Context) {

    private val clientName = "AA-YT-Playlists"
    private val deviceName = "Android"
    private val deviceId = "AA-YT-Playlists-${android.os.Build.MODEL}"
    private val clientVersion = "1.0"

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to normal prefs", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // ─── Connection Properties ──────────────────────────────────────────

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trimEnd('/')).apply()

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    val isConfigured: Boolean
        get() = serverUrl.isNotEmpty() && accessToken.isNotEmpty() && userId.isNotEmpty()

    // ─── Authentication ─────────────────────────────────────────────────

    /**
     * Authenticates with the Jellyfin server using username and password.
     * Stores the access token and user ID on success.
     * @return true on success, false on failure.
     */
    suspend fun authenticate(server: String, user: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${server.trimEnd('/')}/Users/AuthenticateByName"
            val body = JSONObject().apply {
                put("Username", user)
                put("Pw", password)
            }

            val authHeader = buildAuthHeader(null)

            val response = Jsoup.connect(url)
                .ignoreContentType(true)
                .method(org.jsoup.Connection.Method.POST)
                .header("Content-Type", "application/json")
                .header("X-Emby-Authorization", authHeader)
                .requestBody(body.toString())
                .timeout(10000)
                .execute()

            if (response.statusCode() != 200) {
                Log.e(TAG, "Auth failed: HTTP ${response.statusCode()}")
                return@withContext Result.failure(Exception("Authentifizierung fehlgeschlagen (HTTP ${response.statusCode()})"))
            }

            val json = JSONObject(response.body())
            val token = json.optString("AccessToken", "")
            val uid = json.optJSONObject("User")?.optString("Id", "") ?: ""

            if (token.isEmpty() || uid.isEmpty()) {
                return@withContext Result.failure(Exception("Ungültige Server-Antwort"))
            }

            serverUrl = server.trimEnd('/')
            accessToken = token
            userId = uid
            username = user

            Log.d(TAG, "Authenticated as $user (userId=$uid)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            Result.failure(Exception("Verbindung fehlgeschlagen: ${e.message}"))
        }
    }

    /**
     * Tests the current connection by making a simple API call.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        try {
            val url = "$serverUrl/System/Info/Public"
            val response = Jsoup.connect(url)
                .ignoreContentType(true)
                .timeout(5000)
                .execute()
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    fun disconnect() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    // ─── Browsing ───────────────────────────────────────────────────────

    /**
     * Fetches music albums and playlists from the Jellyfin server.
     */
    suspend fun getMusicItems(): List<JellyfinItem> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()

        try {
            val items = mutableListOf<JellyfinItem>()

            // Fetch music albums
            val albumsUrl = "$serverUrl/Users/$userId/Items?" +
                "IncludeItemTypes=MusicAlbum&Recursive=true&SortBy=SortName&SortOrder=Ascending&Limit=100"
            items.addAll(fetchItems(albumsUrl))

            // Fetch playlists
            val playlistsUrl = "$serverUrl/Users/$userId/Items?" +
                "IncludeItemTypes=Playlist&Recursive=true&SortBy=SortName&SortOrder=Ascending&Limit=100"
            items.addAll(fetchItems(playlistsUrl))

            Log.d(TAG, "Fetched ${items.size} music items from Jellyfin")
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching music items", e)
            emptyList()
        }
    }

    /**
     * Fetches tracks for a specific album/playlist.
     */
    suspend fun getPlaylistTracks(itemId: String): List<JellyfinItem> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()

        try {
            val url = "$serverUrl/Users/$userId/Items?" +
                "ParentId=$itemId&SortBy=SortName&SortOrder=Ascending"
            val tracks = fetchItems(url)
            Log.d(TAG, "Fetched ${tracks.size} tracks for item $itemId")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tracks for $itemId", e)
            emptyList()
        }
    }

    private fun fetchItems(url: String): List<JellyfinItem> {
        val response = Jsoup.connect(url)
            .ignoreContentType(true)
            .header("X-Emby-Authorization", buildAuthHeader(accessToken))
            .timeout(10000)
            .execute()

        if (response.statusCode() != 200) {
            Log.w(TAG, "fetchItems HTTP ${response.statusCode()}")
            return emptyList()
        }

        val json = JSONObject(response.body())
        val itemsArray = json.optJSONArray("Items") ?: return emptyList()
        val result = mutableListOf<JellyfinItem>()

        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            val id = item.optString("Id", "")
            val name = item.optString("Name", "")
            val type = item.optString("Type", "")
            val artist = item.optString("AlbumArtist", "")
                .ifEmpty { item.optJSONArray("Artists")?.optString(0) ?: "" }
            val imageTags = item.optJSONObject("ImageTags")
            val imageTag = imageTags?.optString("Primary")

            if (id.isNotEmpty() && name.isNotEmpty()) {
                result.add(JellyfinItem(id, name, type, artist, imageTag))
            }
        }
        return result
    }

    // ─── URL Builders ───────────────────────────────────────────────────

    /**
     * Returns the image URL for a Jellyfin item.
     */
    fun getImageUrl(itemId: String): String? {
        if (!isConfigured) return null
        return "$serverUrl/Items/$itemId/Images/Primary?maxWidth=500&quality=90&api_key=$accessToken"
    }

    /**
     * Builds the direct audio streaming URL for a specific track, including auth parameters.
     */
    fun getAudioStreamUrl(itemId: String): String {
        if (!isConfigured) return ""
        return "$serverUrl/Audio/$itemId/stream?static=true&UserId=$userId&api_key=$accessToken"
    }

    // ─── Private Helpers ────────────────────────────────────────────────

    private fun buildAuthHeader(token: String?): String {
        val sb = StringBuilder("MediaBrowser ")
        sb.append("Client=\"$clientName\"")
        sb.append(", Device=\"$deviceName\"")
        sb.append(", DeviceId=\"$deviceId\"")
        sb.append(", Version=\"$clientVersion\"")
        if (!token.isNullOrEmpty()) {
            sb.append(", Token=\"$token\"")
        }
        return sb.toString()
    }
}
