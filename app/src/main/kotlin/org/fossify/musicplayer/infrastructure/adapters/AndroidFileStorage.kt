package org.fossify.musicplayer.infrastructure.adapters

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.musicplayer.application.ports.FileStorage
import org.fossify.musicplayer.domain.model.AudioMetadata
import org.fossify.musicplayer.helpers.TXXXTagsWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AndroidFileStorage(
    private val context: Context
) : FileStorage {
    
    private val tempDir: File by lazy {
        File(context.cacheDir, "elevenLabs_temp").apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val tagsWriter = TXXXTagsWriter(context)
    
    override suspend fun saveAudioFile(data: ByteArray, metadata: AudioMetadata): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss", Locale.getDefault())
            .format(Date(metadata.generatedAt))
        
        val voiceName = metadata.voiceName ?: "Unknown"
        val filename = "ElevenLabs_${timestamp}_${voiceName}_${metadata.guid}.mp3"
        val file = File(tempDir, filename)
        
        file.writeBytes(data)
        
        if (metadata.voiceId != null && metadata.model != null) {
            tagsWriter.writeElevenLabsTags(
                file = file,
                text = metadata.transcription,
                voiceId = metadata.voiceId,
                model = metadata.model,
                stability = 0.5f,
                similarityBoost = 0.75f,
                style = 0.0f,
                speakerBoost = true,
                voice = metadata.voiceName ?: "Unknown"
            )
        }
        
        file
    }
    
    override suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getTempFiles(): List<File> = withContext(Dispatchers.IO) {
        tempDir.listFiles()?.toList() ?: emptyList()
    }
    
    override suspend fun clearTempFiles(): Unit = withContext(Dispatchers.IO) {
        tempDir.listFiles()?.forEach { it.delete() }
        Unit
    }
    
    override suspend fun clearOldTempFiles(olderThanHours: Int): Unit = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (olderThanHours * 3600 * 1000L)
        tempDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
        Unit
    }
    
    override fun getTempDirectory(): File = tempDir
}
