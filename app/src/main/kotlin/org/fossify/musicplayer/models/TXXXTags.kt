package org.fossify.musicplayer.models

/**
 * Custom TXXX tags stored in MP3 files.
 * 
 * All tags are optional (nullable) as not all MP3 files have these tags.
 * 
 * Tags:
 * - TXXX:Content - transcription (normalized text)
 * - TXXX:CREATED_ON_DATE - creation date (YYYY-MM-DD)
 * - TXXX:CREATED_ON_TS - creation timestamp (ISO 8601 with timezone)
 * - TXXX:DURATION_SEC - duration in seconds (1 decimal place)
 * - TXXX:GUID - unique identifier (UUID v4)
 * - TXXX:CHECKSUM_MD5_AUDIO - MD5 checksum of audio data only
 * - TXXX:GENERATION_PARAMS - generation parameters (JSON)
 */
data class TXXXTags(
    val transcription: String? = null,
    val createdOnDate: String? = null,
    val createdOnTimestamp: Long? = null,
    val duration: Float? = null,
    val guid: String? = null,
    val checksumAudio: String? = null,
    val generationParams: GenerationParams? = null
) {
    /**
     * Check if tags are empty (all fields are null).
     */
    fun isEmpty(): Boolean {
        return transcription == null &&
                createdOnDate == null &&
                createdOnTimestamp == null &&
                duration == null &&
                guid == null &&
                checksumAudio == null &&
                generationParams == null
    }

    /**
     * Check if tags contain at least one field.
     */
    fun isNotEmpty(): Boolean = !isEmpty()
}
