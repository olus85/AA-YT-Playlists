package app.olus.ytmusic.autolauncher.di

import android.content.Context
import app.olus.ytmusic.autolauncher.data.local.PlaylistDatabase
import app.olus.ytmusic.autolauncher.data.local.dao.PlaylistDao
import app.olus.ytmusic.autolauncher.data.local.dao.TrackDao
import app.olus.ytmusic.autolauncher.data.repository.JellyfinRepository
import app.olus.ytmusic.autolauncher.data.repository.MetadataFetcher
import app.olus.ytmusic.autolauncher.data.repository.PlaylistRepository
import app.olus.ytmusic.autolauncher.service.MediaSyncManager
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
        return PlaylistDatabase.getDatabase(context)
    }

    @Provides
    fun providePlaylistDao(database: PlaylistDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideTrackDao(database: PlaylistDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    fun provideLyricsDao(database: PlaylistDatabase): app.olus.ytmusic.autolauncher.data.local.dao.LyricsDao {
        return database.lyricsDao()
    }

    @Provides
    @Singleton
    fun providePlaylistRepository(playlistDao: PlaylistDao, trackDao: TrackDao): PlaylistRepository {
        return PlaylistRepository(playlistDao, trackDao)
    }

    @Provides
    @Singleton
    fun provideMetadataFetcher(): MetadataFetcher {
        return MetadataFetcher()
    }

    @Provides
    @Singleton
    fun provideMediaSyncManager(@ApplicationContext context: Context): MediaSyncManager {
        return MediaSyncManager(context)
    }

    @Provides
    @Singleton
    fun provideJellyfinRepository(@ApplicationContext context: Context): JellyfinRepository {
        return JellyfinRepository(context)
    }
}
