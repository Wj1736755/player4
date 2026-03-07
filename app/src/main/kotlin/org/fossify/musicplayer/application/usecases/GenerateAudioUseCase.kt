package org.fossify.musicplayer.application.usecases

import org.fossify.musicplayer.application.ports.AudioGenerationPort
import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.application.ports.FileStorage
import org.fossify.musicplayer.domain.model.*
import java.util.UUID

class GenerateAudioUseCase(
    private val audioGenerationService: AudioGenerationPort,
    private val fileStorage: FileStorage,
    private val trackRepository: AudioRepository
) {
    suspend fun execute(
        text: String,
        voiceId: String? = null,
        variantCount: Int = 3
    ): Result<List<TrackDomain>> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Text cannot be empty"))
        }
        
        if (variantCount < 1 || variantCount > 10) {
            return Result.failure(IllegalArgumentException("Variant count must be between 1 and 10"))
        }
        
        if (!audioGenerationService.isConfigured()) {
            return Result.failure(IllegalStateException("Audio generation service not configured"))
        }
        
        val request = AudioGenerationRequest(
            text = text,
            voiceId = voiceId,
            variantCount = variantCount
        )
        
        return when (val result = audioGenerationService.generateAudio(request)) {
            is AudioGenerationResultDomain.Success -> {
                try {
                    val tracks = result.audioFiles.map { audio ->
                        val guid = UUID.randomUUID()
                        val metadata = AudioMetadata(
                            guid = guid,
                            title = extractTitle(text),
                            artist = audioGenerationService.getProviderName(),
                            album = "Generated Audio",
                            voiceId = audio.voiceId,
                            voiceName = audio.voiceName,
                            model = audio.model,
                            generatedAt = System.currentTimeMillis(),
                            transcription = text
                        )
                        
                        val file = fileStorage.saveAudioFile(audio.data, metadata)
                        
                        TrackDomain(
                            guid = guid,
                            mediaStoreId = 0L,
                            path = file.absolutePath,
                            duration = audio.duration,
                            folderName = "Generated",
                            year = 0,
                            playlistId = 0
                        )
                    }
                    
                    trackRepository.saveTracks(tracks)
                    Result.success(tracks)
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to save generated audio: ${e.message}", e))
                }
            }
            is AudioGenerationResultDomain.Error -> {
                Result.failure(Exception(result.message, result.cause))
            }
        }
    }
    
    private fun extractTitle(text: String): String {
        val cleaned = text.trim()
        return if (cleaned.length > 50) {
            cleaned.take(47) + "..."
        } else {
            cleaned
        }
    }
}
