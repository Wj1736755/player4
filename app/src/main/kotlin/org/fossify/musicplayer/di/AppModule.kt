package org.fossify.musicplayer.di

import android.content.Context
import org.fossify.musicplayer.application.ports.*
import org.fossify.musicplayer.application.usecases.*
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.domain.model.TrackDomain
import org.fossify.musicplayer.infrastructure.adapters.AndroidFileStorage
import org.fossify.musicplayer.infrastructure.adapters.ElevenLabsAdapter
import org.fossify.musicplayer.infrastructure.adapters.RoomAudioRepository
import org.fossify.musicplayer.infrastructure.adapters.RoomPlayEventLogger
import org.fossify.musicplayer.network.elevenlabs.ElevenLabsClient

object AppModule {
    
    fun provideAudioRepository(context: Context): AudioRepository {
        val database = SongsDatabase.getInstance(context)
        return RoomAudioRepository(database)
    }
    
    fun provideAudioGenerationService(context: Context): AudioGenerationPort {
        val database = SongsDatabase.getInstance(context)
        return ElevenLabsAdapter(
            api = ElevenLabsClient.api,
            apiKeyProvider = {
                database.ElevenLabsApiKeyDao().getActive()?.apiKey
            }
        )
    }
    
    fun provideFileStorage(context: Context): FileStorage {
        return AndroidFileStorage(context)
    }
    
    // Note: MediaPlayerAdapter requires integration with existing PlaybackService
    // For now, use fake implementation in tests
    // fun provideAudioPlayer(...): AudioPlayerPort { ... }
    
    fun providePlayEventLogger(context: Context): PlayEventLogger {
        val database = SongsDatabase.getInstance(context)
        return RoomPlayEventLogger(database)
    }
    
    fun provideGenerateAudioUseCase(
        audioGenService: AudioGenerationPort,
        fileStorage: FileStorage,
        trackRepository: AudioRepository
    ): GenerateAudioUseCase {
        return GenerateAudioUseCase(audioGenService, fileStorage, trackRepository)
    }
    
    fun providePlayTrackUseCase(
        audioPlayer: AudioPlayerPort,
        trackRepository: AudioRepository,
        eventLogger: PlayEventLogger
    ): PlayTrackUseCase {
        return PlayTrackUseCase(audioPlayer, trackRepository, eventLogger)
    }
    
    fun provideManagePlaylistUseCase(
        trackRepository: AudioRepository
    ): ManagePlaylistUseCase {
        return ManagePlaylistUseCase(trackRepository)
    }
    
    fun provideGetTracksUseCase(
        trackRepository: AudioRepository
    ): GetTracksUseCase {
        return GetTracksUseCase(trackRepository)
    }
    
    fun provideDeleteTracksUseCase(
        trackRepository: AudioRepository,
        fileStorage: FileStorage
    ): DeleteTracksUseCase {
        return DeleteTracksUseCase(trackRepository, fileStorage)
    }
}

class DependencyContainer(private val context: Context) {
    
    private val audioRepositoryInstance: AudioRepository by lazy {
        AppModule.provideAudioRepository(context)
    }
    
    private val audioGenerationServiceInstance: AudioGenerationPort by lazy {
        AppModule.provideAudioGenerationService(context)
    }
    
    private val fileStorageInstance: FileStorage by lazy {
        AppModule.provideFileStorage(context)
    }
    
    private val playEventLoggerInstance: PlayEventLogger by lazy {
        AppModule.providePlayEventLogger(context)
    }
    
    fun getGenerateAudioUseCase(): GenerateAudioUseCase {
        return AppModule.provideGenerateAudioUseCase(
            audioGenerationServiceInstance,
            fileStorageInstance,
            audioRepositoryInstance
        )
    }
    
    fun getPlayTrackUseCase(
        audioPlayer: AudioPlayerPort
    ): PlayTrackUseCase {
        return AppModule.providePlayTrackUseCase(
            audioPlayer,
            audioRepositoryInstance,
            playEventLoggerInstance
        )
    }
    
    fun getManagePlaylistUseCase(): ManagePlaylistUseCase {
        return AppModule.provideManagePlaylistUseCase(audioRepositoryInstance)
    }
    
    fun getGetTracksUseCase(): GetTracksUseCase {
        return AppModule.provideGetTracksUseCase(audioRepositoryInstance)
    }
    
    fun getDeleteTracksUseCase(): DeleteTracksUseCase {
        return AppModule.provideDeleteTracksUseCase(audioRepositoryInstance, fileStorageInstance)
    }
}
