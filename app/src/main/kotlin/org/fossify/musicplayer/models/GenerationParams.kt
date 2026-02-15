package org.fossify.musicplayer.models

/**
 * Parameters used to generate audio via TTS/AI service.
 * 
 * Stored in TXXX:GENERATION_PARAMS tag as JSON:
 * {
 *   "type": "elevenlabs",
 *   "typeParams": {
 *     "model": "eleven_multilingual_v2",
 *     "voice": "Antoni",
 *     ...
 *   }
 * }
 */
data class GenerationParams(
    val type: String,
    val typeParams: Map<String, Any>
) {
    companion object {
        /**
         * Create GenerationParams for ElevenLabs TTS.
         */
        fun forElevenLabs(
            model: String,
            voice: String,
            voiceId: String,
            stability: Float,
            similarityBoost: Float,
            style: Float,
            speakerBoost: Boolean
        ): GenerationParams {
            return GenerationParams(
                type = "elevenlabs",
                typeParams = mapOf(
                    "model" to model,
                    "voice" to voice,
                    "voice_id" to voiceId,
                    "stability" to stability,
                    "similarity_boost" to similarityBoost,
                    "style" to style,
                    "speaker_boost" to speakerBoost
                )
            )
        }
    }
}
