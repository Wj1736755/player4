package org.fossify.musicplayer.network.elevenlabs

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * ElevenLabs Text-to-Speech API
 * Documentation: https://docs.elevenlabs.io/api-reference/text-to-speech
 */
interface ElevenLabsApi {
    
    /**
     * Generate audio from text using specified voice
     * 
     * @param voiceId The voice ID to use (get from /voices endpoint)
     * @param request The TTS request body
     * @param apiKey API key for authentication
     * @return Audio file in MP3 format with request_id in headers
     */
    @POST("v1/text-to-speech/{voice_id}")
    suspend fun textToSpeech(
        @Path("voice_id") voiceId: String,
        @Body request: ElevenLabsTTSRequest,
        @Header("xi-api-key") apiKey: String
    ): Response<ResponseBody>
    
    /**
     * Get list of available voices
     */
    @GET("v1/voices")
    suspend fun getVoices(
        @Header("xi-api-key") apiKey: String
    ): Response<ElevenLabsVoicesResponse>
    
    /**
     * Get user subscription info (including character limits)
     */
    @GET("v1/user/subscription")
    suspend fun getUserSubscription(
        @Header("xi-api-key") apiKey: String
    ): Response<ElevenLabsSubscriptionResponse>
    
    /**
     * Get history of generated audio files
     * 
     * @param apiKey API key for authentication
     * @param pageSize How many items to return (max 1000, default 100)
     * @param dateAfterUnix Unix timestamp to filter items after this date (inclusive)
     * @param dateBeforeUnix Unix timestamp to filter items before this date (exclusive)
     * @param voiceId Filter by voice ID
     * @return List of history items
     */
    @GET("v1/history")
    suspend fun getHistory(
        @Header("xi-api-key") apiKey: String,
        @Query("page_size") pageSize: Int? = null,
        @Query("date_after_unix") dateAfterUnix: Long? = null,
        @Query("date_before_unix") dateBeforeUnix: Long? = null,
        @Query("voice_id") voiceId: String? = null
    ): Response<ElevenLabsHistoryResponse>
    
    /**
     * Get audio file from history (does NOT consume credits)
     * 
     * @param historyItemId ID of the history item
     * @param apiKey API key for authentication
     * @return Audio file in MP3 format
     */
    @GET("v1/history/{history_item_id}/audio")
    suspend fun getHistoryAudio(
        @Path("history_item_id") historyItemId: String,
        @Header("xi-api-key") apiKey: String
    ): Response<ResponseBody>
}

/**
 * Request body for text-to-speech
 */
data class ElevenLabsTTSRequest(
    val text: String,
    val model_id: String = "eleven_multilingual_v2",  // Supports Polish
    val voice_settings: VoiceSettings? = null,
    val previous_request_ids: List<String>? = null,  // For regeneration/continuity
    val seed: Int? = null  // For deterministic generation
)

/**
 * Voice settings for TTS generation
 */
data class VoiceSettings(
    val stability: Double = 0.5,           // 0.0 to 1.0
    val similarity_boost: Double = 0.75,   // 0.0 to 1.0
    val style: Double = 0.0,               // 0.0 to 1.0
    val use_speaker_boost: Boolean = true
)

/**
 * Response from /voices endpoint
 */
data class ElevenLabsVoicesResponse(
    val voices: List<Voice>
)

data class Voice(
    val voice_id: String,
    val name: String,
    val category: String? = null,
    val labels: Map<String, String>? = null
)

/**
 * Response from /user/subscription endpoint
 */
data class ElevenLabsSubscriptionResponse(
    val tier: String? = null,
    val character_count: Int? = null,
    val character_limit: Int? = null,
    val can_extend_character_limit: Boolean? = null,
    val allowed_to_extend_character_limit: Boolean? = null,
    val next_character_count_reset_unix: Long? = null,
    val voice_limit: Int? = null,
    val professional_voice_limit: Int? = null,
    val can_use_instant_voice_cloning: Boolean? = null,
    val available_models: List<Map<String, Any>>? = null,
    val status: String? = null
)

/**
 * Response from /history endpoint
 */
data class ElevenLabsHistoryResponse(
    val history: List<ElevenLabsHistoryItem>,
    val has_more: Boolean,
    val last_history_item_id: String? = null,
    val scanned_until: Long? = null
)

/**
 * Single history item from ElevenLabs
 */
data class ElevenLabsHistoryItem(
    val history_item_id: String,
    val request_id: String?,
    val voice_id: String,
    val model_id: String,
    val voice_name: String,
    val voice_category: String?,
    val text: String,
    val date_unix: Long,
    val character_count_change_from: Int?,
    val character_count_change_to: Int?,
    val content_type: String?,
    val state: String?,
    val settings: ElevenLabsHistorySettings?,
    val source: String?
)

/**
 * Voice settings from history item
 */
data class ElevenLabsHistorySettings(
    val stability: Double?,
    val similarity_boost: Double?,
    val style: Double?,
    val use_speaker_boost: Boolean?
)
