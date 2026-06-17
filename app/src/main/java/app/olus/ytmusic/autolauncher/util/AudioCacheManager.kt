package app.olus.ytmusic.autolauncher.util

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton manager for ExoPlayer's audio cache.
 * Uses LRU eviction with a configurable size limit (default: 500 MB).
 * The cache is stored in context.cacheDir so the OS can clean it
 * automatically when device storage is critically low.
 */
object AudioCacheManager {

    private const val CACHE_DIR_NAME = "jellyfin_audio_cache"
    private const val DEFAULT_CACHE_SIZE_MB = 500L
    private const val PREFS_NAME = "app_prefs"
    private const val PREF_KEY = "audio_cache_limit_mb"

    @Volatile
    private var cache: SimpleCache? = null
    @Volatile
    private var currentMaxBytes: Long = 0

    fun getCache(context: Context): SimpleCache? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val maxMb = prefs.getLong(PREF_KEY, DEFAULT_CACHE_SIZE_MB)
        val maxBytes = maxMb * 1024 * 1024

        var localCache = cache
        if (localCache == null || currentMaxBytes != maxBytes) {
            synchronized(this) {
                if (currentMaxBytes != maxBytes) {
                    cache?.release()
                    cache = null
                    currentMaxBytes = 0
                }
                localCache = cache
                if (localCache == null) {
                    localCache = createCache(context, maxBytes)
                    cache = localCache
                    currentMaxBytes = if (localCache != null) maxBytes else 0
                }
            }
        }
        return localCache
    }

    private fun createCache(context: Context, maxBytes: Long): SimpleCache? {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val databaseProvider = StandaloneDatabaseProvider(context)
        return try {
            SimpleCache(cacheDir, evictor, databaseProvider)
        } catch (e: Exception) {
            AALogger.logError("AudioCacheManager", "Failed to create SimpleCache, deleting directory and retrying...", e)
            try {
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                SimpleCache(cacheDir, evictor, databaseProvider)
            } catch (ex: Exception) {
                AALogger.logError("AudioCacheManager", "Failed to recreate SimpleCache after delete", ex)
                null
            }
        }
    }

    fun release() {
        synchronized(this) {
            cache?.release()
            cache = null
            currentMaxBytes = 0
        }
    }
}
