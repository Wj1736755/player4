package org.fossify.musicplayer.services.tts

import android.content.Context
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.models.AudioGenerationResult
import org.fossify.musicplayer.models.GeneratedAudio
import org.fossify.musicplayer.network.elevenlabs.*
import kotlinx.coroutines.*

/**
 * ElevenLabs implementation of AudioGenerationService
 * 
 * ElevenLabs provides unique feature: generating 3 variants of the same text
 * in a single API call without extra cost
 * 
 * @param context Android context
 * @param database Optional database instance (for testing). If null, uses singleton.
 */
class ElevenLabsAudioService(
    private val context: Context,
    database: SongsDatabase? = null
) : AudioGenerationService {
    
    private val api = ElevenLabsClient.api
    private val database = database ?: SongsDatabase.getInstance(context)
    
    // Default voice ID for Polish language
    // User can configure different voices in settings
    private val defaultVoiceId = "21m00Tcm4TlvDq8ikWAM"  // Rachel - multilingual
    
    override suspend fun generateAudio(
        text: String,
        voiceId: String?,
        variantCount: Int,
        previousRequestIds: List<String>?
    ): AudioGenerationResult = withContext(Dispatchers.IO) {
        try {
            // Get active API key from database
            val activeKey = database.ElevenLabsApiKeyDao().getActive()
                ?: return@withContext AudioGenerationResult.Error(
                    "No active API key configured. Please configure ElevenLabs API key in Settings."
                )
            
            val apiKey = activeKey.apiKey
            // Use voiceId from parameter, or from active API key, or fall back to default
            val voice = voiceId ?: activeKey.voiceId
            
            // Generate multiple different variants of the same text
            // Note: previous_request_ids is NOT for generating variants, it's for continuity between text fragments
            // To get different variants, we make independent calls without seed (random) or with different seeds
            val audios = mutableListOf<GeneratedAudio>()
            
            repeat(variantCount) { index ->
                val request = ElevenLabsTTSRequest(
                    text = text,
                    model_id = "eleven_multilingual_v2",
                    voice_settings = VoiceSettings(
                        stability = 0.5,
                        similarity_boost = 0.75,
                        style = 0.0,
                        use_speaker_boost = true
                    ),
                    // Don't use previous_request_ids - that's for text continuity, not variants
                    previous_request_ids = null,
                    // Don't use seed - we want random (different) variants each time
                    seed = null
                )
                
                android.util.Log.d("ElevenLabsAudioService", 
                    "Generating variant $index (independent call for different interpretation)")
                
                val response = api.textToSpeech(voice, request, apiKey)
                
                if (response.isSuccessful) {
                    // Get request_id from response headers for this variant
                    val currentRequestId = response.headers()["request-id"] 
                        ?: response.headers()["x-request-id"]
                    
                    android.util.Log.d("ElevenLabsAudioService", "Variant $index request ID: $currentRequestId")
                    
                    // Note: Character limits are not in TTS response headers
                    // They will be fetched from /user/subscription after all variants are generated
                    
                    val audioBytes = response.body()?.bytes()
                    if (audioBytes != null) {
                        audios.add(
                            GeneratedAudio(
                                variantIndex = index,
                                audioData = audioBytes,
                                mimeType = "audio/mpeg",
                                originalText = text,
                                voiceId = voice,
                                voiceName = if (voice == "ErXwobaYiN019PkySvjV") "Antoni" else "Rachel",
                                model = "eleven_multilingual_v2",
                                stability = 0.5f,
                                similarityBoost = 0.75f,
                                style = 0.0f,
                                speakerBoost = true,
                                requestId = currentRequestId
                            )
                        )
                    } else {
                        return@withContext AudioGenerationResult.Error(
                            "Empty response from ElevenLabs for variant $index"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    return@withContext AudioGenerationResult.Error(
                        "ElevenLabs API error (variant $index): ${response.code()} - $errorBody"
                    )
                }
                
                // Small delay between requests to avoid rate limiting
                if (index < variantCount - 1) {
                    delay(500)
                }
            }
            
            // After successful generation, fetch subscription info to update limits
            try {
                val subscriptionResponse = api.getUserSubscription(apiKey)
                if (subscriptionResponse.isSuccessful) {
                    val subscription = subscriptionResponse.body()
                    if (subscription != null) {
                        activeKey.lastUsedAtUtc = System.currentTimeMillis()
                        activeKey.characterLimit = subscription.character_limit
                        activeKey.characterCount = subscription.character_count
                        activeKey.characterLimitRemaining = (subscription.character_limit ?: 0) - (subscription.character_count ?: 0)
                        activeKey.nextCharacterCountResetUnix = subscription.next_character_count_reset_unix
                        
                        database.ElevenLabsApiKeyDao().update(activeKey)
                        
                        android.util.Log.d("ElevenLabsAudioService", 
                            "✅ Updated subscription info: ${activeKey.characterLimitRemaining} / ${activeKey.characterLimit} remaining, resets: ${activeKey.nextCharacterCountResetUnix}")
                    }
                } else {
                    android.util.Log.d("ElevenLabsAudioService", 
                        "⚠️ Could not fetch subscription info: ${subscriptionResponse.code()} (API key may not have user_read permission)")
                }
            } catch (e: Exception) {
                android.util.Log.d("ElevenLabsAudioService", 
                    "⚠️ Error fetching subscription info: ${e.message}")
            }
            
            AudioGenerationResult.Success(audios)
            
        } catch (e: Exception) {
            AudioGenerationResult.Error(
                "Failed to generate audio: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun isConfigured(): Boolean = withContext(Dispatchers.IO) {
        try {
            val activeKey = database.ElevenLabsApiKeyDao().getActive()
            android.util.Log.d("ElevenLabsAudioService", "isConfigured check: ${activeKey?.email ?: "NO KEY"}")
            activeKey != null
        } catch (e: Exception) {
            android.util.Log.e("ElevenLabsAudioService", "Error checking configuration", e)
            false
        }
    }
    
    override fun getProviderName(): String = "ElevenLabs"
}
