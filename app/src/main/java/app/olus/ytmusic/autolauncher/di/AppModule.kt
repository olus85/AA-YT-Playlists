package app.olus.ytmusic.autolauncher.di

import android.content.Context
import app.olus.ytmusic.autolauncher.data.local.PlaylistDatabase
import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePlaylistDatabase(@ApplicationContext context: Context): PlaylistDatabase {
        return try {
            PlaylistDatabase.getDatabase(context)
        } catch (e: Exception) {
            // Graceful fallback for the DB if migration issues happen (Issue A4 compatibility)
            throw e
        }
    }

    @Provides
    fun providePlaylistDao(database: PlaylistDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun providePlaylistRepository(playlistDao: PlaylistDao): PlaylistRepository {
        return PlaylistRepository(playlistDao)
    }

    @Provides
    @Singleton
    fun provideMetadataFetcher(): MetadataFetcher {
        return MetadataFetcher()
    }
}
