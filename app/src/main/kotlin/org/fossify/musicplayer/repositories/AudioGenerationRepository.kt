package org.fossify.musicplayer.repositories

import android.content.Context
import org.fossify.musicplayer.helpers.TXXXTagsWriter
import org.fossify.musicplayer.models.GeneratedAudio
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for managing generated audio files
 * Handles saving audio to temporary directory and cleanup
 */
class AudioGenerationRepository(private val context: Context) {
    
    private val tempDir: File by lazy {
        File(context.cacheDir, "elevenLabs_temp").apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val tagsWriter = TXXXTagsWriter(context)
    
    /**
     * Save generated audio to temporary file with ID3 tags
     * 
     * Format: ElevenLabs_2024-12-20T14_28_16_Antoni_pre_s50_sb75_se0_b_m2.mp3
     * 
     * @return File object pointing to saved audio
     */
    fun saveAudioToTemp(audio: GeneratedAudio): File {
        // Format timestamp: 2024-12-20T14_28_16 (ISO format with _ instead of :)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss", Locale.getDefault())
            .format(Date(audio.generatedAt))
        
        // Build filename according to ElevenLabs format
        val voiceName = audio.voiceName ?: "Unknown"
        val stability = ((audio.stability ?: 0.5f) * 100).toInt()
        val similarityBoost = ((audio.similarityBoost ?: 0.75f) * 100).toInt()
        val style = ((audio.style ?: 0.0f) * 100).toInt()
        val speakerBoost = if (audio.speakerBoost == true) "b" else ""
        val model = "m2"  // eleven_multilingual_v2
        
        val filename = "ElevenLabs_${timestamp}_${voiceName}_pre_s${stability}_sb${similarityBoost}_se${style}_${speakerBoost}_${model}.mp3"
        val file = File(tempDir, filename)
        
        // 1. Save audio data
        file.writeBytes(audio.audioData)
        audio.localFile = file
        
        // 2. Write TXXX tags to MP3 file (this will also set file.lastModified)
        if (audio.voiceId != null && audio.model != null) {
            android.util.Log.d("AudioGenerationRepository", "=== BEFORE writing tags to ${file.name} ===")
            android.util.Log.d("AudioGenerationRepository", "Text to write: ${audio.originalText}")
            
            val success = tagsWriter.writeElevenLabsTags(
                file = file,
                text = audio.originalText,
                voiceId = audio.voiceId,
                model = audio.model,
                stability = audio.stability ?: 0.5f,
                similarityBoost = audio.similarityBoost ?: 0.75f,
                style = audio.style ?: 0.0f,
                speakerBoost = audio.speakerBoost ?: true,
                voice = audio.voiceName ?: "Unknown"
            )
            
            if (!success) {
                android.util.Log.w("AudioGenerationRepository", 
                    "Failed to write TXXX tags to ${file.name}")
            } else {
                android.util.Log.d("AudioGenerationRepository", 
                    "Successfully wrote TXXX tags to ${file.name}")
                
                // Verify tags were written by reading them back
                android.util.Log.d("AudioGenerationRepository", "=== AFTER writing tags - verifying ===")
                val reader = org.fossify.musicplayer.helpers.TXXXTagsReader
                val tags = reader.readAllTags(file)
                if (tags != null) {
                    android.util.Log.d("AudioGenerationRepository", "✅ Transcription: ${tags.transcription}")
                    android.util.Log.d("AudioGenerationRepository", "✅ GUID: ${tags.guid}")
                    android.util.Log.d("AudioGenerationRepository", "✅ Duration: ${tags.duration}")
                    android.util.Log.d("AudioGenerationRepository", "✅ Checksum: ${tags.checksumAudio}")
                    android.util.Log.d("AudioGenerationRepository", "✅ GenerationParams: ${tags.generationParams}")
                } else {
                    android.util.Log.e("AudioGenerationRepository", "❌ Failed to read tags back!")
                }
            }
        }
        
        return file
    }
    
    /**
     * Save all generated audios to temporary files
     */
    fun saveAllAudios(audios: List<GeneratedAudio>): List<File> {
        return audios.map { saveAudioToTemp(it) }
    }
    
    /**
     * Get all temporary audio files
     */
    fun getTempAudios(): List<File> {
        return tempDir.listFiles()?.toList() ?: emptyList()
    }
    
    /**
     * Clear all temporary audio files
     */
    fun clearTempAudios() {
        tempDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * Clear old temporary files (older than specified hours)
     */
    fun clearOldTempAudios(olderThanHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 3600 * 1000L)
        tempDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }
    
    /**
     * Get temporary directory path
     */
    fun getTempDirectory(): File = tempDir
}
