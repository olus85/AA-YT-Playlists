package app.olus.ytmusic.autolauncher.data.repository

import android.content.Context
import android.content.SharedPreferences
import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.local.dao.TrackDao
import app.olus.ytmusic.autolauncher.data.local.entity.PlaylistEntity
import app.olus.ytmusic.autolauncher.data.local.entity.TrackEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val appId: String = "app.olus.ytmusic.autolauncher",
    val playlists: List<BackupPlaylist>,
    val tracks: List<BackupTrack>,
    val favorites: List<String> = emptyList(),
    val settings: BackupSettings,
    val jellyfin: BackupJellyfin?
)

data class BackupPlaylist(
    val id: Int,
    val url: String,
    val title: String,
    val imageUrl: String?,
    val position: Int,
    val trackCount: String?,
    val duration: String?,
    val source: String,
    val externalId: String?
)

data class BackupTrack(
    val playlistId: Int,
    val title: String,
    val author: String,
    val videoId: String,
    val position: Int
)

data class BackupSettings(
    val autoLyrics: Boolean = false,
    val audioCacheLimitMb: Long = 500
)

data class BackupJellyfin(
    val serverUrl: String,
    val username: String
)

class BackupManager(
    private val context: Context,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val prefs: SharedPreferences,
    private val jellyfinRepository: JellyfinRepository
) {
    suspend fun createBackup(): BackupData {
        val playlists = playlistDao.getAllPlaylistsOnce()
        val allTracks = mutableListOf<BackupTrack>()
        playlists.forEach { playlist ->
            val playlistTracks = trackDao.getTracksForPlaylist(playlist.id)
            playlistTracks.forEach { track ->
                allTracks.add(
                    BackupTrack(
                        playlistId = playlist.id,
                        title = track.title,
                        author = track.author,
                        videoId = track.videoId,
                        position = track.position
                    )
                )
            }
        }

        val settings = BackupSettings(
            autoLyrics = prefs.getBoolean("auto_lyrics", false),
            audioCacheLimitMb = prefs.getLong("audio_cache_limit_mb", 500)
        )

        val jellyfin = try {
            if (jellyfinRepository.isConfigured) {
                BackupJellyfin(
                    serverUrl = jellyfinRepository.serverUrl,
                    username = jellyfinRepository.username
                )
            } else null
        } catch (e: Exception) {
            null
        }

        return BackupData(
            playlists = playlists.map { p ->
                BackupPlaylist(
                    id = p.id,
                    url = p.url,
                    title = p.title,
                    imageUrl = p.imageUrl,
                    position = p.position,
                    trackCount = p.trackCount,
                    duration = p.duration,
                    source = p.source,
                    externalId = p.externalId
                )
            },
            tracks = allTracks,
            settings = settings,
            jellyfin = jellyfin
        )
    }

    fun toJson(backup: BackupData): String {
        val json = JSONObject()
        json.put("version", backup.version)
        json.put("timestamp", backup.timestamp)
        json.put("appId", backup.appId)

        val playlistsArray = JSONArray()
        backup.playlists.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("url", p.url)
            obj.put("title", p.title)
            obj.put("imageUrl", p.imageUrl)
            obj.put("position", p.position)
            obj.put("trackCount", p.trackCount)
            obj.put("duration", p.duration)
            obj.put("source", p.source)
            obj.put("externalId", p.externalId)
            playlistsArray.put(obj)
        }
        json.put("playlists", playlistsArray)

        val tracksArray = JSONArray()
        backup.tracks.forEach { t ->
            val obj = JSONObject()
            obj.put("playlistId", t.playlistId)
            obj.put("title", t.title)
            obj.put("author", t.author)
            obj.put("videoId", t.videoId)
            obj.put("position", t.position)
            tracksArray.put(obj)
        }
        json.put("tracks", tracksArray)

        val favoritesArray = JSONArray()
        backup.favorites.forEach { favoritesArray.put(it) }
        json.put("favorites", favoritesArray)

        val settingsObj = JSONObject()
        settingsObj.put("auto_lyrics", backup.settings.autoLyrics)
        settingsObj.put("audio_cache_limit_mb", backup.settings.audioCacheLimitMb)
        json.put("settings", settingsObj)

        backup.jellyfin?.let { jf ->
            val jfObj = JSONObject()
            jfObj.put("server_url", jf.serverUrl)
            jfObj.put("username", jf.username)
            json.put("jellyfin", jfObj)
        }

        return json.toString(2)
    }

    fun fromJson(jsonString: String): BackupData? {
        return try {
            val json = JSONObject(jsonString)

            val playlists = mutableListOf<BackupPlaylist>()
            val playlistsArray = json.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until playlistsArray.length()) {
                val obj = playlistsArray.getJSONObject(i)
                playlists.add(
                    BackupPlaylist(
                        id = obj.optInt("id", 0),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", ""),
                        imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl"),
                        position = obj.optInt("position", 0),
                        trackCount = obj.optString("trackCount"),
                        duration = obj.optString("duration"),
                        source = obj.optString("source", "YOUTUBE"),
                        externalId = obj.optString("externalId")
                    )
                )
            }

            val tracks = mutableListOf<BackupTrack>()
            val tracksArray = json.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until tracksArray.length()) {
                val obj = tracksArray.getJSONObject(i)
                tracks.add(
                    BackupTrack(
                        playlistId = obj.optInt("playlistId", 0),
                        title = obj.optString("title", ""),
                        author = obj.optString("author", ""),
                        videoId = obj.optString("videoId", ""),
                        position = obj.optInt("position", 0)
                    )
                )
            }

            val favorites = mutableListOf<String>()
            val favoritesArray = json.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until favoritesArray.length()) {
                favorites.add(favoritesArray.getString(i))
            }

            val settingsObj = json.optJSONObject("settings") ?: JSONObject()
            val settings = BackupSettings(
                autoLyrics = settingsObj.optBoolean("auto_lyrics", false),
                audioCacheLimitMb = settingsObj.optLong("audio_cache_limit_mb", 500)
            )

            val jfObj = json.optJSONObject("jellyfin")
            val jellyfin = if (jfObj != null) {
                BackupJellyfin(
                    serverUrl = jfObj.optString("server_url", ""),
                    username = jfObj.optString("username", "")
                )
            } else null

            BackupData(
                version = json.optInt("version", 1),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                appId = json.optString("appId", "app.olus.ytmusic.autolauncher"),
                playlists = playlists,
                tracks = tracks,
                favorites = favorites,
                settings = settings,
                jellyfin = jellyfin
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restoreBackup(backup: BackupData) {
        playlistDao.deleteAllPlaylists()

        backup.playlists.forEach { p ->
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    id = p.id,
                    url = p.url,
                    title = p.title,
                    imageUrl = p.imageUrl,
                    position = p.position,
                    trackCount = p.trackCount,
                    duration = p.duration,
                    source = p.source,
                    externalId = p.externalId
                )
            )
        }

        backup.tracks.forEach { t ->
            trackDao.insertTrack(
                TrackEntity(
                    playlistId = t.playlistId,
                    title = t.title,
                    author = t.author,
                    videoId = t.videoId,
                    position = t.position
                )
            )
        }

        prefs.edit()
            .putBoolean("auto_lyrics", backup.settings.autoLyrics)
            .putLong("audio_cache_limit_mb", backup.settings.audioCacheLimitMb)
            .apply()
    }
}