package org.fossify.musicplayer.infrastructure.adapters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.fossify.musicplayer.application.ports.AudioGenerationPort
import org.fossify.musicplayer.domain.model.AudioGenerationRequest
import org.fossify.musicplayer.domain.model.AudioGenerationResultDomain
import org.fossify.musicplayer.domain.model.GeneratedAudioFileDomain
import org.fossify.musicplayer.network.elevenlabs.ElevenLabsApi
import org.fossify.musicplayer.network.elevenlabs.ElevenLabsTTSRequest
import org.fossify.musicplayer.network.elevenlabs.VoiceSettings

class ElevenLabsAdapter(
    private val api: ElevenLabsApi,
    private val apiKeyProvider: suspend () -> String?
) : AudioGenerationPort {
    
    private val defaultVoiceId = "21m00Tcm4TlvDq8ikWAM"
    
    override suspend fun generateAudio(request: AudioGenerationRequest): AudioGenerationResultDomain = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider() 
                ?: return@withContext AudioGenerationResultDomain.Error(
                    "No active API key configured. Please configure ElevenLabs API key in Settings."
                )
            
            val voiceId = request.voiceId ?: defaultVoiceId
            val audios = mutableListOf<GeneratedAudioFileDomain>()
            
            repeat(request.variantCount) { index ->
                val ttsRequest = ElevenLabsTTSRequest(
                    text = request.text,
                    model_id = request.model,
                    voice_settings = VoiceSettings(
                        stability = request.stability.toDouble(),
                        similarity_boost = request.similarityBoost.toDouble(),
                        style = request.style.toDouble(),
                        use_speaker_boost = request.speakerBoost
                    ),
                    previous_request_ids = null,
                    seed = null
                )
                
                val response = api.textToSpeech(voiceId, ttsRequest, apiKey)
                
                if (response.isSuccessful) {
                    val requestId = response.headers()["request-id"] 
                        ?: response.headers()["x-request-id"]
                    
                    val audioBytes = response.body()?.bytes()
                    if (audioBytes != null) {
                        audios.add(
                            GeneratedAudioFileDomain(
                                variantIndex = index,
                                data = audioBytes,
                                mimeType = "audio/mpeg",
                                originalText = request.text,
                                voiceId = voiceId,
                                voiceName = getVoiceName(voiceId),
                                model = request.model,
                                stability = request.stability,
                                similarityBoost = request.similarityBoost,
                                style = request.style,
                                speakerBoost = request.speakerBoost,
                                requestId = requestId
                            )
                        )
                    } else {
                        return@withContext AudioGenerationResultDomain.Error(
                            "Empty response from ElevenLabs for variant $index"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    return@withContext AudioGenerationResultDomain.Error(
                        "ElevenLabs API error (variant $index): ${response.code()} - $errorBody"
                    )
                }
                
                if (index < request.variantCount - 1) {
                    delay(500)
                }
            }
            
            AudioGenerationResultDomain.Success(audios)
        } catch (e: Exception) {
            AudioGenerationResultDomain.Error(
                "Failed to generate audio: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun isConfigured(): Boolean {
        return apiKeyProvider() != null
    }
    
    override fun getProviderName(): String = "ElevenLabs"
    
    private fun getVoiceName(voiceId: String): String {
        return when (voiceId) {
            "ErXwobaYiN019PkySvjV" -> "Antoni"
            "21m00Tcm4TlvDq8ikWAM" -> "Rachel"
            else -> "Unknown"
        }
    }
}
