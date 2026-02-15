package org.fossify.musicplayer.models

import java.io.File

/**
 * Represents a single generated audio file from text-to-speech service
 */
data class GeneratedAudio(
    val variantIndex: Int,  // 0, 1, or 2 for three variants
    val audioData: ByteArray,
    val mimeType: String = "audio/mpeg",
    val originalText: String,
    val generatedAt: Long = System.currentTimeMillis(),
    var localFile: File? = null,  // Set after saving to disk
    // Generation parameters for ID3 tags
    val voiceId: String? = null,
    val voiceName: String? = null,
    val model: String? = null,
    val stability: Float? = null,
    val similarityBoost: Float? = null,
    val style: Float? = null,
    val speakerBoost: Boolean? = null,
    val requestId: String? = null  // ElevenLabs request ID for regeneration
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeneratedAudio

        if (variantIndex != other.variantIndex) return false
        if (!audioData.contentEquals(other.audioData)) return false
        if (mimeType != other.mimeType) return false
        if (originalText != other.originalText) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variantIndex
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + originalText.hashCode()
        return result
    }
}

/**
 * Result of audio generation request
 */
sealed class AudioGenerationResult {
    data class Success(val audios: List<GeneratedAudio>) : AudioGenerationResult()
    data class Error(val message: String, val cause: Throwable? = null) : AudioGenerationResult()
}
