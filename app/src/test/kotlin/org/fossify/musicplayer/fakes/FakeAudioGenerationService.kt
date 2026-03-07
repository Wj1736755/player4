package org.fossify.musicplayer.fakes

import org.fossify.musicplayer.application.ports.AudioGenerationPort
import org.fossify.musicplayer.domain.model.AudioGenerationRequest
import org.fossify.musicplayer.domain.model.AudioGenerationResultDomain
import org.fossify.musicplayer.domain.model.GeneratedAudioFileDomain

class FakeAudioGenerationService : AudioGenerationPort {
    
    private var configured = false
    private var mockResponse: AudioGenerationResultDomain? = null
    private val generatedRequests = mutableListOf<AudioGenerationRequest>()
    
    fun setConfigured(value: Boolean) {
        configured = value
    }
    
    fun setMockResponse(response: AudioGenerationResultDomain) {
        mockResponse = response
    }
    
    fun setSuccessResponse(text: String, variantCount: Int = 1) {
        val audioFiles = (0 until variantCount).map { index ->
            GeneratedAudioFileDomain(
                variantIndex = index,
                data = "audio_data_variant_$index".toByteArray(),
                mimeType = "audio/mpeg",
                originalText = text,
                voiceId = "test_voice_id",
                voiceName = "Test Voice",
                model = "test_model",
                stability = 0.5f,
                similarityBoost = 0.75f,
                style = 0.0f,
                speakerBoost = true,
                requestId = "test_request_$index",
                duration = 3000
            )
        }
        mockResponse = AudioGenerationResultDomain.Success(audioFiles)
    }
    
    fun getGeneratedRequests(): List<AudioGenerationRequest> = generatedRequests.toList()
    
    fun clear() {
        configured = false
        mockResponse = null
        generatedRequests.clear()
    }
    
    override suspend fun generateAudio(request: AudioGenerationRequest): AudioGenerationResultDomain {
        generatedRequests.add(request)
        
        return mockResponse 
            ?: AudioGenerationResultDomain.Error("No mock response set")
    }
    
    override suspend fun isConfigured(): Boolean = configured
    
    override fun getProviderName(): String = "FakeProvider"
}
