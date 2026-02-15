package org.fossify.musicplayer.application.ports

import org.fossify.musicplayer.domain.model.AudioGenerationRequest
import org.fossify.musicplayer.domain.model.AudioGenerationResultDomain

interface AudioGenerationPort {
    suspend fun generateAudio(request: AudioGenerationRequest): AudioGenerationResultDomain
    
    suspend fun isConfigured(): Boolean
    
    fun getProviderName(): String
}
