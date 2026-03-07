package org.fossify.musicplayer.domain.model

data class AudioGenerationRequest(
    val text: String,
    val voiceId: String? = null,
    val variantCount: Int = 1,
    val stability: Float = 0.5f,
    val similarityBoost: Float = 0.75f,
    val style: Float = 0.0f,
    val speakerBoost: Boolean = true,
    val model: String = "eleven_multilingual_v2"
)
