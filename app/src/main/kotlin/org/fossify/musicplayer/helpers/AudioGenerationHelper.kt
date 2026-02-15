package org.fossify.musicplayer.helpers

import android.content.Context
import org.fossify.musicplayer.models.AudioGenerationResult
import org.fossify.musicplayer.repositories.AudioGenerationRepository
import org.fossify.musicplayer.services.tts.AudioGenerationService
import org.fossify.musicplayer.services.tts.ElevenLabsAudioService
import java.io.File

/**
 * Helper class for easy audio generation
 * 
 * Usage example:
 * ```
 * val helper = AudioGenerationHelper(context)
 * 
 * lifecycleScope.launch {
 *     val result = helper.generateAndSaveAudio("Hello world")
 *     when (result) {
 *         is AudioGenerationResult.Success -> {
 *             val files = result.audios.mapNotNull { it.localFile }
 *             // Play files or show to user
 *         }
 *         is AudioGenerationResult.Error -> {
 *             toast(result.message)
 *         }
 *     }
 * }
 * ```
 */
class AudioGenerationHelper(private val context: Context) {
    
    private val service: AudioGenerationService = ElevenLabsAudioService(context)
    private val repository = AudioGenerationRepository(context)
    
    /**
     * Generate audio from text and save to temporary directory
     * 
     * @param text Text to convert to speech
     * @param voiceId Optional voice ID (provider-specific)
     * @param variantCount Number of variants to generate (default 3)
     * @param previousRequestIds Optional list of request IDs to regenerate from
     * @return AudioGenerationResult with saved files
     */
    suspend fun generateAndSaveAudio(
        text: String,
        voiceId: String? = null,
        variantCount: Int = 3,
        previousRequestIds: List<String>? = null
    ): AudioGenerationResult {
        val result = service.generateAudio(
            text, 
            voiceId = voiceId, 
            variantCount = variantCount,
            previousRequestIds = previousRequestIds
        )
        
        if (result is AudioGenerationResult.Success) {
            // Save all audio files to temp directory
            repository.saveAllAudios(result.audios)
        }
        
        return result
    }
    
    /**
     * Check if audio generation is properly configured
     */
    suspend fun isConfigured(): Boolean = service.isConfigured()
    
    /**
     * Get all temporary audio files
     */
    fun getTempFiles(): List<File> = repository.getTempAudios()
    
    /**
     * Clear temporary audio files
     */
    fun clearTempFiles() = repository.clearTempAudios()
    
    /**
     * Get temporary directory path
     */
    fun getTempDirectory(): File = repository.getTempDirectory()
    
    /**
     * Get the name of configured TTS provider
     */
    fun getProviderName(): String = service.getProviderName()
}
