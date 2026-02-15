package org.fossify.musicplayer.domain.model

sealed class AudioGenerationResultDomain {
    data class Success(val audioFiles: List<GeneratedAudioFileDomain>) : AudioGenerationResultDomain()
    data class Error(val message: String, val cause: Throwable? = null) : AudioGenerationResultDomain()
}

data class GeneratedAudioFileDomain(
    val variantIndex: Int,
    val data: ByteArray,
    val mimeType: String,
    val originalText: String,
    val voiceId: String?,
    val voiceName: String?,
    val model: String?,
    val stability: Float?,
    val similarityBoost: Float?,
    val style: Float?,
    val speakerBoost: Boolean?,
    val requestId: String?,
    val duration: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeneratedAudioFileDomain

        if (variantIndex != other.variantIndex) return false
        if (!data.contentEquals(other.data)) return false
        if (mimeType != other.mimeType) return false
        if (originalText != other.originalText) return false
        if (voiceId != other.voiceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variantIndex
        result = 31 * result + data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + originalText.hashCode()
        result = 31 * result + (voiceId?.hashCode() ?: 0)
        return result
    }
}
