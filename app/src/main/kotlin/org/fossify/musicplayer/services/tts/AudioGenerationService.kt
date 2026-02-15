package org.fossify.musicplayer.services.tts

import org.fossify.musicplayer.models.AudioGenerationResult

/**
 * Interface for text-to-speech audio generation services.
 * This abstraction allows swapping implementations (ElevenLabs, Google TTS, etc.)
 */
interface AudioGenerationService {
    
    /**
     * Generate multiple audio variants from text
     * 
     * @param text The text to convert to speech
     * @param voiceId Optional voice identifier (provider-specific)
     * @param variantCount Number of variants to generate (default 3 for ElevenLabs)
     * @param previousRequestIds Optional list of request IDs to regenerate from (ElevenLabs feature)
     * @return AudioGenerationResult with list of generated audio files or error
     */
    suspend fun generateAudio(
        text: String,
        voiceId: String? = null,
        variantCount: Int = 3,
        previousRequestIds: List<String>? = null
    ): AudioGenerationResult
    
    /**
     * Check if the service is properly configured (e.g., has API key)
     * Suspending function to allow database queries
     */
    suspend fun isConfigured(): Boolean
    
    /**
     * Get the name of this TTS provider
     */
    fun getProviderName(): String
}
